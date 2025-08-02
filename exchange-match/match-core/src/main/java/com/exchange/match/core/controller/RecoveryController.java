package com.exchange.match.core.controller;

import com.exchange.common.response.ApiResponse;
import com.exchange.match.core.service.CommandIdGenerator;
import com.exchange.match.core.service.StartupRecoveryService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * 恢复控制器
 * 提供手动恢复和状态查询接口
 */
@Slf4j
@RestController
@RequestMapping("/api/match/recovery")
public class RecoveryController {
    
    @Autowired
    private StartupRecoveryService startupRecoveryService;
    
    /**
     * 手动触发恢复
     */
    @PostMapping("/trigger")
    public ApiResponse<String> triggerRecovery(@RequestParam String snapshotId) {
        try {
            log.info("手动触发恢复: snapshotId={}", snapshotId);
            
            startupRecoveryService.triggerRecovery(snapshotId);
            
            return ApiResponse.success("恢复触发成功: " + snapshotId);
        } catch (Exception e) {
            log.error("手动触发恢复失败: snapshotId={}", snapshotId, e);
            return ApiResponse.error(500, "恢复触发失败: " + e.getMessage());
        }
    }
    
    /**
     * 获取恢复状态
     */
    @GetMapping("/status")
    public ApiResponse<Map<String, Object>> getRecoveryStatus() {
        try {
            Map<String, Object> status = new HashMap<>();
            
            // 当前命令ID
            status.put("currentCommandId", CommandIdGenerator.getCurrentId());
            
            // 恢复功能状态
            status.put("recoveryEnabled", true);
            status.put("lastRecoveryTime", System.currentTimeMillis());
            
            return ApiResponse.success(status);
        } catch (Exception e) {
            log.error("获取恢复状态失败", e);
            return ApiResponse.error(500, "获取恢复状态失败: " + e.getMessage());
        }
    }
    
    /**
     * 重置命令ID
     */
    @PostMapping("/reset-command-id")
    public ApiResponse<String> resetCommandId(@RequestParam(defaultValue = "0") long commandId) {
        try {
            log.info("重置命令ID: commandId={}", commandId);
            
            CommandIdGenerator.set(commandId);
            
            return ApiResponse.success("命令ID重置成功: " + commandId);
        } catch (Exception e) {
            log.error("重置命令ID失败: commandId={}", commandId, e);
            return ApiResponse.error(500, "重置命令ID失败: " + e.getMessage());
        }
    }
    
    /**
     * 获取命令ID状态
     */
    @GetMapping("/command-id")
    public ApiResponse<Map<String, Object>> getCommandIdStatus() {
        try {
            Map<String, Object> status = new HashMap<>();
            
            status.put("currentCommandId", CommandIdGenerator.getCurrentId());
            status.put("timestamp", System.currentTimeMillis());
            
            return ApiResponse.success(status);
        } catch (Exception e) {
            log.error("获取命令ID状态失败", e);
            return ApiResponse.error(500, "获取命令ID状态失败: " + e.getMessage());
        }
    }
} 