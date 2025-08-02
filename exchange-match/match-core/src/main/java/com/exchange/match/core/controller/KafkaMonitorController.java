package com.exchange.match.core.controller;

import com.exchange.common.response.ApiResponse;
import com.exchange.match.core.service.BatchKafkaService;
import com.exchange.match.core.service.KafkaOffsetManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * Kafka监控控制器
 */
@Slf4j
@RestController
@RequestMapping("/api/kafka/monitor")
public class KafkaMonitorController {
    
    @Autowired
    private BatchKafkaService batchKafkaService;
    
    /**
     * 获取Kafka推送状态
     */
    @GetMapping("/status")
    public ApiResponse<Map<String, Object>> getKafkaStatus() {
        try {
            Map<String, Object> status = new HashMap<>();
            status.put("pendingMessageCount", batchKafkaService.getPendingMessageCount());
            status.put("totalPushedCount", batchKafkaService.getTotalPushedCount());
            status.put("timestamp", System.currentTimeMillis());
            
            return ApiResponse.success(status);
        } catch (Exception e) {
            log.error("获取Kafka状态失败", e);
            return ApiResponse.error(500, "获取Kafka状态失败: " + e.getMessage());
        }
    }
    
    /**
     * 获取offset状态
     */
    @GetMapping("/offset/status")
    public ApiResponse<Map<String, KafkaOffsetManager.OffsetStatus>> getOffsetStatus() {
        try {
            Map<String, KafkaOffsetManager.OffsetStatus> offsetStatus = batchKafkaService.getOffsetStatus();
            return ApiResponse.success(offsetStatus);
        } catch (Exception e) {
            log.error("获取offset状态失败", e);
            return ApiResponse.error(500, "获取offset状态失败: " + e.getMessage());
        }
    }
    
    /**
     * 获取offset一致性状态
     */
    @GetMapping("/offset/consistency")
    public ApiResponse<Map<String, Boolean>> getOffsetConsistency() {
        try {
            Map<String, Boolean> consistency = batchKafkaService.getOffsetConsistency();
            return ApiResponse.success(consistency);
        } catch (Exception e) {
            log.error("获取offset一致性状态失败", e);
            return ApiResponse.error(500, "获取offset一致性状态失败: " + e.getMessage());
        }
    }
    
    /**
     * 获取待确认消息数量
     */
    @GetMapping("/offset/pending")
    public ApiResponse<Map<String, Long>> getPendingMessageCounts() {
        try {
            Map<String, Long> pendingCounts = batchKafkaService.getPendingMessageCounts();
            return ApiResponse.success(pendingCounts);
        } catch (Exception e) {
            log.error("获取待确认消息数量失败", e);
            return ApiResponse.error(500, "获取待确认消息数量失败: " + e.getMessage());
        }
    }
    
    /**
     * 重置主题的offset
     */
    @PostMapping("/offset/reset/{topic}")
    public ApiResponse<String> resetOffset(@PathVariable String topic) {
        try {
            batchKafkaService.resetOffset(topic);
            return ApiResponse.success("重置主题" + topic + "的offset成功");
        } catch (Exception e) {
            log.error("重置主题{}的offset失败", topic, e);
            return ApiResponse.error(500, "重置主题" + topic + "的offset失败: " + e.getMessage());
        }
    }
    
    /**
     * 强制推送所有待发送消息
     */
    @PostMapping("/force-flush")
    public ApiResponse<String> forceFlush() {
        try {
            batchKafkaService.forceFlush();
            return ApiResponse.success("强制推送完成");
        } catch (Exception e) {
            log.error("强制推送失败", e);
            return ApiResponse.error(500, "强制推送失败: " + e.getMessage());
        }
    }
} 