package com.exchange.account.persist.subscriber;

import com.exchange.account.api.dto.AssetStateChangeEvent;
import com.exchange.account.core.cluster.event.AeronArchiveEventPublisher;
import com.exchange.account.persist.entity.ArchivePosition;
import com.exchange.account.persist.repository.ArchivePositionMapper;
import com.exchange.account.persist.service.AssetPersistService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.aeron.Aeron;
import io.aeron.Image;
import io.aeron.Subscription;
import io.aeron.archive.client.AeronArchive;
import io.aeron.driver.MediaDriver;
import io.aeron.logbuffer.FragmentHandler;
import io.aeron.logbuffer.Header;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.agrona.DirectBuffer;
import io.aeron.FragmentAssembler;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeoutException;

/**
 * 资产状态变更持久化订阅者（替代 KafkaAssetPersistenceConsumer）。
 *
 * <h3>数据流</h3>
 * <pre>
 *   Asset Cluster Archive (port 8020)
 *     → replay (aeron:udp?endpoint=localhost:40201, stream 1001)
 *       → AssetArchiveSubscriber.onFragment()
 *         → fundFlowService.record() + assetService.upsertBalance()
 *           → DB
 * </pre>
 *
 * <h3>持久化消费位点</h3>
 * <p>等价于 Kafka offset：每条事件处理完后将 Archive byte position 写入
 * {@code t_archive_position}。服务重启后从此处续读，零丢失。
 *
 * <h3>幂等保证</h3>
 * <ul>
 *   <li>内存缓存（进程内去重，性能优化）：{@code processedEventIds}</li>
 *   <li>DB 唯一索引（{@code t_fund_flow.event_id} UNIQUE）：跨重启的最终兜底，
 *       重复插入按幂等跳过处理（不抛出，避免毒消息死循环）</li>
 * </ul>
 *
 * <h3>事务原子性</h3>
 * <p>流水 + 余额 UPSERT + 消费位点在 {@link AssetPersistService#persistEvent}
 * 的同一个 DB 事务中提交，崩溃不产生中间状态。
 *
 * <h3>错误处理</h3>
 * <p>fragment 处理失败时抛出 {@link RuntimeException}，poll 循环捕获后
 * 退出当前 subscribe() 调用，外层 retry 循环等待 5s 后重新连接 Archive 重试。
 * 消费位点未更新（抛出前的 position），重试时会重新处理同一条事件（DB 幂等保护）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AssetArchiveSubscriber implements DisposableBean {

    // ── 被复用的常量（与发布者对齐） ──────────────────────────────
    private static final String RECORDING_CHANNEL = AeronArchiveEventPublisher.RECORDING_CHANNEL;
    private static final int    RECORDING_STREAM  = AeronArchiveEventPublisher.RECORDING_STREAM;
    private static final int    REPLAY_STREAM     = 1001;

    // ── 依赖注入 ──────────────────────────────────────────────────
    private final AssetPersistService   persistService;
    private final ArchivePositionMapper positionMapper;

    @Value("${asset.archive.control-channel:aeron:udp?endpoint=localhost:8020}")
    private String archiveControlChannel;

    /** Archive 将录制数据回放到此 UDP 端点供本订阅者接收 */
    @Value("${asset.archive.replay-channel:aeron:udp?endpoint=localhost:40201}")
    private String replayChannel;

    // ── 内部状态 ──────────────────────────────────────────────────
    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    /** 进程内幂等缓存上限（超过则整体清空，避免无界增长；DB 唯一索引才是真兜底） */
    private static final int MAX_CACHED_EVENT_IDS = 100_000;

    /** 进程内幂等缓存（重启后清空，DB 唯一索引兜底） */
    private final Set<String> processedEventIds = ConcurrentHashMap.newKeySet();

    private Thread         pollingThread;
    private volatile boolean running = true;

    // =========================================================================
    // Lifecycle
    // =========================================================================

    @PostConstruct
    public void start() {
        pollingThread = new Thread(this::run, "asset-archive-subscriber");
        pollingThread.setDaemon(true);
        pollingThread.start();
        log.info("[AssetArchiveSubscriber] Started — archive={} replayChannel={}",
                archiveControlChannel, replayChannel);
    }

    @Override
    public void destroy() {
        running = false;
        if (pollingThread != null) pollingThread.interrupt();
        log.info("[AssetArchiveSubscriber] Stopped");
    }

    // =========================================================================
    // Subscribe loop（外层 retry）
    // =========================================================================

    private void run() {
        while (running) {
            try {
                subscribe();
            } catch (Exception e) {
                if (running) {
                    log.error("[AssetArchiveSubscriber] Subscriber error, retrying in 5s", e);
                    sleep(5_000);
                }
            }
        }
    }

    /**
     * 一次完整的 Archive 连接 + 回放 + 消费会话。
     * 任何异常抛出后由外层 retry 循环重新发起。
     */
    private void subscribe() throws Exception {
        MediaDriver driver = MediaDriver.launchEmbedded();
        try (Aeron aeron = Aeron.connect(new Aeron.Context().aeronDirectoryName(driver.aeronDirectoryName()));
             AeronArchive archive = AeronArchive.connect(new AeronArchive.Context()
                     .controlRequestChannel(archiveControlChannel)
                     .controlResponseChannel("aeron:udp?endpoint=localhost:0")
                     .aeron(aeron))) {

            // 1. 找到录制（发布者尚未启动时可能还没有录制，等待重试）
            long recordingId = findRecording(archive);
            while (recordingId < 0 && running) {
                log.info("[AssetArchiveSubscriber] No recording found yet for channel={} stream={}, retrying in 2s",
                        RECORDING_CHANNEL, RECORDING_STREAM);
                sleep(2_000);
                recordingId = findRecording(archive);
            }
            if (!running) return;

            // 2. 从 DB 取上次消费位点
            final long rid = recordingId;
            ArchivePosition savedPos = positionMapper.selectById(rid);
            long startPosition = (savedPos != null) ? savedPos.getPosition() : 0L;

            log.info("[AssetArchiveSubscriber] Starting replay — recordingId={} startPosition={}",
                    rid, startPosition);

            // 3. 发起回放（Long.MAX_VALUE：追上后继续接收实时新录制）
            long replaySessionId = archive.startReplay(
                    rid, startPosition, Long.MAX_VALUE, replayChannel, REPLAY_STREAM);

            try (Subscription sub = aeron.addSubscription(replayChannel, REPLAY_STREAM)) {
                // 等待 Archive 回放 Image 连接（最多 15s）
                Image image = awaitImage(sub, (int) replaySessionId, 15_000);

                FragmentAssembler assembler = new FragmentAssembler(
                        (buf, off, len, hdr) -> onFragment(buf, off, len, hdr, rid));

                log.info("[AssetArchiveSubscriber] Replay image connected, polling...");

                while (running && !image.isClosed()) {
                    int fragments = sub.poll(assembler, 10);
                    if (fragments == 0) Thread.yield();
                }

                log.warn("[AssetArchiveSubscriber] Replay image closed, will reconnect");

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

    private void onFragment(DirectBuffer buffer, int offset, int length, Header header, long recordingId) {
        try {
            byte[] bytes = new byte[length];
            buffer.getBytes(offset, bytes);
            AssetStateChangeEvent event = objectMapper.readValue(bytes, AssetStateChangeEvent.class);

            // 进程内快速去重（仅性能优化；真正的幂等由 t_fund_flow.event_id 唯一键保证）
            if (!processedEventIds.add(event.getEventId())) {
                log.debug("[AssetArchiveSubscriber] Duplicate eventId={} skipped (in-memory)", event.getEventId());
                updatePosition(recordingId, header.position());
                return;
            }
            // 防止无界增长（重启清空无碍，DB 幂等兜底）
            if (processedEventIds.size() > MAX_CACHED_EVENT_IDS) {
                processedEventIds.clear();
            }

            log.debug("[AssetArchiveSubscriber] Processing eventId={} type={} userId={} accountType={} asset={}",
                    event.getEventId(), event.getEventType(), event.getUserId(),
                    event.getAccountType(), event.getAsset());

            // 流水 + 余额 + 位点在同一个 DB 事务中原子提交：
            //   - 任意中间点崩溃 → 整体回滚 → 重启后从旧位点重放 → eventId 幂等跳过
            //   - 不再存在"流水已写但位点未推进"或反之的窗口
            persistService.persistEvent(event, recordingId,
                    RECORDING_CHANNEL, RECORDING_STREAM, header.position());

            log.debug("[AssetArchiveSubscriber] Persisted eventId={} position={}",
                    event.getEventId(), header.position());

        } catch (Exception e) {
            log.error("[AssetArchiveSubscriber] Failed to process fragment at position={}",
                    header.position(), e);
            // 抛出让 poll 循环退出 → 外层 retry 重新连接
            throw new RuntimeException("Asset archive processing failed", e);
        }
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private long findRecording(AeronArchive archive) {
        long[] result = {-1L};
        archive.listRecordingsForUri(0, 1, RECORDING_CHANNEL, RECORDING_STREAM,
                (controlSessionId, correlationId, recordingId,
                 startTimestamp, stopTimestamp, startPosition, stopPosition,
                 initialTermId, segmentFileLength, termBufferLength, mtuLength,
                 sessionId, streamId, strippedChannel, originalChannel, sourceIdentity) ->
                        result[0] = recordingId);
        return result[0];
    }

    private Image awaitImage(Subscription sub, int sessionId, long timeoutMs) throws TimeoutException {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (true) {
            Image img = sub.imageBySessionId(sessionId);
            if (img != null) return img;
            if (System.currentTimeMillis() > deadline) {
                throw new TimeoutException(
                        "[AssetArchiveSubscriber] Replay image not connected after " + timeoutMs + "ms");
            }
            Thread.yield();
        }
    }

    private void updatePosition(long recordingId, long position) {
        positionMapper.upsertPosition(recordingId, RECORDING_CHANNEL, RECORDING_STREAM, position);
    }

    private void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }
}
