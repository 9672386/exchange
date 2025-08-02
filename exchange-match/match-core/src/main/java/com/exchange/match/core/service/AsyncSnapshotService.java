package com.exchange.match.core.service;

import com.exchange.match.core.event.handler.SnapshotEventHandler;
import com.exchange.match.core.model.MatchEngineSnapshot;
import com.exchange.match.request.EventSnapshotReq;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.DisposableBean;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 异步快照服务
 * 避免快照生成阻塞撮合流程
 */
@Slf4j
@Service
public class AsyncSnapshotService implements InitializingBean, DisposableBean {
    
    @Autowired
    private BatchKafkaService batchKafkaService;
    
    @Autowired
    private SnapshotEventHandler snapshotEventHandler;
    
    /**
     * 快照生成线程池
     */
    private ExecutorService snapshotExecutor;
    
    /**
     * 快照任务队列
     */
    private BlockingQueue<SnapshotTask> snapshotQueue;
    
    /**
     * 快照生成器
     */
    private SnapshotGenerator snapshotGenerator;
    
    /**
     * 是否正在运行
     */
    private final AtomicBoolean isRunning = new AtomicBoolean(false);
    
    /**
     * 快照计数器
     */
    private final AtomicLong snapshotCounter = new AtomicLong(0);
    
    @Override
    public void afterPropertiesSet() {
        // 初始化快照线程池
        snapshotExecutor = Executors.newFixedThreadPool(2, r -> {
            Thread t = new Thread(r, "snapshot-worker-" + snapshotCounter.incrementAndGet());
            t.setDaemon(true);
            return t;
        });
        
        // 初始化快照队列
        snapshotQueue = new LinkedBlockingQueue<>(100);
        
        // 初始化快照生成器
        snapshotGenerator = new SnapshotGenerator();
        
        // 启动快照处理线程
        startSnapshotProcessor();
        
        log.info("异步快照服务初始化完成");
    }
    
    @Override
    public void destroy() {
        isRunning.set(false);
        
        if (snapshotExecutor != null) {
            snapshotExecutor.shutdown();
            try {
                if (!snapshotExecutor.awaitTermination(10, TimeUnit.SECONDS)) {
                    snapshotExecutor.shutdownNow();
                }
            } catch (InterruptedException e) {
                snapshotExecutor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
        
        log.info("异步快照服务已关闭");
    }
    
    /**
     * 异步生成快照
     */
    public CompletableFuture<MatchEngineSnapshot> generateSnapshotAsync(EventSnapshotReq snapshotReq) {
        CompletableFuture<MatchEngineSnapshot> future = new CompletableFuture<>();
        
        SnapshotTask task = new SnapshotTask(snapshotReq, future);
        
        try {
            // 将任务放入队列，不阻塞调用线程
            if (snapshotQueue.offer(task, 1, TimeUnit.SECONDS)) {
                log.debug("快照任务已加入队列: symbol={}", 
                        snapshotReq != null ? snapshotReq.getSymbol() : "all");
            } else {
                log.warn("快照队列已满，任务被拒绝");
                future.completeExceptionally(new RuntimeException("快照队列已满"));
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            future.completeExceptionally(e);
        }
        
        return future;
    }
    
    /**
     * 启动快照处理线程
     */
    private void startSnapshotProcessor() {
        isRunning.set(true);
        
        snapshotExecutor.submit(() -> {
            while (isRunning.get()) {
                try {
                    // 从队列中获取快照任务
                    SnapshotTask task = snapshotQueue.poll(1, TimeUnit.SECONDS);
                    
                    if (task != null) {
                        processSnapshotTask(task);
                    }
                    
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                    log.error("处理快照任务异常", e);
                }
            }
        });
        
        log.info("快照处理线程已启动");
    }
    
    /**
     * 处理快照任务
     */
    private void processSnapshotTask(SnapshotTask task) {
        try {
            log.info("开始处理快照任务: symbol={}", 
                    task.snapshotReq != null ? task.snapshotReq.getSymbol() : "all");
            
            // 生成快照
            MatchEngineSnapshot snapshot = snapshotGenerator.generateSnapshot(task.snapshotReq);
            
            // 推送快照到Kafka
            batchKafkaService.pushSnapshot(snapshot);
            
            // 完成任务
            task.future.complete(snapshot);
            
            log.info("快照任务处理完成: snapshotId={}", snapshot.getSnapshotId());
            
        } catch (Exception e) {
            log.error("处理快照任务失败", e);
            task.future.completeExceptionally(e);
        }
    }
    
    /**
     * 获取快照队列状态
     */
    public SnapshotQueueStatus getQueueStatus() {
        SnapshotQueueStatus status = new SnapshotQueueStatus();
        status.setQueueSize(snapshotQueue.size());
        status.setQueueCapacity(snapshotQueue.remainingCapacity());
        status.setIsRunning(isRunning.get());
        status.setCompletedCount(snapshotCounter.get());
        status.setTimestamp(System.currentTimeMillis());
        return status;
    }
    
    /**
     * 快照任务
     */
    private static class SnapshotTask {
        private final EventSnapshotReq snapshotReq;
        private final CompletableFuture<MatchEngineSnapshot> future;
        
        public SnapshotTask(EventSnapshotReq snapshotReq, CompletableFuture<MatchEngineSnapshot> future) {
            this.snapshotReq = snapshotReq;
            this.future = future;
        }
    }
    
    /**
     * 快照生成器
     */
    private class SnapshotGenerator {
        
        public MatchEngineSnapshot generateSnapshot(EventSnapshotReq snapshotReq) {
            return snapshotEventHandler.generateFullSnapshot(snapshotReq);
        }
    }
    
    /**
     * 快照队列状态
     */
    public static class SnapshotQueueStatus {
        private int queueSize;
        private int queueCapacity;
        private boolean isRunning;
        private long completedCount;
        private long timestamp;
        
        // Getters and Setters
        public int getQueueSize() { return queueSize; }
        public void setQueueSize(int queueSize) { this.queueSize = queueSize; }
        
        public int getQueueCapacity() { return queueCapacity; }
        public void setQueueCapacity(int queueCapacity) { this.queueCapacity = queueCapacity; }
        
        public boolean isRunning() { return isRunning; }
        public void setIsRunning(boolean isRunning) { this.isRunning = isRunning; }
        
        public long getCompletedCount() { return completedCount; }
        public void setCompletedCount(long completedCount) { this.completedCount = completedCount; }
        
        public long getTimestamp() { return timestamp; }
        public void setTimestamp(long timestamp) { this.timestamp = timestamp; }
    }
} 