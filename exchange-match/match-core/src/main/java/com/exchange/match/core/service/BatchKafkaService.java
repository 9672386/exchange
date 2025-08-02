package com.exchange.match.core.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.DisposableBean;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.Map;

/**
 * 批量Kafka推送服务
 * 支持1000条或50ms的批量推送条件
 */
@Slf4j
@Service
public class BatchKafkaService implements InitializingBean, DisposableBean {
    
    @Autowired
    private KafkaProducer<String, String> kafkaProducer;
    
    @Autowired
    private KafkaOffsetManager offsetManager;
    
    @Value("${kafka.topic.match-results:match-results}")
    private String matchResultsTopic;
    
    @Value("${kafka.topic.snapshots:snapshots}")
    private String snapshotsTopic;
    
    @Value("${kafka.topic.state-changes:state-changes}")
    private String stateChangesTopic;
    
    @Value("${kafka.batch.size:1000}")
    private int batchSize;
    
    @Value("${kafka.batch.timeout-ms:50}")
    private long batchTimeoutMs;
    
    private final List<String> pendingMessages = new ArrayList<>();
    private final Object pendingLock = new Object();
    private final AtomicInteger messageCounter = new AtomicInteger(0);
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    private ScheduledExecutorService scheduler;
    private long lastFlushTime;
    
    @Override
    public void afterPropertiesSet() {
        lastFlushTime = System.currentTimeMillis();
        scheduler = Executors.newSingleThreadScheduledExecutor();
        
        // 启动定时任务，每50ms检查一次是否需要推送
        scheduler.scheduleAtFixedRate(this::checkAndFlush, 
                batchTimeoutMs, batchTimeoutMs, TimeUnit.MILLISECONDS);
        
        log.info("批量Kafka推送服务初始化完成，批量大小: {}, 超时时间: {}ms", batchSize, batchTimeoutMs);
    }
    
    @Override
    public void destroy() {
        if (scheduler != null) {
            scheduler.shutdown();
            try {
                if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                    scheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                scheduler.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
        
        // 关闭前推送剩余消息
        flushPendingMessages();
        
        if (kafkaProducer != null) {
            kafkaProducer.close();
        }
        
        log.info("批量Kafka推送服务已关闭");
    }
    
    /**
     * 推送撮合结果
     */
    public void pushMatchResult(Object result) {
        try {
            String message = objectMapper.writeValueAsString(result);
            addMessage(matchResultsTopic, message);
        } catch (JsonProcessingException e) {
            log.error("序列化撮合结果失败", e);
        }
    }
    
    /**
     * 推送快照数据
     */
    public void pushSnapshot(Object snapshot) {
        try {
            String message = objectMapper.writeValueAsString(snapshot);
            addMessage(snapshotsTopic, message);
        } catch (JsonProcessingException e) {
            log.error("序列化快照数据失败", e);
        }
    }
    
    /**
     * 推送状态变动事件
     */
    public void pushStateChangeEvent(Object stateChangeEvent) {
        try {
            String message = objectMapper.writeValueAsString(stateChangeEvent);
            addMessage(stateChangesTopic, message);
        } catch (JsonProcessingException e) {
            log.error("序列化状态变动事件失败", e);
        }
    }
    
    /**
     * 添加消息到待推送队列
     */
    private void addMessage(String topic, String message) {
        synchronized (pendingLock) {
            pendingMessages.add(message);
            int currentCount = pendingMessages.size();
            
            // 增加offset
            long currentOffset = offsetManager.incrementOffset(topic);
            
            log.debug("添加消息到队列，当前数量: {}, 主题: {}, offset: {}", currentCount, topic, currentOffset);
            
            // 检查是否达到批量推送条件
            if (currentCount >= batchSize) {
                flushPendingMessages();
            }
        }
    }
    
    /**
     * 检查并推送消息
     */
    private void checkAndFlush() {
        long currentTime = System.currentTimeMillis();
        long timeSinceLastFlush = currentTime - lastFlushTime;
        
        synchronized (pendingLock) {
            if (!pendingMessages.isEmpty() && timeSinceLastFlush >= batchTimeoutMs) {
                flushPendingMessages();
            }
        }
    }
    
    /**
     * 推送待发送的消息
     */
    private void flushPendingMessages() {
        List<String> messagesToSend;
        
        synchronized (pendingLock) {
            if (pendingMessages.isEmpty()) {
                return;
            }
            
            messagesToSend = new ArrayList<>(pendingMessages);
            pendingMessages.clear();
            lastFlushTime = System.currentTimeMillis();
        }
        
        // 推送消息到Kafka
        for (String message : messagesToSend) {
            try {
                // 根据消息内容判断主题
                String topic = determineTopic(message);
                ProducerRecord<String, String> record = new ProducerRecord<>(topic, message);
                
                kafkaProducer.send(record, (metadata, exception) -> {
                    if (exception != null) {
                        log.error("推送消息到Kafka失败: {}", exception.getMessage(), exception);
                    } else {
                        // 消息发送成功，确认offset
                        long currentOffset = offsetManager.getCurrentOffset(topic);
                        offsetManager.commitOffset(topic, currentOffset);
                        
                        log.debug("消息推送成功: topic={}, partition={}, offset={}", 
                                metadata.topic(), metadata.partition(), metadata.offset());
                    }
                });
                
                messageCounter.incrementAndGet();
                
            } catch (Exception e) {
                log.error("推送消息到Kafka异常", e);
            }
        }
        
        log.info("批量推送消息完成，数量: {}, 累计推送: {}", 
                messagesToSend.size(), messageCounter.get());
    }
    
    /**
     * 根据消息内容判断推送主题
     */
    private String determineTopic(String message) {
        if (message.contains("snapshot") || message.contains("Snapshot")) {
            return snapshotsTopic;
        } else if (message.contains("commandId") || message.contains("StateChangeEvent")) {
            return stateChangesTopic;
        } else {
            return matchResultsTopic;
        }
    }
    
    /**
     * 强制推送所有待发送消息
     */
    public void forceFlush() {
        flushPendingMessages();
    }
    
    /**
     * 获取当前待推送消息数量
     */
    public int getPendingMessageCount() {
        synchronized (pendingLock) {
            return pendingMessages.size();
        }
    }
    
    /**
     * 获取累计推送消息数量
     */
    public int getTotalPushedCount() {
        return messageCounter.get();
    }
    
    /**
     * 获取offset状态
     */
    public Map<String, KafkaOffsetManager.OffsetStatus> getOffsetStatus() {
        return offsetManager.getAllOffsetStatus();
    }
    
    /**
     * 获取offset一致性状态
     */
    public Map<String, Boolean> getOffsetConsistency() {
        return offsetManager.getAllOffsetConsistency();
    }
    
    /**
     * 获取待确认消息数量
     */
    public Map<String, Long> getPendingMessageCounts() {
        return offsetManager.getAllPendingMessageCounts();
    }
    
    /**
     * 重置主题的offset
     */
    public void resetOffset(String topic) {
        offsetManager.resetOffset(topic);
        log.info("重置主题{}的offset", topic);
    }
} 