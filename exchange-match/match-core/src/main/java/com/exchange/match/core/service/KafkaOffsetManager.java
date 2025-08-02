package com.exchange.match.core.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Kafka Offset管理器
 * 管理各个主题的offset，确保数据一致性
 */
@Slf4j
@Service
public class KafkaOffsetManager {
    
    @Value("${kafka.topic.match-results:match-results}")
    private String matchResultsTopic;
    
    @Value("${kafka.topic.snapshots:snapshots}")
    private String snapshotsTopic;
    
    /**
     * 各主题的offset映射
     */
    private final Map<String, AtomicLong> topicOffsets = new ConcurrentHashMap<>();
    
    /**
     * 各主题的已确认offset映射
     */
    private final Map<String, AtomicLong> committedOffsets = new ConcurrentHashMap<>();
    
    /**
     * 初始化offset管理器
     */
    public KafkaOffsetManager() {
        // 初始化各主题的offset
        topicOffsets.put(matchResultsTopic, new AtomicLong(0));
        topicOffsets.put(snapshotsTopic, new AtomicLong(0));
        committedOffsets.put(matchResultsTopic, new AtomicLong(0));
        committedOffsets.put(snapshotsTopic, new AtomicLong(0));
        
        log.info("Kafka Offset管理器初始化完成");
    }
    
    /**
     * 获取主题的当前offset
     */
    public long getCurrentOffset(String topic) {
        AtomicLong offset = topicOffsets.get(topic);
        return offset != null ? offset.get() : 0;
    }
    
    /**
     * 获取主题的已确认offset
     */
    public long getCommittedOffset(String topic) {
        AtomicLong offset = committedOffsets.get(topic);
        return offset != null ? offset.get() : 0;
    }
    
    /**
     * 增加主题的offset
     */
    public long incrementOffset(String topic) {
        AtomicLong offset = topicOffsets.get(topic);
        if (offset != null) {
            return offset.incrementAndGet();
        }
        return 0;
    }
    
    /**
     * 设置主题的offset
     */
    public void setOffset(String topic, long offset) {
        AtomicLong currentOffset = topicOffsets.get(topic);
        if (currentOffset != null) {
            currentOffset.set(offset);
            log.debug("设置主题{}的offset为: {}", topic, offset);
        }
    }
    
    /**
     * 确认主题的offset
     */
    public void commitOffset(String topic, long offset) {
        AtomicLong committedOffset = committedOffsets.get(topic);
        if (committedOffset != null) {
            committedOffset.set(offset);
            log.debug("确认主题{}的offset为: {}", topic, offset);
        }
    }
    
    /**
     * 获取所有主题的offset状态
     */
    public Map<String, OffsetStatus> getAllOffsetStatus() {
        Map<String, OffsetStatus> status = new ConcurrentHashMap<>();
        
        for (String topic : topicOffsets.keySet()) {
            OffsetStatus offsetStatus = new OffsetStatus();
            offsetStatus.setTopic(topic);
            offsetStatus.setCurrentOffset(getCurrentOffset(topic));
            offsetStatus.setCommittedOffset(getCommittedOffset(topic));
            offsetStatus.setPendingOffset(offsetStatus.getCurrentOffset() - offsetStatus.getCommittedOffset());
            offsetStatus.setTimestamp(System.currentTimeMillis());
            
            status.put(topic, offsetStatus);
        }
        
        return status;
    }
    
    /**
     * 重置主题的offset
     */
    public void resetOffset(String topic) {
        AtomicLong currentOffset = topicOffsets.get(topic);
        AtomicLong committedOffset = committedOffsets.get(topic);
        
        if (currentOffset != null) {
            currentOffset.set(0);
        }
        if (committedOffset != null) {
            committedOffset.set(0);
        }
        
        log.info("重置主题{}的offset", topic);
    }
    
    /**
     * 检查offset是否一致
     */
    public boolean isOffsetConsistent(String topic) {
        long currentOffset = getCurrentOffset(topic);
        long committedOffset = getCommittedOffset(topic);
        
        return currentOffset == committedOffset;
    }
    
    /**
     * 获取所有主题的offset一致性状态
     */
    public Map<String, Boolean> getAllOffsetConsistency() {
        Map<String, Boolean> consistency = new ConcurrentHashMap<>();
        
        for (String topic : topicOffsets.keySet()) {
            consistency.put(topic, isOffsetConsistent(topic));
        }
        
        return consistency;
    }
    
    /**
     * 获取待确认的消息数量
     */
    public long getPendingMessageCount(String topic) {
        return getCurrentOffset(topic) - getCommittedOffset(topic);
    }
    
    /**
     * 获取所有主题的待确认消息数量
     */
    public Map<String, Long> getAllPendingMessageCounts() {
        Map<String, Long> pendingCounts = new ConcurrentHashMap<>();
        
        for (String topic : topicOffsets.keySet()) {
            pendingCounts.put(topic, getPendingMessageCount(topic));
        }
        
        return pendingCounts;
    }
    
    /**
     * Offset状态类
     */
    public static class OffsetStatus {
        private String topic;
        private long currentOffset;
        private long committedOffset;
        private long pendingOffset;
        private long timestamp;
        
        // Getters and Setters
        public String getTopic() { return topic; }
        public void setTopic(String topic) { this.topic = topic; }
        
        public long getCurrentOffset() { return currentOffset; }
        public void setCurrentOffset(long currentOffset) { this.currentOffset = currentOffset; }
        
        public long getCommittedOffset() { return committedOffset; }
        public void setCommittedOffset(long committedOffset) { this.committedOffset = committedOffset; }
        
        public long getPendingOffset() { return pendingOffset; }
        public void setPendingOffset(long pendingOffset) { this.pendingOffset = pendingOffset; }
        
        public long getTimestamp() { return timestamp; }
        public void setTimestamp(long timestamp) { this.timestamp = timestamp; }
    }
} 