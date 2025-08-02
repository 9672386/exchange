package com.exchange.match.core.controller;

import com.exchange.common.response.ApiResponse;
import com.exchange.match.core.service.KafkaConsumerService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Kafka消费者监控控制器
 */
@Slf4j
@RestController
@RequestMapping("/api/kafka/consumer")
public class KafkaConsumerController {
    
    @Autowired
    private KafkaConsumerService consumerService;
    
    /**
     * 获取当前消费的offset
     */
    @GetMapping("/offsets")
    public ApiResponse<Map<String, Long>> getCurrentConsumerOffsets() {
        try {
            Map<String, Long> offsets = consumerService.getCurrentConsumerOffsets();
            return ApiResponse.success(offsets);
        } catch (Exception e) {
            log.error("获取当前消费offset失败", e);
            return ApiResponse.error(500, "获取当前消费offset失败: " + e.getMessage());
        }
    }
    
    /**
     * 检查消费状态
     */
    @GetMapping("/status")
    public ApiResponse<Boolean> getConsumerStatus() {
        try {
            boolean isConsuming = consumerService.isConsuming();
            return ApiResponse.success(isConsuming);
        } catch (Exception e) {
            log.error("获取消费状态失败", e);
            return ApiResponse.error(500, "获取消费状态失败: " + e.getMessage());
        }
    }
    
    /**
     * 暂停消费
     */
    @PostMapping("/pause")
    public ApiResponse<String> pauseConsuming() {
        try {
            consumerService.pauseConsuming();
            return ApiResponse.success("暂停消费成功");
        } catch (Exception e) {
            log.error("暂停消费失败", e);
            return ApiResponse.error(500, "暂停消费失败: " + e.getMessage());
        }
    }
    
    /**
     * 恢复消费
     */
    @PostMapping("/resume")
    public ApiResponse<String> resumeConsuming() {
        try {
            consumerService.resumeConsuming();
            return ApiResponse.success("恢复消费成功");
        } catch (Exception e) {
            log.error("恢复消费失败", e);
            return ApiResponse.error(500, "恢复消费失败: " + e.getMessage());
        }
    }
} 