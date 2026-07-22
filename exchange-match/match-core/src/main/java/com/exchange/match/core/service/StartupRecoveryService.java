package com.exchange.match.core.service;

import com.exchange.match.model.MatchResponse;

import com.exchange.match.core.model.MatchEngineSnapshot;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

/**
 * 非 Cluster 模式下的撮合引擎启动恢复服务。
 *
 * <h3>两种恢复路径</h3>
 * <ul>
 *   <li><b>Cluster 模式</b>（{@code match.cluster.enabled=true}）：Aeron Cluster 框架通过
 *       Raft 日志 + {@code MatchClusteredService.onStart()} 自动完成快照加载和日志回放，
 *       本服务直接跳过，避免双重恢复。</li>
 *   <li><b>非 Cluster 模式</b>（默认）：
 *     <ol>
 *       <li>从 {@link SnapshotStorageService} 加载最新文件快照。</li>
 *       <li>通过 {@link SnapshotRestoreService} 将快照状态写入 {@link com.exchange.match.core.memory.MemoryManager}。</li>
 *       <li>通过 {@link EventReplayService} 从 Aeron Archive 重放快照后的 MatchResponse，
 *           补全成交和撤单，使内存订单薄与崩溃前保持一致。</li>
 *     </ol>
 *   </li>
 * </ul>
 *
 * <h3>快照后丢失的委托</h3>
 * <p>快照生成后、崩溃前进入订单薄但尚未成交的委托，其 orderId 是 Snowflake 格式；
 * 重启后超过 10 s 的 Snowflake 时间窗口，{@code MatchClusteredService} 会拒绝同一
 * orderId 的重新提交。客户端感知超时后以新 orderId 重新下单即可，系统无需额外处理。
 */
@Slf4j
@Service
public class StartupRecoveryService {

    @Autowired
    private SnapshotRestoreService snapshotRestoreService;

    @Autowired
    private SnapshotStorageService snapshotStorageService;

    @Autowired
    private EventReplayService eventReplayService;

    /** 是否启用 Aeron Cluster 模式；true 时跳过本服务（Cluster 框架自行恢复）。 */
    @Value("${match.cluster.enabled:false}")
    private boolean clusterEnabled;

    /** 是否启用启动恢复（非 Cluster 模式下的全局开关）。 */
    @Value("${match.recovery.enabled:true}")
    private boolean recoveryEnabled;

    /** 指定从特定快照 ID 恢复（留空则取最新快照）。 */
    @Value("${match.recovery.snapshot-id:}")
    private String recoverySnapshotId;

    // ─────────────────────────────────────────────────────────────────────────

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        // Cluster 模式：交由 Aeron Cluster 框架恢复，跳过
        if (clusterEnabled) {
            log.info("[StartupRecovery] Cluster mode enabled — recovery delegated to Aeron Cluster framework");
            return;
        }

        if (!recoveryEnabled) {
            log.info("[StartupRecovery] Recovery disabled (match.recovery.enabled=false)");
            return;
        }

        log.info("[StartupRecovery] Starting standalone recovery...");
        try {
            // 1. 加载快照
            MatchEngineSnapshot snapshot = loadSnapshot();
            if (snapshot == null) {
                log.info("[StartupRecovery] No snapshot found — starting with empty state");
                return;
            }

            // 2. 将快照状态写入内存
            snapshotRestoreService.restoreFromSnapshot(snapshot);

            // 3. 从 Archive 重放快照后的成交 / 撤单事件
            replayEventsAfterSnapshot(snapshot);

            log.info("[StartupRecovery] Recovery complete: snapshotId={}, lastCommandId={}, archivePosition={}",
                    snapshot.getSnapshotId(), snapshot.getLastCommandId(), snapshot.getArchivePosition());

        } catch (Exception e) {
            log.error("[StartupRecovery] Recovery failed — engine starting with potentially incomplete state", e);
            throw new RuntimeException("StartupRecovery failed", e);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────

    private MatchEngineSnapshot loadSnapshot() {
        if (recoverySnapshotId != null && !recoverySnapshotId.isBlank()) {
            log.info("[StartupRecovery] Loading specified snapshot: {}", recoverySnapshotId);
            return snapshotStorageService.loadSnapshotById(recoverySnapshotId);
        }
        log.info("[StartupRecovery] Loading latest snapshot");
        return snapshotStorageService.loadLatestSnapshot();
    }

    private void replayEventsAfterSnapshot(MatchEngineSnapshot snapshot) {
        log.info("[StartupRecovery] Replaying events after snapshot: archiveRecordingId={}, archivePosition={}",
                snapshot.getArchiveRecordingId(), snapshot.getArchivePosition());

        eventReplayService.startEventReplay(snapshot)
                .thenAccept(success -> {
                    if (success) {
                        log.info("[StartupRecovery] Event replay completed successfully");
                    } else {
                        log.error("[StartupRecovery] Event replay returned failure — " +
                                "order book may be incomplete; manual inspection recommended");
                    }
                })
                .exceptionally(ex -> {
                    log.error("[StartupRecovery] Event replay threw exception", ex);
                    return null;
                });
    }

    /**
     * 手动触发恢复（运维接口，如线上 HTTP 触发）。
     *
     * @param snapshotId 指定快照 ID；null 表示最新快照
     */
    public void triggerRecovery(String snapshotId) {
        log.info("[StartupRecovery] Manual recovery triggered: snapshotId={}", snapshotId);
        try {
            MatchEngineSnapshot snapshot = snapshotId != null && !snapshotId.isBlank()
                    ? snapshotStorageService.loadSnapshotById(snapshotId)
                    : snapshotStorageService.loadLatestSnapshot();

            if (snapshot == null) {
                throw new RuntimeException("Snapshot not found: " + snapshotId);
            }

            snapshotRestoreService.restoreFromSnapshot(snapshot);
            replayEventsAfterSnapshot(snapshot);

            log.info("[StartupRecovery] Manual recovery complete: snapshotId={}", snapshot.getSnapshotId());
        } catch (Exception e) {
            log.error("[StartupRecovery] Manual recovery failed: snapshotId={}", snapshotId, e);
            throw new RuntimeException("Manual recovery failed", e);
        }
    }
}
