package com.exchange.match.core.event.handler;

import com.exchange.match.core.event.EventHandler;
import com.exchange.match.core.event.MatchEvent;
import com.exchange.match.core.memory.MemoryManager;
import com.exchange.match.core.model.MatchResponse;
import com.exchange.match.core.model.MatchStatus;
import com.exchange.match.core.model.OrderBook;
import com.exchange.match.core.service.BatchKafkaService;
import com.exchange.match.core.service.CommandIdGenerator;
import com.exchange.match.core.model.StateChangeEvent;
import com.exchange.match.enums.EventType;
import com.exchange.match.request.EventClearReq;
import org.springframework.beans.factory.annotation.Autowired;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 清理事件处理器
 */
@Slf4j
@Component
public class ClearEventHandler implements EventHandler {
    
    @Autowired
    private MemoryManager memoryManager;
    
    @Autowired
    private BatchKafkaService batchKafkaService;
    
    @Override
    public void handle(MatchEvent event) {
        try {
            EventClearReq clearReq = event.getClearReq();
            log.info("处理清理事件: symbol={}", clearReq.getSymbol());
            
            // 执行清理逻辑
            MatchResponse response = processClearOrders(clearReq);
            
            // 只有成功清理才推送状态变动事件
            if (response.getStatus() == MatchStatus.SUCCESS) {
                // 生成命令ID
                long commandId = CommandIdGenerator.nextId();
                
                // 创建状态变动事件
                StateChangeEvent stateChangeEvent = StateChangeEvent.createSuccess(
                    commandId, 
                    EventType.CLEAR, 
                    clearReq, 
                    response
                );
                
                // 推送状态变动事件到Kafka
                batchKafkaService.pushStateChangeEvent(stateChangeEvent);
                
                log.info("推送清理状态变动事件: commandId={}, symbol={}", 
                        commandId, clearReq.getSymbol());
            } else {
                log.info("清理失败，不推送状态变动事件: symbol={}, status={}, reason={}", 
                        clearReq.getSymbol(), response.getStatus(), response.getErrorMessage());
            }
            
            // 设置处理结果
            event.setResult(response);
            
        } catch (Exception e) {
            log.error("处理清理事件失败", e);
            event.setException(e);
        }
    }
    
    /**
     * 处理清理订单逻辑
     */
    private MatchResponse processClearOrders(EventClearReq clearReq) {
        MatchResponse response = new MatchResponse();
        response.setSymbol(clearReq.getSymbol());
        
        try {
            // 获取订单薄
            OrderBook orderBook = memoryManager.getOrderBook(clearReq.getSymbol());
            if (orderBook == null) {
                response.setStatus(MatchStatus.REJECTED);
                response.setErrorMessage("交易对不存在: " + clearReq.getSymbol());
                return response;
            }
            
            // 清理订单薄中的所有订单
            int clearedCount = orderBook.getOrderCount();
            orderBook.clear();
            
            response.setStatus(MatchStatus.SUCCESS);
            response.setErrorMessage("清理完成，共清理订单: " + clearedCount);
            
            log.info("清理订单完成: symbol={}, clearedCount={}", clearReq.getSymbol(), clearedCount);
            
        } catch (Exception e) {
            log.error("清理订单失败: symbol={}", clearReq.getSymbol(), e);
            response.setStatus(MatchStatus.REJECTED);
            response.setErrorMessage("清理失败: " + e.getMessage());
        }
        
        return response;
    }
    
    @Override
    public EventType getSupportedEventType() {
        return EventType.CLEAR;
    }
} 