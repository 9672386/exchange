package com.exchange.match.core.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 撮合监控服务
 * 提供完善的错误处理和监控功能
 */
@Slf4j
@Service
public class MatchMonitoringService {
    
    @Autowired
    private CommandIdGenerator commandIdGenerator;
    
    // 监控指标
    private final AtomicLong totalEvents = new AtomicLong(0);
    private final AtomicLong successEvents = new AtomicLong(0);
    private final AtomicLong failedEvents = new AtomicLong(0);
    private final AtomicLong stateChangeEvents = new AtomicLong(0);
    private final AtomicLong snapshotCount = new AtomicLong(0);
    private final AtomicLong replayCount = new AtomicLong(0);
    
    // 错误统计
    private final ConcurrentHashMap<String, AtomicInteger> errorCounts = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Long> lastErrorTimes = new ConcurrentHashMap<>();
    
    // 性能指标
    private final AtomicLong totalProcessingTime = new AtomicLong(0);
    private final AtomicLong maxProcessingTime = new AtomicLong(0);
    private final AtomicLong minProcessingTime = new AtomicLong(Long.MAX_VALUE);
    
    /**
     * 记录事件处理
     */
    public void recordEventProcessed(boolean success, long processingTimeMs) {
        totalEvents.incrementAndGet();
        
        if (success) {
            successEvents.incrementAndGet();
        } else {
            failedEvents.incrementAndGet();
        }
        
        // 更新性能指标
        totalProcessingTime.addAndGet(processingTimeMs);
        updateMaxProcessingTime(processingTimeMs);
        updateMinProcessingTime(processingTimeMs);
    }
    
    /**
     * 记录状态变动事件
     */
    public void recordStateChangeEvent() {
        stateChangeEvents.incrementAndGet();
    }
    
    /**
     * 记录快照生成
     */
    public void recordSnapshotGenerated() {
        snapshotCount.incrementAndGet();
    }
    
    /**
     * 记录事件重放
     */
    public void recordEventReplay() {
        replayCount.incrementAndGet();
    }
    
    /**
     * 记录错误
     */
    public void recordError(String errorType, String errorMessage) {
        errorCounts.computeIfAbsent(errorType, k -> new AtomicInteger(0)).incrementAndGet();
        lastErrorTimes.put(errorType, System.currentTimeMillis());
        
        log.error("监控记录错误: type={}, message={}", errorType, errorMessage);
    }
    
    /**
     * 获取监控状态
     */
    public MonitoringStatus getMonitoringStatus() {
        MonitoringStatus status = new MonitoringStatus();
        
        // 基本指标
        status.setTotalEvents(totalEvents.get());
        status.setSuccessEvents(successEvents.get());
        status.setFailedEvents(failedEvents.get());
        status.setStateChangeEvents(stateChangeEvents.get());
        status.setSnapshotCount(snapshotCount.get());
        status.setReplayCount(replayCount.get());
        
        // 成功率
        long total = totalEvents.get();
        if (total > 0) {
            status.setSuccessRate((double) successEvents.get() / total * 100);
        }
        
        // 性能指标
        long totalTime = totalProcessingTime.get();
        if (total > 0) {
            status.setAverageProcessingTime((double) totalTime / total);
        }
        status.setMaxProcessingTime(maxProcessingTime.get());
        status.setMinProcessingTime(minProcessingTime.get() == Long.MAX_VALUE ? 0 : minProcessingTime.get());
        
        // 命令ID
        status.setCurrentCommandId(commandIdGenerator.getCurrentId());
        
        // 错误统计
        status.setErrorCounts(new ConcurrentHashMap<>(errorCounts));
        status.setLastErrorTimes(new ConcurrentHashMap<>(lastErrorTimes));
        
        return status;
    }
    
    /**
     * 重置监控指标
     */
    public void resetMonitoringMetrics() {
        totalEvents.set(0);
        successEvents.set(0);
        failedEvents.set(0);
        stateChangeEvents.set(0);
        snapshotCount.set(0);
        replayCount.set(0);
        totalProcessingTime.set(0);
        maxProcessingTime.set(0);
        minProcessingTime.set(Long.MAX_VALUE);
        errorCounts.clear();
        lastErrorTimes.clear();
        
        log.info("监控指标已重置");
    }
    
    /**
     * 更新最大处理时间
     */
    private void updateMaxProcessingTime(long processingTimeMs) {
        long currentMax = maxProcessingTime.get();
        while (processingTimeMs > currentMax) {
            if (maxProcessingTime.compareAndSet(currentMax, processingTimeMs)) {
                break;
            }
            currentMax = maxProcessingTime.get();
        }
    }
    
    /**
     * 更新最小处理时间
     */
    private void updateMinProcessingTime(long processingTimeMs) {
        long currentMin = minProcessingTime.get();
        while (processingTimeMs < currentMin) {
            if (minProcessingTime.compareAndSet(currentMin, processingTimeMs)) {
                break;
            }
            currentMin = minProcessingTime.get();
        }
    }
    
    /**
     * 监控状态
     */
    public static class MonitoringStatus {
        private long totalEvents;
        private long successEvents;
        private long failedEvents;
        private long stateChangeEvents;
        private long snapshotCount;
        private long replayCount;
        private double successRate;
        private double averageProcessingTime;
        private long maxProcessingTime;
        private long minProcessingTime;
        private long currentCommandId;
        private ConcurrentHashMap<String, AtomicInteger> errorCounts;
        private ConcurrentHashMap<String, Long> lastErrorTimes;
        
        // Getters and Setters
        public long getTotalEvents() { return totalEvents; }
        public void setTotalEvents(long totalEvents) { this.totalEvents = totalEvents; }
        
        public long getSuccessEvents() { return successEvents; }
        public void setSuccessEvents(long successEvents) { this.successEvents = successEvents; }
        
        public long getFailedEvents() { return failedEvents; }
        public void setFailedEvents(long failedEvents) { this.failedEvents = failedEvents; }
        
        public long getStateChangeEvents() { return stateChangeEvents; }
        public void setStateChangeEvents(long stateChangeEvents) { this.stateChangeEvents = stateChangeEvents; }
        
        public long getSnapshotCount() { return snapshotCount; }
        public void setSnapshotCount(long snapshotCount) { this.snapshotCount = snapshotCount; }
        
        public long getReplayCount() { return replayCount; }
        public void setReplayCount(long replayCount) { this.replayCount = replayCount; }
        
        public double getSuccessRate() { return successRate; }
        public void setSuccessRate(double successRate) { this.successRate = successRate; }
        
        public double getAverageProcessingTime() { return averageProcessingTime; }
        public void setAverageProcessingTime(double averageProcessingTime) { this.averageProcessingTime = averageProcessingTime; }
        
        public long getMaxProcessingTime() { return maxProcessingTime; }
        public void setMaxProcessingTime(long maxProcessingTime) { this.maxProcessingTime = maxProcessingTime; }
        
        public long getMinProcessingTime() { return minProcessingTime; }
        public void setMinProcessingTime(long minProcessingTime) { this.minProcessingTime = minProcessingTime; }
        
        public long getCurrentCommandId() { return currentCommandId; }
        public void setCurrentCommandId(long currentCommandId) { this.currentCommandId = currentCommandId; }
        
        public ConcurrentHashMap<String, AtomicInteger> getErrorCounts() { return errorCounts; }
        public void setErrorCounts(ConcurrentHashMap<String, AtomicInteger> errorCounts) { this.errorCounts = errorCounts; }
        
        public ConcurrentHashMap<String, Long> getLastErrorTimes() { return lastErrorTimes; }
        public void setLastErrorTimes(ConcurrentHashMap<String, Long> lastErrorTimes) { this.lastErrorTimes = lastErrorTimes; }
    }
} 