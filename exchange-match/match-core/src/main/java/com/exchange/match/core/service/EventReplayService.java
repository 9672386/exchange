package com.exchange.match.core.service;

import com.exchange.match.core.event.service.EventPublishService;
import com.exchange.match.core.model.StateChangeEvent;
import com.exchange.match.enums.EventType;
import com.exchange.match.request.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
// import org.springframework.kafka.annotation.KafkaListener;
// import org.springframework.kafka.core.KafkaTemplate;
// import org.springframework.kafka.support.KafkaHeaders;
// import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 事件重放服务
 * 实现从Kafka重放状态变动事件的具体逻辑
 */
@Slf4j
@Service
public class EventReplayService {
    
    @Autowired
    private EventPublishService eventPublishService;
    
    @Autowired
    private ObjectMapper objectMapper;
    
    @Value("${kafka.topic.state-changes:state-changes}")
    private String stateChangesTopic;
    
    @Value("${match.replay.enabled:true}")
    private boolean replayEnabled;
    
    @Value("${match.replay.batch-size:100}")
    private int replayBatchSize;
    
    @Value("${match.replay.timeout-ms:30000}")
    private long replayTimeoutMs;
    
    // 重放状态跟踪
    private final AtomicLong expectedCommandId = new AtomicLong(0);
    private final AtomicLong lastReplayedCommandId = new AtomicLong(0);
    private final ConcurrentHashMap<Long, StateChangeEvent> pendingEvents = new ConcurrentHashMap<>();
    private volatile boolean isReplaying = false;
    
    /**
     * 开始事件重放
     */
    public CompletableFuture<Boolean> startEventReplay(long startCommandId) {
        if (!replayEnabled) {
            log.info("事件重放功能已禁用");
            return CompletableFuture.completedFuture(false);
        }
        
        log.info("开始事件重放: startCommandId={}", startCommandId);
        
        return CompletableFuture.supplyAsync(() -> {
            try {
                isReplaying = true;
                expectedCommandId.set(startCommandId + 1);
                lastReplayedCommandId.set(startCommandId);
                
                // 等待重放完成
                long startTime = System.currentTimeMillis();
                while (isReplaying && (System.currentTimeMillis() - startTime) < replayTimeoutMs) {
                    Thread.sleep(100);
                }
                
                if (isReplaying) {
                    log.warn("事件重放超时");
                    isReplaying = false;
                    return false;
                }
                
                log.info("事件重放完成: lastReplayedCommandId={}", lastReplayedCommandId.get());
                return true;
                
            } catch (Exception e) {
                log.error("事件重放失败", e);
                isReplaying = false;
                return false;
            }
        });
    }
    
    /**
     * 停止事件重放
     */
    public void stopEventReplay() {
        log.info("停止事件重放");
        isReplaying = false;
    }
    
    /**
     * Kafka消费者：处理状态变动事件（用于重放）
     */
    // @KafkaListener(topics = "${kafka.topic.state-changes:state-changes}", groupId = "replay-group")
    public void handleStateChangeEvent(String message, String topic) {
        try {
            if (!isReplaying) {
                log.debug("重放未开始，忽略事件: {}", message);
                return;
            }
            
            // 解析状态变动事件
            StateChangeEvent stateChangeEvent = objectMapper.readValue(message, StateChangeEvent.class);
            
            // 检查命令ID顺序
            long commandId = stateChangeEvent.getCommandId();
            long expectedId = expectedCommandId.get();
            
            if (commandId < expectedId) {
                log.debug("跳过过期事件: commandId={}, expectedId={}", commandId, expectedId);
                return;
            }
            
            if (commandId > expectedId) {
                log.debug("缓存未来事件: commandId={}, expectedId={}", commandId, expectedId);
                pendingEvents.put(commandId, stateChangeEvent);
                return;
            }
            
            // 重放当前事件
            replayEvent(stateChangeEvent);
            
            // 处理缓存的事件
            processPendingEvents();
            
        } catch (Exception e) {
            log.error("处理状态变动事件失败: {}", message, e);
        }
    }
    
    /**
     * 重放单个事件
     */
    private void replayEvent(StateChangeEvent stateChangeEvent) {
        try {
            long commandId = stateChangeEvent.getCommandId();
            EventType eventType = stateChangeEvent.getEventType();
            
            log.debug("重放事件: commandId={}, eventType={}", commandId, eventType);
            
            // 根据事件类型重放
            switch (eventType) {
                case NEW_ORDER:
                    replayNewOrderEvent(stateChangeEvent);
                    break;
                case CANAL:
                    replayCancelOrderEvent(stateChangeEvent);
                    break;
                case CLEAR:
                    replayClearEvent(stateChangeEvent);
                    break;
                case LIQUIDATION:
                    replayLiquidationEvent(stateChangeEvent);
                    break;
                default:
                    log.warn("不支持重放的事件类型: {}", eventType);
            }
            
            // 更新重放状态
            lastReplayedCommandId.set(commandId);
            expectedCommandId.set(commandId + 1);
            
        } catch (Exception e) {
            log.error("重放事件失败: commandId={}, eventType={}", 
                    stateChangeEvent.getCommandId(), stateChangeEvent.getEventType(), e);
        }
    }
    
    /**
     * 重放新订单事件
     */
    private void replayNewOrderEvent(StateChangeEvent stateChangeEvent) {
        try {
            EventNewOrderReq originalRequest = objectMapper.convertValue(
                stateChangeEvent.getOriginalRequest(), EventNewOrderReq.class);
            
            // 发布新订单事件到Disruptor
            eventPublishService.publishNewOrderEvent(originalRequest);
            
            log.debug("重放新订单事件成功: commandId={}, orderId={}", 
                    stateChangeEvent.getCommandId(), originalRequest.getOrderId());
            
        } catch (Exception e) {
            log.error("重放新订单事件失败", e);
        }
    }
    
    /**
     * 重放撤单事件
     */
    private void replayCancelOrderEvent(StateChangeEvent stateChangeEvent) {
        try {
            EventCanalReq originalRequest = objectMapper.convertValue(
                stateChangeEvent.getOriginalRequest(), EventCanalReq.class);
            
            // 发布撤单事件到Disruptor
            eventPublishService.publishCanalEvent(originalRequest);
            
            log.debug("重放撤单事件成功: commandId={}, orderId={}", 
                    stateChangeEvent.getCommandId(), originalRequest.getOrderId());
            
        } catch (Exception e) {
            log.error("重放撤单事件失败", e);
        }
    }
    
    /**
     * 重放清理事件
     */
    private void replayClearEvent(StateChangeEvent stateChangeEvent) {
        try {
            EventClearReq originalRequest = objectMapper.convertValue(
                stateChangeEvent.getOriginalRequest(), EventClearReq.class);
            
            // 发布清理事件到Disruptor
            eventPublishService.publishClearEvent(originalRequest);
            
            log.debug("重放清理事件成功: commandId={}, symbol={}", 
                    stateChangeEvent.getCommandId(), originalRequest.getSymbol());
            
        } catch (Exception e) {
            log.error("重放清理事件失败", e);
        }
    }
    
    /**
     * 重放强平事件
     */
    private void replayLiquidationEvent(StateChangeEvent stateChangeEvent) {
        try {
            EventLiquidationReq originalRequest = objectMapper.convertValue(
                stateChangeEvent.getOriginalRequest(), EventLiquidationReq.class);
            
            // 发布强平事件到Disruptor
            eventPublishService.publishLiquidationEvent(originalRequest);
            
            log.debug("重放强平事件成功: commandId={}, liquidationId={}", 
                    stateChangeEvent.getCommandId(), originalRequest.getLiquidationId());
            
        } catch (Exception e) {
            log.error("重放强平事件失败", e);
        }
    }
    
    /**
     * 处理缓存的事件
     */
    private void processPendingEvents() {
        long expectedId = expectedCommandId.get();
        
        while (true) {
            StateChangeEvent event = pendingEvents.remove(expectedId);
            if (event == null) {
                break;
            }
            
            replayEvent(event);
            expectedId = expectedCommandId.get();
        }
    }
    
    /**
     * 获取重放状态
     */
    public ReplayStatus getReplayStatus() {
        ReplayStatus status = new ReplayStatus();
        status.setReplaying(isReplaying);
        status.setExpectedCommandId(expectedCommandId.get());
        status.setLastReplayedCommandId(lastReplayedCommandId.get());
        status.setPendingEventCount(pendingEvents.size());
        return status;
    }
    
    /**
     * 重放状态
     */
    public static class ReplayStatus {
        private boolean replaying;
        private long expectedCommandId;
        private long lastReplayedCommandId;
        private int pendingEventCount;
        
        // Getters and Setters
        public boolean isReplaying() { return replaying; }
        public void setReplaying(boolean replaying) { this.replaying = replaying; }
        
        public long getExpectedCommandId() { return expectedCommandId; }
        public void setExpectedCommandId(long expectedCommandId) { this.expectedCommandId = expectedCommandId; }
        
        public long getLastReplayedCommandId() { return lastReplayedCommandId; }
        public void setLastReplayedCommandId(long lastReplayedCommandId) { this.lastReplayedCommandId = lastReplayedCommandId; }
        
        public int getPendingEventCount() { return pendingEventCount; }
        public void setPendingEventCount(int pendingEventCount) { this.pendingEventCount = pendingEventCount; }
    }
} 