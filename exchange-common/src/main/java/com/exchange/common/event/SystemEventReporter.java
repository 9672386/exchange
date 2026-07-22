package com.exchange.common.event;

import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

/**
 * 系统事件上报器:计数 + 节流日志。
 *
 * <h3>解决的问题</h3>
 * <p>高频异常事件若每次都打日志会淹没日志系统并拖慢线程;若完全不打又无法排查。
 * 本类对每个事件维护累计计数,按"次数阈值"或"时间阈值"(先到先触发)输出汇总日志,
 * 日志中同时包含<b>累计值与本窗口增量</b>,因此节流不丢失信息量。
 *
 * <h3>在 Raft 状态机内使用</h3>
 * <p>状态机线程禁止读 wall-clock(破坏确定性)且禁止阻塞。因此:
 * <ul>
 *   <li>必须调用带 {@code refTimeMs} 的重载,传入 <b>cluster timestamp</b></li>
 *   <li>热路径使用不带 {@code detail} 的重载,零额外分配</li>
 *   <li>计数本身是节点本地的观测状态,<b>不属于状态机状态</b>,
 *       不入快照、不参与判定,因此各副本计数不同不构成分歧</li>
 * </ul>
 *
 * <p>注意:日志输出时机由 cluster timestamp 驱动,因此各副本会在相同的日志位置输出,
 * 便于跨节点比对排查。
 *
 * <h3>事件停止后的收尾</h3>
 * <p>"按时间触发"依赖新事件到达才能检查。若某事件突然停止,最后一个窗口的增量不会被输出。
 * 由定时器周期性调用 {@link #flushPending(long)} 补齐。
 *
 * <h3>code 唯一性</h3>
 * <p>{@link SystemEvent#code()} 作为计数键与配置键,要求<b>全局唯一</b>。
 * 各模块自定义事件建议加模块前缀,如 {@code ASSET_LEDGER_REJECTED}。
 */
@Slf4j
public class SystemEventReporter {

    /** 输出事件日志时使用的独立 logger,便于单独配置级别或路由到专用文件。 */
    private static final Logger EVENT_LOG = LoggerFactory.getLogger("SYSTEM_EVENT");

    private final SystemEventProperties props;

    /** code → 计数器 */
    private final ConcurrentHashMap<String, Counter> counters = new ConcurrentHashMap<>();

    public SystemEventReporter(SystemEventProperties props) {
        this.props = props;
        log.info("[SystemEventReporter] Initialized — enabled={} defaultPolicy(everyN={}, intervalMs={})",
                props.isEnabled(),
                props.getDefaultPolicy().getLogEveryN(),
                props.getDefaultPolicy().getLogIntervalMs());
    }

    /**
     * 创建"只计数不输出"的实例。
     *
     * <p>用于单元测试,或 Spring 容器不可用的构造场景(如手工 new 出来的状态机组件),
     * 避免调用方到处判空。
     */
    public static SystemEventReporter noop() {
        SystemEventProperties p = new SystemEventProperties();
        p.setEnabled(false);
        return new SystemEventReporter(p);
    }

    // =========================================================================
    // 上报入口
    // =========================================================================

    /**
     * 上报事件(使用本地时钟)。
     *
     * <p><b>禁止在 Raft 状态机线程中调用</b>,请使用 {@link #record(SystemEvent, long)}。
     */
    public void record(SystemEvent event) {
        record(event, System.currentTimeMillis(), null);
    }

    /**
     * 上报事件(指定参考时间,状态机内使用)。
     *
     * @param refTimeMs 参考时间。状态机内传 cluster timestamp,其他场景传 wall-clock
     */
    public void record(SystemEvent event, long refTimeMs) {
        record(event, refTimeMs, null);
    }

    /** 上报事件并附带明细(本地时钟)。 */
    public void record(SystemEvent event, Supplier<String> detail) {
        record(event, System.currentTimeMillis(), detail);
    }

    /**
     * 上报事件并附带明细。
     *
     * @param detail 明细供应者。<b>仅在实际输出日志时才会被调用</b>,
     *               被节流时零开销。热路径若无需明细请传 {@code null} 或使用无 detail 重载
     *               (lambda 捕获变量会产生对象分配)。
     */
    public void record(SystemEvent event, long refTimeMs, Supplier<String> detail) {
        Counter c = counters.computeIfAbsent(event.code(), k -> new Counter(event));
        long total = c.total.incrementAndGet();

        if (!props.isEnabled()) {
            return;                       // 仅计数,不输出
        }

        SystemEventProperties.Policy policy = props.resolve(event.code());
        long lastCount = c.lastLoggedCount.get();
        long lastTime  = c.lastLoggedTimeMs.get();

        boolean byCount = policy.countTriggerEnabled()
                && (total - lastCount) >= policy.getLogEveryN();
        boolean byTime  = policy.timeTriggerEnabled()
                && lastTime > 0
                && (refTimeMs - lastTime) >= policy.getLogIntervalMs();

        // 首次出现:初始化时间基准并立即输出一次(便于第一时间发现异常事件)
        boolean first = (lastTime == 0);

        if (byCount || byTime || first) {
            // CAS 抢占输出权,保证并发下同一窗口只输出一条
            if (c.lastLoggedCount.compareAndSet(lastCount, total)) {
                c.lastLoggedTimeMs.set(refTimeMs);
                emit(event, total, total - lastCount, refTimeMs - lastTime, lastTime == 0, detail);
            }
        }
    }

    // =========================================================================
    // 收尾与查询
    // =========================================================================

    /**
     * 输出所有存在未上报增量的事件(由定时器周期调用)。
     *
     * <p>解决"事件停止后最后一个窗口不输出"的问题。
     *
     * @param refTimeMs 参考时间。状态机内传 cluster timestamp
     * @return 本次实际输出的事件数
     */
    public int flushPending(long refTimeMs) {
        if (!props.isEnabled()) return 0;
        int emitted = 0;
        for (Counter c : counters.values()) {
            long total     = c.total.get();
            long lastCount = c.lastLoggedCount.get();
            if (total > lastCount && c.lastLoggedCount.compareAndSet(lastCount, total)) {
                long lastTime = c.lastLoggedTimeMs.getAndSet(refTimeMs);
                emit(c.event, total, total - lastCount, refTimeMs - lastTime, false, null);
                emitted++;
            }
        }
        return emitted;
    }

    /** 查询单个事件的累计次数。 */
    public long count(SystemEvent event) {
        Counter c = counters.get(event.code());
        return c != null ? c.total.get() : 0L;
    }

    /** 导出全部计数快照(监控端点使用)。 */
    public List<EventStat> stats() {
        List<EventStat> list = new ArrayList<>(counters.size());
        for (Map.Entry<String, Counter> e : counters.entrySet()) {
            Counter c = e.getValue();
            list.add(new EventStat(
                    c.event.code(),
                    c.event.description(),
                    c.event.severity().name(),
                    c.event.expectZero(),
                    c.total.get()));
        }
        list.sort((a, b) -> Long.compare(b.total(), a.total()));
        return list;
    }

    /**
     * 是否存在"期望恒为 0 却已计数"的事件。
     *
     * <p>可直接作为健康检查项:返回 true 说明系统出现了不该出现的情况。
     */
    public boolean hasUnexpectedEvents() {
        return counters.values().stream()
                .anyMatch(c -> c.event.expectZero() && c.total.get() > 0);
    }

    /** 重置全部计数(仅供测试或运维手动清零)。 */
    public void reset() {
        counters.clear();
    }

    // =========================================================================
    // 内部
    // =========================================================================

    private void emit(SystemEvent event, long total, long delta,
                      long windowMs, boolean first, Supplier<String> detail) {
        String detailText = (detail != null) ? detail.get() : null;
        String msg = String.format(
                "[%s] %s — total=%d delta=%d window=%s%s%s",
                event.code(),
                event.description(),
                total,
                delta,
                first ? "first" : (windowMs + "ms"),
                event.expectZero() ? " [EXPECT_ZERO]" : "",
                detailText != null ? " | " + detailText : "");

        switch (event.severity()) {
            case ERROR -> EVENT_LOG.error(msg);
            case WARN  -> EVENT_LOG.warn(msg);
            case INFO  -> EVENT_LOG.info(msg);
        }
    }

    private static final class Counter {
        final SystemEvent event;
        final AtomicLong  total           = new AtomicLong();
        final AtomicLong  lastLoggedCount = new AtomicLong();
        final AtomicLong  lastLoggedTimeMs = new AtomicLong();

        Counter(SystemEvent event) {
            this.event = event;
        }
    }

    /** 计数快照(供监控端点序列化)。 */
    public record EventStat(
            String  code,
            String  description,
            String  severity,
            boolean expectZero,
            long    total
    ) {}
}
