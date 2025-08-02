package com.exchange.match.core.event.handler;

import com.exchange.match.core.event.EventHandler;
import com.exchange.match.core.event.MatchEvent;
import com.exchange.match.enums.EventType;
import com.exchange.match.request.EventClearReq;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 清理事件处理器
 */
@Slf4j
@Component
public class ClearEventHandler implements EventHandler {
    
    @Override
    public void handle(MatchEvent event) {
        try {
            EventClearReq clearReq = event.getClearReq();
            log.info("处理清理事件: symbol={}", clearReq.getSymbol());
            
            // TODO: 实现具体的清理处理逻辑
            // 1. 验证用户权限
            // 2. 清理指定symbol的所有订单
            // 3. 清理撮合引擎中的订单
            // 4. 更新数据库状态
            
            // 模拟处理结果
            event.setResult("清理处理成功: " + clearReq.getSymbol());
            
        } catch (Exception e) {
            log.error("处理清理事件失败", e);
            event.setException(e);
        }
    }
    
    @Override
    public EventType getSupportedEventType() {
        return EventType.CLEAR;
    }
} 