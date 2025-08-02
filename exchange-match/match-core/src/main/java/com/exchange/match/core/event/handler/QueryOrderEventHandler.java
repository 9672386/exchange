package com.exchange.match.core.event.handler;

import com.exchange.match.core.event.EventHandler;
import com.exchange.match.core.event.MatchEvent;
import com.exchange.match.core.memory.MemoryManager;
import com.exchange.match.core.model.*;
import com.exchange.match.enums.EventType;
import com.exchange.match.request.EventQueryOrderReq;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 查询订单事件处理器
 * 整合查询订单逻辑到Disruptor事件中
 */
@Slf4j
@Component
public class QueryOrderEventHandler implements EventHandler {
    
    @Autowired
    private MemoryManager memoryManager;
    
    @Override
    public void handle(MatchEvent event) {
        try {
            EventQueryOrderReq queryOrderReq = event.getQueryOrderReq();
            log.info("处理查询订单事件: symbol={}, userId={}, orderType={}", 
                    queryOrderReq.getSymbol(), queryOrderReq.getUserId(), queryOrderReq.getOrderType());
            
            // 执行查询订单逻辑
            QueryOrderResponse response = processQueryOrder(queryOrderReq);
            
            // 设置处理结果
            event.setResult(response);
            
            log.info("查询订单完成: symbol={}, userId={}, orderCount={}", 
                    queryOrderReq.getSymbol(), queryOrderReq.getUserId(), 
                    response.getOrders() != null ? response.getOrders().size() : 0);
            
        } catch (Exception e) {
            log.error("处理查询订单事件失败", e);
            event.setException(e);
        }
    }
    
    /**
     * 处理查询订单逻辑
     */
    private QueryOrderResponse processQueryOrder(EventQueryOrderReq queryOrderReq) {
        QueryOrderResponse response = new QueryOrderResponse();
        response.setUserId(queryOrderReq.getUserId());
        response.setSymbol(queryOrderReq.getSymbol());
        
        try {
            List<Order> userOrders = new ArrayList<>();
            
            // 查询所有订单薄
            for (OrderBook orderBook : memoryManager.getAllOrderBooks().values()) {
                // 如果指定了交易对，只查询该交易对的订单
                if (queryOrderReq.getSymbol() != null && !queryOrderReq.getSymbol().isEmpty()) {
                    if (!orderBook.getSymbol().equals(queryOrderReq.getSymbol())) {
                        continue;
                    }
                }
                
                // 获取该订单薄中属于该用户的订单
                List<Order> orders = orderBook.getUserOrders(queryOrderReq.getUserId());
                if (orders != null) {
                    userOrders.addAll(orders);
                }
            }
            
            // 根据订单类型过滤
            if (queryOrderReq.getOrderType() >= 0) {
                userOrders = userOrders.stream()
                        .filter(order -> order.getType().ordinal() == queryOrderReq.getOrderType())
                        .collect(Collectors.toList());
            }
            
            // 按创建时间排序（最新的在前）
            userOrders.sort((o1, o2) -> o2.getCreateTime().compareTo(o1.getCreateTime()));
            
            response.setOrders(userOrders);
            response.setTotalCount(userOrders.size());
            response.setSuccess(true);
            
        } catch (Exception e) {
            log.error("查询订单失败: userId={}, symbol={}", queryOrderReq.getUserId(), queryOrderReq.getSymbol(), e);
            response.setSuccess(false);
            response.setErrorMessage("系统错误: " + e.getMessage());
        }
        
        return response;
    }
    
    /**
     * 查询订单响应
     */
    public static class QueryOrderResponse {
        private Long userId;
        private String symbol;
        private List<Order> orders;
        private int totalCount;
        private boolean success;
        private String errorMessage;
        
        // Getters and Setters
        public Long getUserId() { return userId; }
        public void setUserId(Long userId) { this.userId = userId; }
        
        public String getSymbol() { return symbol; }
        public void setSymbol(String symbol) { this.symbol = symbol; }
        
        public List<Order> getOrders() { return orders; }
        public void setOrders(List<Order> orders) { this.orders = orders; }
        
        public int getTotalCount() { return totalCount; }
        public void setTotalCount(int totalCount) { this.totalCount = totalCount; }
        
        public boolean isSuccess() { return success; }
        public void setSuccess(boolean success) { this.success = success; }
        
        public String getErrorMessage() { return errorMessage; }
        public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    }
    
    @Override
    public EventType getSupportedEventType() {
        return EventType.QUERY_ORDER;
    }
} 