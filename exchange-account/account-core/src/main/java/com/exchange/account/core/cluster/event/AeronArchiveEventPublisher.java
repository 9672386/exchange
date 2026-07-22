package com.exchange.account.core.cluster.event;

import com.exchange.account.api.dto.AssetStateChangeEvent;
import com.exchange.common.event.CoreSystemEvent;
import com.exchange.common.event.SystemEventReporter;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.aeron.Aeron;
import io.aeron.ExclusivePublication;
import io.aeron.Publication;
import io.aeron.archive.client.AeronArchive;
import io.aeron.archive.codecs.SourceLocation;
import lombok.extern.slf4j.Slf4j;
import org.agrona.concurrent.UnsafeBuffer;

import java.nio.ByteBuffer;

/**
 * Aeron Archive 实现的资产事件发布器（替代 KafkaAssetEventPublisher）。
 *
 * <h3>数据流</h3>
 * <pre>
 *   AssetClusteredService (Leader)
 *     → AeronArchiveEventPublisher.publish()
 *       → ExclusivePublication (aeron:ipc stream=1000)
 *         → Asset Cluster 内嵌 Archive 录制 (port 8020)
 *           → AssetArchiveSubscriber (account-persist) 回放落库
 * </pre>
 *
 * <h3>持久性</h3>
 * <p>与 Kafka 等价：Archive 将 IPC 流记录到磁盘，account-persist 启动时
 * 从上次 position 续读，崩溃不丢消息。
 *
 * <h3>初始化顺序</h3>
 * <p>此类 <b>不</b> 使用 {@code @PostConstruct}，因为 Archive 在 Cluster 启动后才可用。
 * 由 {@link com.exchange.account.lifecycle.AssetClusterLifecycle} 在
 * {@code clusterNode.start()} 成功后显式调用 {@link #connect}。
 *
 * <h3>Leader-only</h3>
 * <p>调用方（{@code AssetClusteredService.publishIfLeader}）已保证仅 Leader 发布，
 * 此类无需再做角色判断。
 */
@Slf4j
public class AeronArchiveEventPublisher implements AssetEventPublisher, AutoCloseable {

    /** IPC channel（进程内，零拷贝，与 Archive 共用同一 Aeron driver） */
    public static final String RECORDING_CHANNEL = "aeron:ipc";
    /** Asset 状态变更事件专用 stream ID（与 Cluster 内部 stream 不冲突） */
    public static final int    RECORDING_STREAM  = 1000;

    private final ObjectMapper objectMapper;

    private Aeron                aeron;
    private AeronArchive         archive;
    private ExclusivePublication publication;
    private final UnsafeBuffer   buffer    = new UnsafeBuffer(ByteBuffer.allocateDirect(65536));
    private volatile boolean     connected = false;

    /**
     * 累计丢弃事件数（发布失败）。
     *
     * <p><b>已知架构缺口</b>：事件发布是 Raft 状态机之外的副作用，
     * Leader 在账本提交后、发布前崩溃 → 事件永久丢失（日志重放不补发）。
     * 此计数器 &gt; 0 或进程曾非正常退出时，t_user_asset / t_fund_flow
     * 可能落后于内存账本，需运行对账任务（全量 BALANCE_QUERY vs DB diff）修复。
     */
    private final java.util.concurrent.atomic.AtomicLong droppedEvents = new java.util.concurrent.atomic.AtomicLong();

    /** 累计丢弃的事件数（监控告警用）。 */
    public long getDroppedEvents() {
        return droppedEvents.get();
    }

    /** 系统事件上报器（丢弃事件计数与告警）。 */
    private final SystemEventReporter eventReporter;

    public AeronArchiveEventPublisher() {
        this(SystemEventReporter.noop());
    }

    public AeronArchiveEventPublisher(SystemEventReporter eventReporter) {
        this.eventReporter = eventReporter != null ? eventReporter : SystemEventReporter.noop();
        this.objectMapper  = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    // =========================================================================
    // Lifecycle（由 AssetClusterLifecycle 在 ClusteredMediaDriver 启动后调用）
    // =========================================================================

    /**
     * 连接到 Archive 并开始录制。
     *
     * @param aeronDir              Aeron driver 目录（与 AssetClusterNode 使用相同目录）
     * @param archiveControlChannel Archive 控制通道，如 {@code aeron:udp?endpoint=localhost:8020}
     */
    public void connect(String aeronDir, String archiveControlChannel) {
        try {
            aeron = Aeron.connect(new Aeron.Context().aeronDirectoryName(aeronDir));

            archive = AeronArchive.connect(new AeronArchive.Context()
                    .controlRequestChannel(archiveControlChannel)
                    .controlResponseChannel("aeron:udp?endpoint=localhost:0")
                    .aeron(aeron));

            // 告知 Archive 录制 IPC stream 1000（幂等：若已在录制则忽略）
            archive.startRecording(RECORDING_CHANNEL, RECORDING_STREAM, SourceLocation.LOCAL);

            // 创建 IPC 发布者
            publication = aeron.addExclusivePublication(RECORDING_CHANNEL, RECORDING_STREAM);

            // 等待 Archive 订阅者就绪（通常 < 10ms）
            long deadline = System.currentTimeMillis() + 5_000;
            while (!publication.isConnected()) {
                if (System.currentTimeMillis() > deadline) {
                    throw new IllegalStateException("Archive subscription not connected after 5s");
                }
                Thread.yield();
            }

            connected = true;
            log.info("[AeronArchiveEventPublisher] Connected — aeronDir={} archive={} channel={} stream={}",
                    aeronDir, archiveControlChannel, RECORDING_CHANNEL, RECORDING_STREAM);

        } catch (Exception e) {
            log.error("[AeronArchiveEventPublisher] Failed to connect", e);
            // 不抛出：Cluster 继续运行，只是事件无法持久化（账本状态仍安全）
        }
    }

    @Override
    public void close() {
        connected = false;
        if (publication != null) { try { publication.close(); } catch (Exception ignored) {} }
        if (archive     != null) { try { archive.close();     } catch (Exception ignored) {} }
        if (aeron       != null) { try { aeron.close();       } catch (Exception ignored) {} }
        log.info("[AeronArchiveEventPublisher] Closed");
    }

    // =========================================================================
    // Publish
    // =========================================================================

    @Override
    public void publish(AssetStateChangeEvent event) {
        if (!connected || publication == null) {
            dropped(event, "not connected");
            return;
        }
        try {
            byte[] bytes = objectMapper.writeValueAsBytes(event);
            buffer.putBytes(0, bytes);

            long result;
            int  retries = 0;
            while ((result = publication.offer(buffer, 0, bytes.length)) < 0) {
                if (result == Publication.CLOSED || result == Publication.NOT_CONNECTED) {
                    dropped(event, "publication unavailable, result=" + result);
                    return;
                }
                if (++retries > 20_000) {
                    dropped(event, "offer timeout after " + retries + " retries");
                    return;
                }
                Thread.yield();
            }
            log.debug("[AeronArchiveEventPublisher] Published eventId={} pos={}", event.getEventId(), result);

        } catch (Exception e) {
            droppedEvents.incrementAndGet();
            log.error("[AeronArchiveEventPublisher] Failed to publish eventId={} (total dropped={})",
                    event.getEventId(), droppedEvents.get(), e);
        }
    }

    private void dropped(AssetStateChangeEvent event, String reason) {
        long total = droppedEvents.incrementAndGet();
        // 事件丢失意味着 DB 与账本不一致，必须触发对账。
        // 此处使用事件自带的 clusterTimestamp 作为参考时间，与状态机时钟保持一致。
        eventReporter.record(CoreSystemEvent.EVENT_PUBLISH_DROPPED, event.getClusterTimestamp(),
                () -> "reason=" + reason + " eventId=" + event.getEventId()
                        + " userId=" + event.getUserId() + " asset=" + event.getAsset());
        log.error("[AeronArchiveEventPublisher] EVENT DROPPED ({}) eventId={} totalDropped={} "
                + "— DB may diverge from ledger, reconciliation required",
                reason, event.getEventId(), total);
    }
}
