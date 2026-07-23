package com.exchange.match.core.service;

import com.exchange.match.model.MatchResponse;
import com.exchange.match.enums.MatchStatus;
import com.exchange.match.model.Trade;

import com.exchange.match.core.memory.MemoryManager;
import com.exchange.match.core.model.*;
import com.exchange.transport.aeron.config.AeronConfigFactory;
import com.exchange.transport.aeron.config.AeronConfigFactory.PublisherChannelConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.aeron.Aeron;
import io.aeron.Image;
import io.aeron.Subscription;
import io.aeron.archive.client.AeronArchive;
import lombok.extern.slf4j.Slf4j;
import org.agrona.DirectBuffer;
import io.aeron.FragmentAssembler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 撮合引擎启动事件重放服务（非 Cluster 模式专用）。
 *
 * <h3>恢复原理</h3>
 * <ol>
 *   <li>{@link StartupRecoveryService} 加载文件快照，将内存状态恢复到快照时刻。</li>
 *   <li>本服务从 {@code snapshot.archivePosition} 开始重放 Aeron Archive 中的
 *       {@link MatchResponse} 记录，将快照之后发生的成交（填单）和撤单补全到内存订单薄。</li>
 *   <li>快照后入队但尚未成交且仍处于挂单状态的委托，在重启后会超过 10 s Snowflake 时间戳校验，
 *       由客户端重新提交新委托即可，系统不额外处理。</li>
 * </ol>
 *
 * <h3>Cluster 模式</h3>
 * <p>当 {@code match.cluster.enabled=true} 时，Aeron Cluster 框架通过 Raft 日志 + 快照
 * 自动恢复，{@link StartupRecoveryService} 直接跳过，本服务不会被调用。
 *
 * <h3>Aeron 不可用时的降级</h3>
 * <p>若 {@link Aeron} / {@link AeronArchive} Bean 不存在（测试 / 无 Aeron 部署），
 * 本服务返回 {@code true} 并记录日志，以快照为唯一恢复点。
 */
@Slf4j
@Service
public class EventReplayService {

    // ── 依赖（可选注入：非 Aeron 部署时为 null）─────────────────────────────
    @Autowired(required = false)
    private Aeron aeron;

    @Autowired(required = false)
    private AeronArchive aeronArchive;

    @Autowired
    private MemoryManager memoryManager;

    @Autowired
    private ObjectMapper objectMapper;

    // ── 配置 ─────────────────────────────────────────────────────────────────
    /**
     * 重放专用 Subscription channel，与 TradeSettlementForwarder 的 40300 端口区分开，
     * 避免同一进程内订阅冲突。
     */
    @Value("${match.archive.event-replay-channel:aeron:udp?endpoint=localhost:40302}")
    private String replayChannel;

    /** 重放专用 stream ID，不与 TradeSettlementForwarder (2001) 冲突。 */
    private static final int REPLAY_STREAM = 2002;

    /** 等待 replay Image 建立连接的超时时间。 */
    private static final long IMAGE_CONNECT_TIMEOUT_MS = 10_000L;

    // ─────────────────────────────────────────────────────────────────────────
    //  Public API
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * 从快照中记录的 Archive 位点开始重放，异步执行。
     *
     * @param snapshot 已通过 {@link SnapshotRestoreService} 恢复到内存的快照对象
     * @return {@code true} 表示重放成功或无需重放；{@code false} 表示重放失败（内存状态可能不完整）
     */
    /** 最近一次重放状态（观测用；MonitoringController 读取）。 */
    private volatile String replayStatus = "IDLE";

    /** 供 MonitoringController 查询当前重放状态。 */
    public String getReplayStatus() {
        return replayStatus;
    }

    public CompletableFuture<Boolean> startEventReplay(MatchEngineSnapshot snapshot) {
        if (aeron == null || aeronArchive == null) {
            log.info("[EventReplay] Aeron not available — snapshot is the sole recovery point, no replay");
            return CompletableFuture.completedFuture(true);
        }

        long archiveRecordingId = snapshot.getArchiveRecordingId();
        long archivePosition    = snapshot.getArchivePosition();

        if (archiveRecordingId < 0) {
            log.info("[EventReplay] Snapshot has no archiveRecordingId — skipping replay (first run or Archive disabled)");
            return CompletableFuture.completedFuture(true);
        }

        return CompletableFuture.supplyAsync(() -> {
            try {
                doReplay(archiveRecordingId, archivePosition);
                return true;
            } catch (Exception e) {
                log.error("[EventReplay] Replay failed — memory state may be incomplete, manual inspection required", e);
                return false;
            }
        });
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Core replay logic
    // ─────────────────────────────────────────────────────────────────────────

    private void doReplay(long recordingId, long startPosition) throws Exception {
        long targetPosition = resolveTargetPosition(recordingId);

        if (targetPosition <= startPosition) {
            log.info("[EventReplay] No events after snapshot position={} (recordingEnd={}); nothing to replay",
                    startPosition, targetPosition);
            return;
        }

        long replayLength = targetPosition - startPosition;
        log.info("[EventReplay] Replaying recordingId={} from position={} length={} (target={})",
                recordingId, startPosition, replayLength, targetPosition);

        long replaySessionId = aeronArchive.startReplay(
                recordingId, startPosition, replayLength, replayChannel, REPLAY_STREAM);

        AtomicInteger appliedCount = new AtomicInteger();
        try (Subscription sub = aeron.addSubscription(replayChannel, REPLAY_STREAM)) {
            Image image = awaitImage(sub, (int) replaySessionId, IMAGE_CONNECT_TIMEOUT_MS);
            FragmentAssembler assembler = new FragmentAssembler(
                    (buf, off, len, hdr) -> applyFragment(buf, off, len, appliedCount));

            // Poll until the image closes (all bytes within replayLength consumed)
            while (!image.isClosed()) {
                sub.poll(assembler, 20);
                Thread.yield();
            }
        } finally {
            try { aeronArchive.stopReplay(replaySessionId); } catch (Exception ignored) {}
        }

        log.info("[EventReplay] Complete — applied {} MatchResponse events, orderBooks={}",
                appliedCount.get(), memoryManager.getAllOrderBooks().size());
    }

    /**
     * 解析目标重放终点：
     * <ul>
     *   <li>录制已停止（上次进程正常关闭）→ 使用 {@code stopPosition}。</li>
     *   <li>录制仍在 Archive 账本中标记为活跃（上次进程崩溃）→ 查询 {@code getRecordingPosition}。</li>
     * </ul>
     */
    private long resolveTargetPosition(long recordingId) {
        long[] target = {0L};
        aeronArchive.listRecordings(recordingId, 1,
                (controlSessionId, correlationId, rid,
                 startTimestamp, stopTimestamp,
                 startPosition, stopPosition,
                 initialTermId, segmentFileLength, termBufferLength,
                 mtuLength, sessionId, streamId,
                 strippedChannel, originalChannel, sourceIdentity) -> {
                    if (stopPosition > 0) {
                        // Recording finished cleanly — use the known stop position
                        target[0] = stopPosition;
                    } else {
                        // Still open in Archive's view (crashed process) — query current write head
                        long pos = aeronArchive.getRecordingPosition(rid);
                        target[0] = Math.max(pos, 0L);
                    }
                });
        return target[0];
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Fragment application
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * 将单个 {@link MatchResponse} fragment 应用到内存订单薄：
     * <ol>
     *   <li>对每笔 Trade，将 buy/sell 双方订单执行部分成交；归零后从薄中移除。</li>
     *   <li>CANCELLED 状态：直接从薄中移除该委托。</li>
     * </ol>
     * <p>快照之后新进、尚未进入订单薄的委托找不到 Order 对象时静默跳过（超 10 s 超时后客户端重提）。
     */
    private void applyFragment(DirectBuffer buf, int offset, int length, AtomicInteger count) {
        try {
            byte[] bytes = new byte[length];
            buf.getBytes(offset, bytes);
            MatchResponse response = objectMapper.readValue(bytes, MatchResponse.class);
            count.incrementAndGet();

            // 1. Apply all trade fills
            List<Trade> trades = response.getTrades();
            if (trades != null && !trades.isEmpty()) {
                for (Trade trade : trades) {
                    applyTrade(trade);
                }
            }

            // 2. Apply cancellation
            if (response.getStatus() == MatchStatus.CANCELLED
                    && response.getOrderId() != null
                    && response.getSymbol() != null) {
                OrderBook ob = memoryManager.getOrderBook(response.getSymbol());
                if (ob != null) {
                    ob.removeOrder(response.getOrderId());
                    log.debug("[EventReplay] Applied cancel orderId={}", response.getOrderId());
                }
            }

        } catch (Exception e) {
            log.warn("[EventReplay] Failed to deserialize/apply MatchResponse fragment — skipping", e);
        }
    }

    private void applyTrade(Trade trade) {
        if (trade.getSymbol() == null) return;
        OrderBook ob = memoryManager.getOrderBook(trade.getSymbol());
        if (ob == null) return;

        applyFill(ob, trade.getBuyOrderId(), trade.getQuantity());
        applyFill(ob, trade.getSellOrderId(), trade.getQuantity());
    }

    /**
     * 将成交数量 {@code qty} 应用到 {@code orderId} 对应的挂单。
     * <ul>
     *   <li>剩余量归零 → 从薄中移除（全成交）。</li>
     *   <li>剩余量 &gt; 0 → 更新剩余量（部分成交）。</li>
     *   <li>订单不在薄中 → 静默跳过（快照后入队且尚未挂单成功，或已先被其他 fragment 移除）。</li>
     * </ul>
     */
    private void applyFill(OrderBook ob, String orderId, BigDecimal qty) {
        if (orderId == null || qty == null) return;
        Order order = ob.getOrder(orderId);
        if (order == null) return;  // Not in snapshot book — will time-out on client retry

        com.exchange.match.core.model.Symbol s = memoryManager.getSymbol(order.getSymbol());
        long qtyRaw = com.exchange.common.math.FixedPoint.fromBigDecimal(
                qty, s != null ? s.baseScale() : 8, java.math.RoundingMode.DOWN);
        order.updateFilledQuantity(qtyRaw);
        if (order.getRemainingQuantity() <= 0) {
            ob.removeOrder(orderId);
            log.debug("[EventReplay] Fully filled & removed orderId={}", orderId);
        } else {
            ob.updateOrder(order);
            log.debug("[EventReplay] Partial fill orderId={} remaining={}", orderId, order.getRemainingQuantity());
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Helpers
    // ─────────────────────────────────────────────────────────────────────────

    private Image awaitImage(Subscription sub, int sessionId, long timeoutMs) throws TimeoutException {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (true) {
            Image img = sub.imageBySessionId(sessionId);
            if (img != null) return img;
            if (System.currentTimeMillis() > deadline) {
                throw new TimeoutException(
                        "[EventReplay] Replay image not connected after " + timeoutMs + " ms");
            }
            Thread.yield();
        }
    }
}
