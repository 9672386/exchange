package com.exchange.account.core.cluster.client;

import com.exchange.account.core.gateway.AssetGatewayService;
import com.exchange.match.core.model.MatchResponse;
import com.exchange.match.core.model.Trade;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.aeron.Aeron;
import io.aeron.Image;
import io.aeron.Subscription;
import io.aeron.archive.client.AeronArchive;
import io.aeron.driver.MediaDriver;
import lombok.extern.slf4j.Slf4j;
import org.agrona.DirectBuffer;
import io.aeron.FragmentAssembler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.context.annotation.DependsOn;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeoutException;

/**
 * 成交事件转发器：Match Archive → Asset Cluster Ingress。
 *
 * <h3>旧路径（已移除）</h3>
 * <pre>
 *   MatchEngine → Kafka(match-results) → [Kafka Consumer] → Asset Cluster
 * </pre>
 *
 * <h3>新路径（Kafka-free）</h3>
 * <pre>
 *   MatchEngine
 *     → AeronMatchResultPublisher(MDC) ─── Match Archive(port 8010) 持久化
 *     → [TradeSettlementForwarder] ← Archive replay(port 40300)
 *           └→ BATCH_SETTLE → Asset Cluster Ingress(port 20140)
 * </pre>
 *
 * <h3>持久化保证 / 位点一致性</h3>
 * <p>Match Archive 将 MDC 流录制到磁盘。本转发器在启动时通过 MATCH_POSITION_QUERY 从
 * Asset Cluster Snapshot 中读取已提交的 byte position，从该点续读，保证位点与账本严格原子：
 * {@code archivePosition} 随 BATCH_SETTLE 写入同一 Raft 日志条目，Cluster 宕机重启后
 * Snapshot 恢复的位点与账本状态天然一致，不会出现"位点超前/落后于账本"的数据窗口。
 *
 * <h3>幂等保证</h3>
 * <p>BalanceLedger 内部 {@code processedBizNos} 检测重复 tradeId，重放时安全跳过。
 *
 * <h3>失败语义</h3>
 * <p>fragment 处理失败时抛出异常，poll 循环退出，外层 retry 等待 5s 后重连续读。
 * 位点未更新，重启后会重试当前事件（BalanceLedger 幂等保护）。
 */
@Slf4j
@Component
@DependsOn("assetGatewayService")   // 确保 AssetGatewayService.connect() 在 start() 之前完成
@ConditionalOnProperty(name = "asset.cluster.enabled", havingValue = "true", matchIfMissing = true)
public class TradeSettlementForwarder {

    // ── Match Archive 配置（消费侧）─────────────────────────────────
    @Value("${match.archive.control-channel:aeron:udp?endpoint=localhost:8010}")
    private String matchArchiveControlChannel;

    @Value("${match.archive.replay-channel:aeron:udp?endpoint=localhost:40300}")
    private String replayChannel;

    /** 回放使用独立 stream（与录制 stream 不同，避免冲突）*/
    private static final int REPLAY_STREAM = 2001;

    // ── 依赖注入 ──────────────────────────────────────────────────────
    /**
     * 通过 AssetGatewayService 发送 BATCH_SETTLE 并等待 Egress 确认，
     * 同时用于启动时查询 Cluster 已持久化的 Match Archive 消费位点。
     *
     * <p>之前版本维护自有 {@code AeronCluster} 连接（fire-and-forget）；
     * 现在统一走 Gateway，利用其 correlationId + CompletableFuture 等确认，
     * 失败时抛异常触发 subscribe 重试，位点不推进。
     */
    @Autowired
    private AssetGatewayService assetGatewayService;

    // ── 内部状态 ─────────────────────────────────────────────────────
    private final ObjectMapper   objectMapper;

    /** Archive 消费位点（从 Cluster Snapshot 中读取，随 BATCH_SETTLE 原子更新）。 */
    private volatile long startPosition = 0L;

    private Thread           subscribeThread;
    private volatile boolean running = true;

    public TradeSettlementForwarder() {
        this.objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    // =========================================================================
    // Lifecycle
    // =========================================================================

    @PostConstruct
    public void start() {
        // 位点查询移入 subscribeOnce（每次重连都取 Cluster 的最新已提交位点）。
        // 查询失败绝不允许 fallback 到 0：从 0 重放全部历史成交时，老 tradeId 的
        // 幂等记录早已 TTL 驱逐，会导致重复结算或 frozen 不足死循环——灾难性错误。
        subscribeThread = new Thread(this::subscribeLoop, "settlement-archive-subscriber");
        subscribeThread.setDaemon(true);
        subscribeThread.start();
    }

    @PreDestroy
    public void stop() {
        running = false;
        if (subscribeThread != null) subscribeThread.interrupt();
        log.info("[TradeSettlementForwarder] Stopped");
    }

    // =========================================================================
    // Archive 消费循环
    // =========================================================================

    private void subscribeLoop() {
        while (running) {
            try {
                subscribeOnce();
            } catch (Exception e) {
                if (running) {
                    log.error("[TradeSettlementForwarder] Archive subscriber error, retrying in 5s", e);
                    sleep(5_000);
                }
            }
        }
    }

    private void subscribeOnce() throws Exception {
        // 每次会话开始前从 Cluster 取权威位点（fail-fast：查询失败直接抛出，
        // 由外层 retry 循环 5s 后重试，绝不 fallback 到 0 从头重放）
        startPosition = assetGatewayService.queryMatchArchivePosition(0L);
        log.info("[TradeSettlementForwarder] Cluster matchArchivePosition={}", startPosition);

        MediaDriver driver = MediaDriver.launchEmbedded();
        try (Aeron aeron = Aeron.connect(new Aeron.Context().aeronDirectoryName(driver.aeronDirectoryName()));
             AeronArchive archive = AeronArchive.connect(new AeronArchive.Context()
                     .controlRequestChannel(matchArchiveControlChannel)
                     .controlResponseChannel("aeron:udp?endpoint=localhost:0")
                     .aeron(aeron))) {

            // 找到成交结果录制
            long recordingId = findMatchRecording(archive);
            while (recordingId < 0 && running) {
                log.info("[TradeSettlementForwarder] No match recording found, waiting 2s...");
                sleep(2_000);
                recordingId = findMatchRecording(archive);
            }
            if (!running) return;

            final long rid = recordingId;
            log.info("[TradeSettlementForwarder] Replay recordingId={} startPosition={}", rid, startPosition);

            long replaySessionId = archive.startReplay(
                    rid, startPosition, Long.MAX_VALUE, replayChannel, REPLAY_STREAM);

            try (Subscription sub = aeron.addSubscription(replayChannel, REPLAY_STREAM)) {
                Image image = awaitImage(sub, (int) replaySessionId, 15_000);
                FragmentAssembler assembler = new FragmentAssembler(
                        (buf, off, len, hdr) -> onFragment(buf, off, len, hdr, rid));

                log.info("[TradeSettlementForwarder] Replay image connected, consuming...");
                while (running && !image.isClosed()) {
                    sub.poll(assembler, 10);
                    Thread.yield();
                }
                log.warn("[TradeSettlementForwarder] Replay image closed, reconnecting...");
            } finally {
                try { archive.stopReplay(replaySessionId); } catch (Exception ignored) {}
            }
        } finally {
            driver.close();
        }
    }

    // =========================================================================
    // Fragment handler
    // =========================================================================

    private void onFragment(DirectBuffer buffer, int offset, int length,
                            io.aeron.logbuffer.Header header, long recordingId) {
        try {
            byte[] bytes = new byte[length];
            buffer.getBytes(offset, bytes);
            MatchResponse response = objectMapper.readValue(bytes, MatchResponse.class);

            List<Trade> trades = response.getTrades();
            if (trades != null && !trades.isEmpty()) {
                // 将当前 fragment 的 Archive position 随 BATCH_SETTLE 一同发送到 Cluster，
                // Cluster 原子写入 Raft 日志 → Snapshot，保证位点与结算的一致性
                forwardBatchSettlement(trades, header.position());
            }
            // 无论是否有 trades，推进本地位点（下次重启从此处续读）
            startPosition = header.position();

        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("[TradeSettlementForwarder] Fragment processing failed", e);
        }
    }

    // =========================================================================
    // Forward to Asset Cluster
    // =========================================================================

    /**
     * 将 N 笔成交打包为一条 BATCH_SETTLE 消息发送到 Asset Cluster，并等待 Egress 确认。
     *
     * <h3>与旧实现的差异</h3>
     * <p>旧实现直接 {@code assetClusterClient.offer()} 后立即返回（fire-and-forget）：
     * Cluster 内部处理失败时调用方无感知，位点仍被推进，该批成交永久跳过不结算。
     *
     * <p>新实现委托给 {@link AssetGatewayService#batchSettle(Long, List, long)}：
     * 内部通过 correlationId + CompletableFuture 等待 Egress 响应（超时 5s）。
     * Cluster 返回 ERROR 或超时时抛出异常，poll 循环退出，5s 后重试，
     * 位点不推进，成交不会漏结算。
     *
     * @param trades          本次批量成交列表
     * @param archivePosition 当前 fragment 在 Match Archive 中的 byte position，
     *                        随 BATCH_SETTLE 写入同一 Raft 日志条目，与结算原子提交
     */
    private void forwardBatchSettlement(List<Trade> trades, long archivePosition) {
        try {
            List<Map<String, Object>> tradeItems = new ArrayList<>(trades.size());
            Long routingUserId = null;

            for (Trade trade : trades) {
                String[] parts    = trade.getSymbol().split("_", 2);
                String baseAsset  = parts.length > 0 ? parts[0] : "UNKNOWN";
                String quoteAsset = parts.length > 1 ? parts[1] : "USDT";

                Map<String, Object> t = new HashMap<>();
                t.put("tradeId",    trade.getTradeId());
                t.put("buyerId",    trade.getBuyUserId());
                t.put("sellerId",   trade.getSellUserId());
                t.put("baseAsset",  baseAsset);
                t.put("quoteAsset", quoteAsset);
                t.put("qty",        trade.getQuantity().toPlainString());
                t.put("quoteAmt",   trade.getAmount().toPlainString());
                t.put("buyFee",     trade.getBuyFee()  != null ? trade.getBuyFee().toPlainString()  : "0");
                t.put("sellFee",    trade.getSellFee() != null ? trade.getSellFee().toPlainString() : "0");
                tradeItems.add(t);

                // 取第一笔成交的 buyerId 作为分片路由键（单节点时任意值均路由到 shard 0）
                if (routingUserId == null) {
                    routingUserId = trade.getBuyUserId();
                }
            }

            if (routingUserId == null) routingUserId = 0L;

            // 同步调用：等待 Egress 返回 OK；失败时抛异常 → subscribeOnce 退出 → retry
            assetGatewayService.batchSettle(routingUserId, tradeItems, archivePosition);
            log.debug("[TradeSettlementForwarder] BATCH_SETTLE confirmed, trades={}", trades.size());

        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException(
                    "[TradeSettlementForwarder] batchSettle failed, trades=" + trades.size(), e);
        }
    }

    // =========================================================================
    // Archive helpers
    // =========================================================================

    /** 查找 Match 引擎录制的成交结果 recording（按 MDC stream ID 匹配）。 */
    private long findMatchRecording(AeronArchive archive) {
        int targetStream = resolveMdcStreamId();
        long[] result = {-1L};
        archive.listRecordings(0, 1_000,
                (controlSessionId, correlationId, recordingId, startTimestamp, stopTimestamp,
                 startPosition, stopPosition, initialTermId, segmentFileLength, termBufferLength,
                 mtuLength, sessionId, streamId, strippedChannel, originalChannel, sourceIdentity) -> {
                    if (streamId == targetStream && result[0] < 0) {
                        result[0] = recordingId;
                    }
                });
        return result[0];
    }

    private int resolveMdcStreamId() {
        try {
            String v = System.getenv("STREAM_ID");
            return (v != null && !v.isBlank()) ? Integer.parseInt(v.trim()) : 1001;
        } catch (Exception e) {
            return 1001;
        }
    }

    private Image awaitImage(Subscription sub, int sessionId, long timeoutMs) throws TimeoutException {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (true) {
            Image img = sub.imageBySessionId(sessionId);
            if (img != null) return img;
            if (System.currentTimeMillis() > deadline) {
                throw new TimeoutException("Replay image not connected after " + timeoutMs + "ms");
            }
            Thread.yield();
        }
    }

    private void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }
}
