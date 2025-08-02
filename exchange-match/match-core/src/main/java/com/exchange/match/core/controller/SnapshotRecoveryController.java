package com.exchange.match.core.controller;

import com.exchange.common.response.ApiResponse;
import com.exchange.match.core.model.MatchEngineSnapshot;
import com.exchange.match.core.service.SnapshotRecoveryService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

/**
 * 快照恢复控制器
 */
@Slf4j
@RestController
@RequestMapping("/api/snapshot/recovery")
public class SnapshotRecoveryController {
    
    @Autowired
    private SnapshotRecoveryService snapshotRecoveryService;
    
    /**
     * 从快照恢复撮合引擎状态
     */
    @PostMapping("/recover")
    public ApiResponse<String> recoverFromSnapshot(@RequestBody MatchEngineSnapshot snapshot) {
        try {
            log.info("开始从快照恢复撮合引擎状态: snapshotId={}", snapshot.getSnapshotId());
            
            // 验证快照数据完整性
            if (!snapshotRecoveryService.validateSnapshot(snapshot)) {
                return ApiResponse.error(400, "快照数据不完整，无法恢复");
            }
            
            // 执行恢复
            snapshotRecoveryService.recoverFromSnapshot(snapshot);
            
            return ApiResponse.success("快照恢复成功: " + snapshot.getSnapshotId());
            
        } catch (Exception e) {
            log.error("快照恢复失败: snapshotId={}", snapshot.getSnapshotId(), e);
            return ApiResponse.error(500, "快照恢复失败: " + e.getMessage());
        }
    }
    
    /**
     * 验证快照数据完整性
     */
    @PostMapping("/validate")
    public ApiResponse<Boolean> validateSnapshot(@RequestBody MatchEngineSnapshot snapshot) {
        try {
            log.info("验证快照数据完整性: snapshotId={}", snapshot.getSnapshotId());
            
            boolean isValid = snapshotRecoveryService.validateSnapshot(snapshot);
            
            return ApiResponse.success(isValid);
            
        } catch (Exception e) {
            log.error("快照验证失败: snapshotId={}", snapshot.getSnapshotId(), e);
            return ApiResponse.error(500, "快照验证失败: " + e.getMessage());
        }
    }
} 