package com.exchange.match.core.transport;

import com.exchange.match.constant.MatchSettlementStream;
import com.exchange.match.model.MatchResponse;
import com.exchange.transport.aeron.config.AeronConfigFactory;
import com.exchange.transport.aeron.config.AeronConfigFactory.PublisherChannelConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.aeron.Aeron;
import io.aeron.Publication;
import io.aeron.archive.client.AeronArchive;
import io.aeron.archive.codecs.SourceLocation;
import lombok.extern.slf4j.Slf4j;
import org.agrona.concurrent.UnsafeBuffer;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.LongAdder;

/**
 * Aeron MDC 出站发布者 —— 撮合结果广播。
 *
 * <h3>职责</h3>
 * <p>撮合引擎处理完订单后，通过 Aeron MDC（Multi-Destination Cast）将 {@link MatchResponse}
 * 广播给所有已订阅的下游消费方（Risk、Quote 等）。订阅方动态加入，无需重启 Publisher。
 *
 * <h3>持久化</h3>
 * <p>Aeron Archive 同步录制 MDC 出站流，{@code TradeSettlementForwarder} 从 Archive 消费
 * 成交结果并转发至 Asset Cluster，取代原 Kafka 路径。
 *
 * <h3>激活条件</h3>
 * <pre>
 *   aeron.enabled=true              # 默认 true
 *   aeron.result-publisher=true     # 默认 true
 * </pre>
 *
 * <h3>环境变量</h3>
 * <pre>
 *   MATCH_CONTROL_ADDR   MDC 控制地址，格式 ip:port，Risk/Quote 订阅方使用相同地址
 *   STREAM_ID            Aeron stream ID，默认 1001
 *   AERON_INTERFACE      本机 ENI IP（AWS 环境必填）
 * </pre>
 *
 * <h3>背压处理策略</h3>
 * <p>Aeron Publication 的 {@code offer()} 返回负值时表示背压或异常：
 * <ul>
 *   <li>{@code BACK_PRESSURED}：下游消费慢，当前实现记录 metric 后丢弃（撮合引擎不阻塞）。</li>
 *   <li>{@code NOT_CONNECTED}：无订阅方，视为正常（无消费方时安静丢弃）。</li>
 *   <li>{@code CLOSED}：Publication 已关闭，记录错误。</li>
 * </ul>
 * <p>如需可靠投递，将丢弃的消息路由到重试队列；Archive 录制保证持久化。
 */
@Slf4j
@Component
@ConditionalOnBean(Aeron.class)
@ConditionalOnProperty(name = "aeron.result-publisher", havingValue = "true", matchIfMissing = true)
public class AeronMatchResultPublisher implements InitializingBean, DisposableBean {

    @Autowired
    private Aeron aeron;

    /**
     * Match Cluster 内嵌 Archive（port 8010），用于录制成交结果供 TradeSettlementForwarder 消费。
     * {@code required = false}：Archive 未启用时仅通过 MDC 广播，不录制（无持久化保证）。
     */
    @Autowired(required = false)
    private AeronArchive aeronArchive;

    private final ObjectMapper objectMapper = new ObjectMapper();

    /** 实时广播 publication（UDP MDC）→ Risk / Quote 等动态订阅方,尽力而为。 */
    private Publication livePublication;

    /**
     * 结算录制 publication（进程内 IPC,stream={@link MatchSettlementStream#SETTLEMENT_STREAM}）
     * → Archive 本地录制 → TradeSettlementForwarder 消费。<b>可靠写入,绝不丢。</b>
     * Archive 未启用时为 null（降级:仅 UDP 广播,无持久化）。
     */
    private Publication settlementPublication;

    /** channel/streamId 配置，保存供快照记录查询用。 */
    private PublisherChannelConfig cfg;

    /** 最近一次成功 offer 的结算 publication position，快照时写入 archivePosition 字段。 */
    private volatile long lastPublishedPosition = 0L;

    /**
     * BACK_PRESSURED 时最大自旋重试次数（约 2 ms 窗口）。
     * 超出后视为消费方严重滞后，记录告警；Archive 录制依赖 publication 流，
     * 超出上限仍丢弃会导致 TradeSettlementForwarder 漏成交，须同时触发监控告警。
     */
    private static final int MAX_BACK_PRESSURE_RETRIES = 2_000;

    // 轻量 metric（无锁计数，避免影响撮合线程）
    private final LongAdder sentCount        = new LongAdder();
    private final LongAdder backPressureCount = new LongAdder();
    private final LongAdder errorCount        = new LongAdder();

    /* ══════════════════════════════════════════════════════════════
     *  Spring 生命周期
     * ══════════════════════════════════════════════════════════════ */

    @Override
    public void afterPropertiesSet() {
        cfg = AeronConfigFactory.buildPublisherFromEnv();
        // 1) 实时广播流（UDP MDC）——给 Risk/Quest 等动态订阅方
        livePublication = aeron.addPublication(cfg.getChannel(), cfg.getStreamId());
        log.info("[AeronMatchResultPublisher] Live MDC publication ready — channel={}, streamId={}",
                cfg.getChannel(), cfg.getStreamId());

        // 2) 结算录制流（IPC）——Archive 本地可靠录制,供 TradeSettlementForwarder 消费
        if (aeronArchive != null) {
            settlementPublication = aeron.addExclusivePublication(
                    MatchSettlementStream.SETTLEMENT_CHANNEL, MatchSettlementStream.SETTLEMENT_STREAM);
            aeronArchive.startRecording(
                    MatchSettlementStream.SETTLEMENT_CHANNEL, MatchSettlementStream.SETTLEMENT_STREAM,
                    SourceLocation.LOCAL);
            log.info("[AeronMatchResultPublisher] Settlement recording started — channel={} stream={} (durable)",
                    MatchSettlementStream.SETTLEMENT_CHANNEL, MatchSettlementStream.SETTLEMENT_STREAM);
        } else {
            log.warn("[AeronMatchResultPublisher] AeronArchive not available — settlement NOT durably recorded; "
                    + "结算将无持久化保证,仅实时 UDP 广播（降级模式,勿用于生产）");
        }
    }

    @Override
    public void destroy() {
        if (livePublication != null)       livePublication.close();
        if (settlementPublication != null) settlementPublication.close();
        log.info("[AeronMatchResultPublisher] Shutdown — sent={}, backPressure={}, error={}",
                sentCount.sum(), backPressureCount.sum(), errorCount.sum());
    }

    /* ══════════════════════════════════════════════════════════════
     *  Public API
     * ══════════════════════════════════════════════════════════════ */

    /**
     * 将 {@link MatchResponse} 通过 Aeron MDC 广播给所有下游订阅方。
     *
     * <p><b>调用线程：Disruptor 事件处理线程</b>（单线程，无并发竞争）。
     * 此方法必须是非阻塞的，不得有任何等待或锁定。
     *
     * @param response 撮合结果，非 null
     */
    /**
     * 将撮合结果广播到 Aeron MDC（同步写入 publication，Archive 同步录制）。
     *
     * <h3>背压处理策略</h3>
     * <ol>
     *   <li>首次 {@code BACK_PRESSURED}：自旋重试最多 {@link #MAX_BACK_PRESSURE_RETRIES} 次（~2 ms）。</li>
     *   <li>超出重试上限：记录 ERROR 告警，丢弃本条消息。
     *       <b>注意</b>：Archive 录制依赖 publication 流，丢弃意味着 TradeSettlementForwarder
     *       将漏掉该成交，必须同时触发监控告警并人工介入。</li>
     *   <li>其他错误码（{@code NOT_CONNECTED} / {@code CLOSED}）：直接记录，不阻塞撮合线程。</li>
     * </ol>
     *
     * <p><b>调用线程：Disruptor 事件处理线程</b>（单线程，无并发竞争）。
     */
    public void send(MatchResponse response) {
        if (response == null) return;

        try {
            byte[] jsonBytes = objectMapper.writeValueAsBytes(response);
            // UnsafeBuffer 直接包装字节数组，零拷贝传入 Aeron（不额外分配堆内存）
            UnsafeBuffer buffer = new UnsafeBuffer(jsonBytes);

            // ① 结算流：可靠写入 IPC（Archive 录制）。绝不丢——漏一条就漏一笔结算。
            //    IPC 本地极快,背压罕见;真背压时自旋直到成功(宁可短暂阻塞撮合,也不丢钱)。
            publishSettlementReliable(buffer, jsonBytes.length, response.getOrderId());

            // ② 实时流：尽力而为广播给 Risk/Quote。慢消费者背压→丢弃,不影响结算,不阻塞撮合。
            publishLiveBestEffort(buffer, jsonBytes.length, response.getOrderId());

        } catch (Exception e) {
            errorCount.increment();
            log.error("[AeronMatchResultPublisher] Serialization error for orderId={}",
                    response.getOrderId(), e);
        }
    }

    /**
     * 可靠写入结算流(IPC),自旋直到 Archive 录制成功。
     *
     * <p>结算依赖此流的完整性,因此除 {@code CLOSED}(关机)/{@code MAX_POSITION_EXCEEDED}(极端)
     * 外<b>绝不放弃</b>。IPC→本地 Archive 极快,持续背压意味着磁盘/Archive 严重异常,
     * 此时短暂阻塞撮合线程(可被延迟监控发现)远优于静默漏结算。
     */
    private void publishSettlementReliable(UnsafeBuffer buffer, int len, String orderId) {
        if (settlementPublication == null) {
            // 降级模式(Archive 未启用):无结算持久化——仅测试/无 Aeron 部署
            errorCount.increment();
            return;
        }
        long result;
        long retries = 0;
        while ((result = settlementPublication.offer(buffer, 0, len)) < 0) {
            if (result == Publication.CLOSED) {
                log.error("[AeronMatchResultPublisher] Settlement publication CLOSED — trade NOT recorded, orderId={}", orderId);
                errorCount.increment();
                return;   // 仅关机路径
            }
            if (result == Publication.MAX_POSITION_EXCEEDED) {
                log.error("[AeronMatchResultPublisher] Settlement MAX_POSITION_EXCEEDED, orderId={}", orderId);
                errorCount.increment();
                return;   // 极端,需运维介入
            }
            // BACK_PRESSURED / ADMIN_ACTION / (IPC 下 NOT_CONNECTED 不应出现) → 自旋直到成功
            if (++retries % 200_000 == 0) {
                backPressureCount.increment();
                log.warn("[AeronMatchResultPublisher] Settlement publish spinning {} retries — Archive slow? orderId={}",
                        retries, orderId);
            }
            Thread.yield();
        }
        lastPublishedPosition = result;
        sentCount.increment();
    }

    /**
     * 尽力而为广播到实时流(UDP MDC),供 Risk/Quote 消费。慢消费者丢弃,不阻塞撮合。
     */
    private void publishLiveBestEffort(UnsafeBuffer buffer, int len, String orderId) {
        long result = livePublication.offer(buffer, 0, len);
        if (result == Publication.BACK_PRESSURED) {
            int retries = 0;
            while (result == Publication.BACK_PRESSURED && retries < MAX_BACK_PRESSURE_RETRIES) {
                Thread.yield();
                result = livePublication.offer(buffer, 0, len);
                retries++;
            }
            if (result == Publication.BACK_PRESSURED) {
                // 实时消费方严重滞后:丢弃(不影响结算,结算走 IPC 流)
                log.debug("[AeronMatchResultPublisher] Live stream dropped for slow subscriber, orderId={}", orderId);
                return;
            }
        }
        handleOfferResult(result, orderId);
    }

    /**
     * 处理实时流 offer 返回值（仅日志/metric,不影响结算）。
     *
     * <p>注意:{@code lastPublishedPosition} 和 {@code sentCount} 由结算流
     * ({@code publishSettlementReliable})维护,此处不再触碰——快照的 archivePosition
     * 必须来自结算流,而非实时流。
     */
    private void handleOfferResult(long result, String orderId) {
        if (result > 0) {
            return;   // 实时广播成功,无需记录位点
        }

        switch ((int) result) {
            case (int) Publication.BACK_PRESSURED ->
                // send() 已经 spin retry 后才走到这里，正常不应到达此分支
                log.warn("[AeronMatchResultPublisher] Unexpected BACK_PRESSURED in handleOfferResult, orderId={}", orderId);

            case (int) Publication.NOT_CONNECTED ->
                // 暂无订阅方，安静丢弃（Risk/Quote 尚未订阅时的正常状态）
                log.trace("[AeronMatchResultPublisher] No active subscribers for orderId={}", orderId);

            case (int) Publication.ADMIN_ACTION ->
                // 内部管理操作（如订阅方变更），短暂等待后重试，此处记录 warn
                log.warn("[AeronMatchResultPublisher] Admin action in progress, orderId={}", orderId);

            case (int) Publication.CLOSED ->
                log.error("[AeronMatchResultPublisher] Publication is CLOSED, orderId={}", orderId);

            case (int) Publication.MAX_POSITION_EXCEEDED ->
                log.error("[AeronMatchResultPublisher] Max position exceeded, orderId={}", orderId);

            default ->
                log.warn("[AeronMatchResultPublisher] Unexpected offer result={}, orderId={}", result, orderId);
        }
    }

    /* ══════════════════════════════════════════════════════════════
     *  Metric 暴露（供 Actuator / Prometheus 采集）
     * ══════════════════════════════════════════════════════════════ */

    public long getSentCount()         { return sentCount.sum(); }
    public long getBackPressureCount() { return backPressureCount.sum(); }
    public long getErrorCount()        { return errorCount.sum(); }

    /**
     * 最近一次成功发布的 Archive byte position。
     * 快照时写入 {@code MatchEngineSnapshot.archivePosition}，
     * 重启后 {@link com.exchange.match.core.service.EventReplayService} 从此处续播。
     */
    public long getLastPublishedPosition() { return lastPublishedPosition; }

    /**
     * 查询当前 Archive 中本 stream 的最新 recordingId（最大值 = 最近一次）。
     * 快照时写入 {@code MatchEngineSnapshot.archiveRecordingId}，供重放时直接定位录制。
     * 未启用 Archive 时返回 {@code -1}。
     */
    public long findCurrentRecordingId() {
        if (aeronArchive == null) return -1L;
        long[] found = {-1L};
        try {
            // 注意:录制的是结算流(SETTLEMENT_STREAM),不是实时 MDC 流
            aeronArchive.listRecordings(0, Integer.MAX_VALUE,
                    (controlSessionId, correlationId, recordingId, startTimestamp, stopTimestamp,
                     startPosition, stopPosition, initialTermId, segmentFileLength, termBufferLength,
                     mtuLength, sessionId, streamId, strippedChannel, originalChannel, sourceIdentity) -> {
                        if (streamId == MatchSettlementStream.SETTLEMENT_STREAM) {
                            found[0] = recordingId;  // iterate all; last (largest) id = most recent
                        }
                    });
        } catch (Exception e) {
            log.warn("[AeronMatchResultPublisher] Failed to query recordingId", e);
        }
        return found[0];
    }

    /**
     * 结算 publication 是否已连接（Archive 录制路径健康）。
     * 可用于健康检查探针。
     */
    public boolean isConnected() {
        Publication p = (settlementPublication != null) ? settlementPublication : livePublication;
        return p != null && p.isConnected();
    }
}
