package com.exchange.match.core.event.handler;

import com.exchange.match.core.event.EventHandler;
import com.exchange.match.core.event.MatchEvent;
import com.exchange.match.core.memory.MemoryManager;
import com.exchange.match.core.model.*;
import com.exchange.match.enums.EventType;
import com.exchange.match.request.EventQueryPositionReq;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 查询持仓事件处理器
 * 整合查询持仓逻辑到Disruptor事件中
 */
@Slf4j
@Component
public class QueryPositionEventHandler implements EventHandler {
    
    @Autowired
    private MemoryManager memoryManager;
    
    @Override
    public void handle(MatchEvent event) {
        try {
            EventQueryPositionReq queryPositionReq = event.getQueryPositionReq();
            log.info("处理查询持仓事件: symbol={}, userId={}", 
                    queryPositionReq.getSymbol(), queryPositionReq.getUserId());
            
            // 执行查询持仓逻辑
            QueryPositionResponse response = processQueryPosition(queryPositionReq);
            
            // 设置处理结果
            event.setResult(response);
            
            log.info("查询持仓完成: symbol={}, userId={}, positionCount={}", 
                    queryPositionReq.getSymbol(), queryPositionReq.getUserId(), 
                    response.getPositions() != null ? response.getPositions().size() : 0);
            
        } catch (Exception e) {
            log.error("处理查询持仓事件失败", e);
            event.setException(e);
        }
    }
    
    /**
     * 处理查询持仓逻辑
     */
    private QueryPositionResponse processQueryPosition(EventQueryPositionReq queryPositionReq) {
        QueryPositionResponse response = new QueryPositionResponse();
        response.setUserId(queryPositionReq.getUserId());
        response.setSymbol(queryPositionReq.getSymbol());
        
        try {
            List<Position> userPositions = new ArrayList<>();
            
            // 查询所有持仓
            List<Position> allPositions = memoryManager.getUserPositions(queryPositionReq.getUserId());
            
            if (allPositions != null) {
                // 如果指定了交易对，只查询该交易对的持仓
                if (queryPositionReq.getSymbol() != null && !queryPositionReq.getSymbol().isEmpty()) {
                    userPositions = allPositions.stream()
                            .filter(position -> position.getSymbol().equals(queryPositionReq.getSymbol()))
                            .collect(Collectors.toList());
                } else {
                    userPositions = allPositions;
                }
            }
            
            // 过滤掉零仓位
            userPositions = userPositions.stream()
                    .filter(position -> position.getQuantity().compareTo(BigDecimal.ZERO) != 0)
                    .collect(Collectors.toList());
            
            response.setPositions(userPositions);
            response.setTotalCount(userPositions.size());
            response.setSuccess(true);
            
        } catch (Exception e) {
            log.error("查询持仓失败: userId={}, symbol={}", queryPositionReq.getUserId(), queryPositionReq.getSymbol(), e);
            response.setSuccess(false);
            response.setErrorMessage("系统错误: " + e.getMessage());
        }
        
        return response;
    }
    
    /**
     * 查询持仓响应
     */
    public static class QueryPositionResponse {
        private Long userId;
        private String symbol;
        private List<Position> positions;
        private int totalCount;
        private boolean success;
        private String errorMessage;
        
        // Getters and Setters
        public Long getUserId() { return userId; }
        public void setUserId(Long userId) { this.userId = userId; }
        
        public String getSymbol() { return symbol; }
        public void setSymbol(String symbol) { this.symbol = symbol; }
        
        public List<Position> getPositions() { return positions; }
        public void setPositions(List<Position> positions) { this.positions = positions; }
        
        public int getTotalCount() { return totalCount; }
        public void setTotalCount(int totalCount) { this.totalCount = totalCount; }
        
        public boolean isSuccess() { return success; }
        public void setSuccess(boolean success) { this.success = success; }
        
        public String getErrorMessage() { return errorMessage; }
        public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    }
    
    @Override
    public EventType getSupportedEventType() {
        return EventType.QUERY_POSITION;
    }
} 