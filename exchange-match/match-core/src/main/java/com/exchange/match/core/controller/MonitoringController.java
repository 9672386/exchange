package com.exchange.match.core.controller;

import com.exchange.common.response.ApiResponse;
import com.exchange.match.core.service.MatchMonitoringService;
import com.exchange.match.core.service.SnapshotStorageService;
import com.exchange.match.core.service.EventReplayService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * 监控控制器
 * 提供监控状态查询和错误处理接口
 */
@Slf4j
@RestController
@RequestMapping("/api/match/monitoring")
public class MonitoringController {
    
    @Autowired
    private MatchMonitoringService matchMonitoringService;
    
    @Autowired
    private SnapshotStorageService snapshotStorageService;
    
    @Autowired
    private EventReplayService eventReplayService;
    
    /**
     * 获取监控状态
     */
    @GetMapping("/status")
    public ApiResponse<Map<String, Object>> getMonitoringStatus() {
        try {
            Map<String, Object> status = new HashMap<>();
            
            // 基本监控状态
            MatchMonitoringService.MonitoringStatus monitoringStatus = matchMonitoringService.getMonitoringStatus();
            status.put("monitoring", monitoringStatus);
            
            // 快照文件列表
            status.put("snapshotFiles", snapshotStorageService.listSnapshotFiles());
            
            // 重放状态
            status.put("replayStatus", eventReplayService.getReplayStatus());
            
            return ApiResponse.success(status);
        } catch (Exception e) {
            log.error("获取监控状态失败", e);
            return ApiResponse.error(500, "获取监控状态失败: " + e.getMessage());
        }
    }
    
    /**
     * 重置监控指标
     */
    @PostMapping("/reset")
    public ApiResponse<String> resetMonitoringMetrics() {
        try {
            matchMonitoringService.resetMonitoringMetrics();
            return ApiResponse.success("监控指标重置成功");
        } catch (Exception e) {
            log.error("重置监控指标失败", e);
            return ApiResponse.error(500, "重置监控指标失败: " + e.getMessage());
        }
    }
    
    /**
     * 获取错误统计
     */
    @GetMapping("/errors")
    public ApiResponse<Map<String, Object>> getErrorStatistics() {
        try {
            Map<String, Object> errors = new HashMap<>();
            
            MatchMonitoringService.MonitoringStatus status = matchMonitoringService.getMonitoringStatus();
            errors.put("errorCounts", status.getErrorCounts());
            errors.put("lastErrorTimes", status.getLastErrorTimes());
            
            return ApiResponse.success(errors);
        } catch (Exception e) {
            log.error("获取错误统计失败", e);
            return ApiResponse.error(500, "获取错误统计失败: " + e.getMessage());
        }
    }
    
    /**
     * 获取性能指标
     */
    @GetMapping("/performance")
    public ApiResponse<Map<String, Object>> getPerformanceMetrics() {
        try {
            Map<String, Object> performance = new HashMap<>();
            
            MatchMonitoringService.MonitoringStatus status = matchMonitoringService.getMonitoringStatus();
            performance.put("totalEvents", status.getTotalEvents());
            performance.put("successRate", status.getSuccessRate());
            performance.put("averageProcessingTime", status.getAverageProcessingTime());
            performance.put("maxProcessingTime", status.getMaxProcessingTime());
            performance.put("minProcessingTime", status.getMinProcessingTime());
            
            return ApiResponse.success(performance);
        } catch (Exception e) {
            log.error("获取性能指标失败", e);
            return ApiResponse.error(500, "获取性能指标失败: " + e.getMessage());
        }
    }
    
    /**
     * 获取快照统计
     */
    @GetMapping("/snapshots")
    public ApiResponse<Map<String, Object>> getSnapshotStatistics() {
        try {
            Map<String, Object> snapshots = new HashMap<>();
            
            MatchMonitoringService.MonitoringStatus status = matchMonitoringService.getMonitoringStatus();
            snapshots.put("snapshotCount", status.getSnapshotCount());
            snapshots.put("snapshotFiles", snapshotStorageService.listSnapshotFiles());
            
            return ApiResponse.success(snapshots);
        } catch (Exception e) {
            log.error("获取快照统计失败", e);
            return ApiResponse.error(500, "获取快照统计失败: " + e.getMessage());
        }
    }
    
    /**
     * 获取重放统计
     */
    @GetMapping("/replay")
    public ApiResponse<Map<String, Object>> getReplayStatistics() {
        try {
            Map<String, Object> replay = new HashMap<>();
            
            MatchMonitoringService.MonitoringStatus status = matchMonitoringService.getMonitoringStatus();
            replay.put("replayCount", status.getReplayCount());
            replay.put("replayStatus", eventReplayService.getReplayStatus());
            
            return ApiResponse.success(replay);
        } catch (Exception e) {
            log.error("获取重放统计失败", e);
            return ApiResponse.error(500, "获取重放统计失败: " + e.getMessage());
        }
    }
    
    /**
     * 记录自定义错误
     */
    @PostMapping("/error")
    public ApiResponse<String> recordError(@RequestParam String errorType, @RequestParam String errorMessage) {
        try {
            matchMonitoringService.recordError(errorType, errorMessage);
            return ApiResponse.success("错误记录成功");
        } catch (Exception e) {
            log.error("记录错误失败", e);
            return ApiResponse.error(500, "记录错误失败: " + e.getMessage());
        }
    }
    
    /**
     * 健康检查
     */
    @GetMapping("/health")
    public ApiResponse<Map<String, Object>> healthCheck() {
        try {
            Map<String, Object> health = new HashMap<>();
            
            MatchMonitoringService.MonitoringStatus status = matchMonitoringService.getMonitoringStatus();
            
            // 基本健康状态
            health.put("status", "UP");
            health.put("timestamp", System.currentTimeMillis());
            health.put("totalEvents", status.getTotalEvents());
            health.put("successRate", status.getSuccessRate());
            health.put("currentCommandId", status.getCurrentCommandId());
            
            // 错误检查
            boolean hasErrors = !status.getErrorCounts().isEmpty();
            health.put("hasErrors", hasErrors);
            
            if (hasErrors) {
                health.put("errorCount", status.getErrorCounts().size());
            }
            
            return ApiResponse.success(health);
        } catch (Exception e) {
            log.error("健康检查失败", e);
            return ApiResponse.error(500, "健康检查失败: " + e.getMessage());
        }
    }
} 