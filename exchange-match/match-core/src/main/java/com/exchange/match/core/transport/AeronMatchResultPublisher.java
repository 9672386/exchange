package com.exchange.match.core.transport;

import com.exchange.match.core.model.MatchResponse;
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

    private Publication publication;

    /** channel/streamId 配置，保存供快照记录查询用。 */
    private PublisherChannelConfig cfg;

    /** 最近一次成功 offer 的 publication position，快照时写入 archivePosition 字段。 */
    private volatile long lastPublishedPosition = 0L;

    /** 成交结果录制专用 stream（与 Cluster 内部 stream 不冲突）。供 TradeSettlementForwarder 订阅。*/
    public static final int SETTLEMENT_RECORDING_STREAM = 2000;

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
        publication = aeron.addPublication(cfg.getChannel(), cfg.getStreamId());
        log.info("[AeronMatchResultPublisher] Publication ready — channel={}, streamId={}",
                cfg.getChannel(), cfg.getStreamId());

        // Archive 录制：持久化成交结果供 TradeSettlementForwarder 消费（替代 Kafka）
        if (aeronArchive != null) {
            // 录制 MDC 出站流（LOCAL = 发布者与 Archive 在同一进程）
            aeronArchive.startRecording(cfg.getChannel(), cfg.getStreamId(), SourceLocation.LOCAL);
            log.info("[AeronMatchResultPublisher] Archive recording started — stream={} (settlement)",
                    cfg.getStreamId());
        } else {
            log.warn("[AeronMatchResultPublisher] AeronArchive not available — " +
                    "match results will NOT be durably recorded; TradeSettlementForwarder uses MDC only");
        }
    }

    @Override
    public void destroy() {
        if (publication != null) {
            publication.close();
        }
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

            long result = publication.offer(buffer, 0, jsonBytes.length);

            // 背压时自旋重试，保证 Archive 能录制到本条消息
            if (result == Publication.BACK_PRESSURED) {
                int retries = 0;
                while (result == Publication.BACK_PRESSURED && retries < MAX_BACK_PRESSURE_RETRIES) {
                    Thread.yield();
                    result = publication.offer(buffer, 0, jsonBytes.length);
                    retries++;
                }
                if (retries > 0) {
                    backPressureCount.add(retries);
                }
                if (result == Publication.BACK_PRESSURED) {
                    // 超过重试上限：Archive 可能漏录，须告警
                    errorCount.increment();
                    log.error("[AeronMatchResultPublisher] BACK_PRESSURED exceeded {} retries — " +
                              "message DROPPED, Archive may miss this trade! orderId={}",
                              MAX_BACK_PRESSURE_RETRIES, response.getOrderId());
                    return;
                }
            }

            handleOfferResult(result, response.getOrderId());

        } catch (Exception e) {
            errorCount.increment();
            log.error("[AeronMatchResultPublisher] Serialization error for orderId={}",
                    response.getOrderId(), e);
        }
    }

    /**
     * 处理 Aeron offer 返回值，记录 metric，不阻塞撮合线程。
     */
    private void handleOfferResult(long result, String orderId) {
        if (result > 0) {
            // 正值 = 新的 Publication position，发送成功；记录供快照使用
            lastPublishedPosition = result;
            sentCount.increment();
            return;
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
        if (aeronArchive == null || cfg == null) return -1L;
        long[] found = {-1L};
        try {
            aeronArchive.listRecordings(0, Integer.MAX_VALUE,
                    (controlSessionId, correlationId, recordingId, startTimestamp, stopTimestamp,
                     startPosition, stopPosition, initialTermId, segmentFileLength, termBufferLength,
                     mtuLength, sessionId, streamId, strippedChannel, originalChannel, sourceIdentity) -> {
                        if (streamId == cfg.getStreamId()) {
                            found[0] = recordingId;  // iterate all; last (largest) id = most recent
                        }
                    });
        } catch (Exception e) {
            log.warn("[AeronMatchResultPublisher] Failed to query recordingId", e);
        }
        return found[0];
    }

    /**
     * Publication 是否已连接到至少一个订阅方。
     * 可用于健康检查探针。
     */
    public boolean isConnected() {
        return publication != null && publication.isConnected();
    }
}
