package com.exchange.match.core.controller;

import com.exchange.common.response.ApiResponse;
import com.exchange.match.core.model.MatchEngineSnapshot;
import com.exchange.match.core.service.AsyncSnapshotService;
import com.exchange.match.request.EventSnapshotReq;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.concurrent.CompletableFuture;

/**
 * 异步快照监控控制器
 */
@Slf4j
@RestController
@RequestMapping("/api/snapshot/async")
public class AsyncSnapshotController {
    
    @Autowired
    private AsyncSnapshotService asyncSnapshotService;
    
    /**
     * 异步生成快照
     */
    @PostMapping("/generate")
    public ApiResponse<String> generateSnapshotAsync(@RequestBody EventSnapshotReq snapshotReq) {
        try {
            log.info("异步生成快照: symbol={}", snapshotReq != null ? snapshotReq.getSymbol() : "all");
            
            CompletableFuture<MatchEngineSnapshot> future = asyncSnapshotService.generateSnapshotAsync(snapshotReq);
            
            // 异步处理结果
            future.thenAccept(snapshot -> {
                log.info("异步快照生成完成: snapshotId={}", snapshot.getSnapshotId());
            }).exceptionally(throwable -> {
                log.error("异步快照生成失败", throwable);
                return null;
            });
            
            return ApiResponse.success("快照任务已提交，正在异步处理");
            
        } catch (Exception e) {
            log.error("提交异步快照任务失败", e);
            return ApiResponse.error(500, "提交异步快照任务失败: " + e.getMessage());
        }
    }
    
    /**
     * 获取快照队列状态
     */
    @GetMapping("/queue/status")
    public ApiResponse<AsyncSnapshotService.SnapshotQueueStatus> getQueueStatus() {
        try {
            AsyncSnapshotService.SnapshotQueueStatus status = asyncSnapshotService.getQueueStatus();
            return ApiResponse.success(status);
        } catch (Exception e) {
            log.error("获取快照队列状态失败", e);
            return ApiResponse.error(500, "获取快照队列状态失败: " + e.getMessage());
        }
    }
} 