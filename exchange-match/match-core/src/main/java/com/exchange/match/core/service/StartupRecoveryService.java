package com.exchange.match.core.service;

import com.exchange.match.core.model.MatchEngineSnapshot;
import com.exchange.match.core.model.StateChangeEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
// import org.springframework.kafka.annotation.KafkaListener;
// import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;

/**
 * 启动恢复服务
 * 用于系统启动时加载快照并重放事件
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
    
    @Autowired
    private BatchKafkaService batchKafkaService;
    
    // @Autowired
    // private KafkaTemplate<String, String> kafkaTemplate;
    
    @Value("${kafka.topic.state-changes:state-changes}")
    private String stateChangesTopic;
    
    @Value("${kafka.topic.snapshots:snapshots}")
    private String snapshotsTopic;
    
    @Value("${match.recovery.enabled:true}")
    private boolean recoveryEnabled;
    
    @Value("${match.recovery.snapshot-id:}")
    private String recoverySnapshotId;
    
    /**
     * 应用启动完成后执行恢复
     */
    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        if (!recoveryEnabled) {
            log.info("恢复功能已禁用，跳过启动恢复");
            return;
        }
        
        log.info("开始启动恢复流程...");
        
        try {
            // 1. 加载快照
            MatchEngineSnapshot snapshot = loadLatestSnapshot();
            if (snapshot == null) {
                log.info("未找到快照，跳过恢复流程");
                return;
            }
            
            // 2. 从快照恢复内存状态
            snapshotRestoreService.restoreFromSnapshot(snapshot);
            
            // 3. 重放快照之后的事件
            replayEventsAfterSnapshot(snapshot.getLastCommandId());
            
            log.info("启动恢复完成: snapshotId={}, lastCommandId={}", 
                    snapshot.getSnapshotId(), snapshot.getLastCommandId());
            
        } catch (Exception e) {
            log.error("启动恢复失败", e);
            throw new RuntimeException("启动恢复失败", e);
        }
    }
    
    /**
     * 加载最新快照
     */
    private MatchEngineSnapshot loadLatestSnapshot() {
        log.info("加载最新快照");
        
        // 从快照存储服务加载最新快照
        return snapshotStorageService.loadLatestSnapshot();
    }
    
    /**
     * 重放快照之后的事件
     */
    private void replayEventsAfterSnapshot(long lastCommandId) {
        log.info("开始重放事件，从commandId={}开始", lastCommandId + 1);
        
        // 使用事件重放服务重放事件
        eventReplayService.startEventReplay(lastCommandId)
            .thenAccept(success -> {
                if (success) {
                    log.info("事件重放完成: lastCommandId={}", lastCommandId);
                } else {
                    log.error("事件重放失败: lastCommandId={}", lastCommandId);
                }
            })
            .exceptionally(throwable -> {
                log.error("事件重放异常: lastCommandId={}", lastCommandId, throwable);
                return null;
            });
    }
    
    /**
     * Kafka消费者：处理状态变动事件（用于重放）
     */
    // @KafkaListener(topics = "${kafka.topic.state-changes:state-changes}", groupId = "recovery-group")
    public void handleStateChangeEvent(String message) {
        try {
            // TODO: 实现状态变动事件的处理逻辑
            // 这里需要解析StateChangeEvent并重放到内存中
            log.debug("收到状态变动事件: {}", message);
            
        } catch (Exception e) {
            log.error("处理状态变动事件失败", e);
        }
    }
    
    /**
     * 手动触发恢复
     */
    public void triggerRecovery(String snapshotId) {
        log.info("手动触发恢复: snapshotId={}", snapshotId);
        
        try {
            // 1. 加载指定快照
            MatchEngineSnapshot snapshot = loadSnapshotById(snapshotId);
            if (snapshot == null) {
                throw new RuntimeException("未找到快照: " + snapshotId);
            }
            
            // 2. 从快照恢复内存状态
            snapshotRestoreService.restoreFromSnapshot(snapshot);
            
            // 3. 重放快照之后的事件
            replayEventsAfterSnapshot(snapshot.getLastCommandId());
            
            log.info("手动恢复完成: snapshotId={}", snapshotId);
            
        } catch (Exception e) {
            log.error("手动恢复失败: snapshotId={}", snapshotId, e);
            throw new RuntimeException("手动恢复失败", e);
        }
    }
    
    /**
     * 根据ID加载快照
     */
    private MatchEngineSnapshot loadSnapshotById(String snapshotId) {
        log.info("加载快照: snapshotId={}", snapshotId);
        return snapshotStorageService.loadSnapshotById(snapshotId);
    }
} 