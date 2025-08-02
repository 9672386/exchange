package com.exchange.match.core.controller;

import com.exchange.common.response.ApiResponse;
import com.exchange.match.core.service.MatchEventService;
import com.exchange.match.request.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

/**
 * 撮合事件控制器
 */
@Slf4j
@RestController
@RequestMapping("/api/match/event")
public class MatchEventController {
    
    @Autowired
    private MatchEventService matchEventService;
    
    /**
     * 提交新订单
     */
    @PostMapping("/new-order")
    public ApiResponse<String> submitNewOrder(@RequestBody EventNewOrderReq newOrderReq) {
        try {
            String result = matchEventService.submitNewOrder(newOrderReq);
            return ApiResponse.success(result);
        } catch (Exception e) {
            log.error("提交新订单失败", e);
            return ApiResponse.error(500, "提交新订单失败: " + e.getMessage());
        }
    }
    
    /**
     * 撤销订单
     */
    @PostMapping("/cancel-order")
    public ApiResponse<String> cancelOrder(@RequestBody EventCanalReq canalReq) {
        try {
            String result = matchEventService.cancelOrder(canalReq);
            return ApiResponse.success(result);
        } catch (Exception e) {
            log.error("撤销订单失败", e);
            return ApiResponse.error(500, "撤销订单失败: " + e.getMessage());
        }
    }
    
    /**
     * 清理订单
     */
    @PostMapping("/clear-orders")
    public ApiResponse<String> clearOrders(@RequestBody EventClearReq clearReq) {
        try {
            String result = matchEventService.clearOrders(clearReq);
            return ApiResponse.success(result);
        } catch (Exception e) {
            log.error("清理订单失败", e);
            return ApiResponse.error(500, "清理订单失败: " + e.getMessage());
        }
    }
    
    /**
     * 生成快照
     */
    @PostMapping("/snapshot")
    public ApiResponse<String> generateSnapshot(@RequestBody EventSnapshotReq snapshotReq) {
        try {
            String result = matchEventService.generateSnapshot(snapshotReq);
            return ApiResponse.success(result);
        } catch (Exception e) {
            log.error("生成快照失败", e);
            return ApiResponse.error(500, "生成快照失败: " + e.getMessage());
        }
    }
    
    /**
     * 停止引擎
     */
    @PostMapping("/stop")
    public ApiResponse<String> stopEngine(@RequestBody EventStopReq stopReq) {
        try {
            String result = matchEventService.stopEngine(stopReq);
            return ApiResponse.success(result);
        } catch (Exception e) {
            log.error("停止引擎失败", e);
            return ApiResponse.error(500, "停止引擎失败: " + e.getMessage());
        }
    }
    
    /**
     * 查询订单
     */
    @PostMapping("/query-order")
    public ApiResponse<String> queryOrder(@RequestBody EventQueryOrderReq queryOrderReq) {
        try {
            String result = matchEventService.queryOrder(queryOrderReq);
            return ApiResponse.success(result);
        } catch (Exception e) {
            log.error("查询订单失败", e);
            return ApiResponse.error(500, "查询订单失败: " + e.getMessage());
        }
    }
    
    /**
     * 查询持仓
     */
    @PostMapping("/query-position")
    public ApiResponse<String> queryPosition(@RequestBody EventQueryPositionReq queryPositionReq) {
        try {
            String result = matchEventService.queryPosition(queryPositionReq);
            return ApiResponse.success(result);
        } catch (Exception e) {
            log.error("查询持仓失败", e);
            return ApiResponse.error(500, "查询持仓失败: " + e.getMessage());
        }
    }
} 