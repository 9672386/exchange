package com.exchange.match.core.event.handler;

import com.exchange.match.core.event.EventHandler;
import com.exchange.match.core.event.MatchEvent;
import com.exchange.match.enums.EventType;
import com.exchange.match.request.EventCanalReq;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 撤单事件处理器
 */
@Slf4j
@Component
public class CanalEventHandler implements EventHandler {
    
    @Override
    public void handle(MatchEvent event) {
        try {
            EventCanalReq canalReq = event.getCanalReq();
            log.info("处理撤单事件: orderId={}, userId={}", 
                    canalReq.getOrderId(), canalReq.getUserId());
            
            // TODO: 实现具体的撤单处理逻辑
            // 1. 验证订单是否存在
            // 2. 检查用户权限
            // 3. 从撮合引擎中移除订单
            // 4. 更新订单状态
            
            // 模拟处理结果
            event.setResult("撤单处理成功: " + canalReq.getOrderId());
            
        } catch (Exception e) {
            log.error("处理撤单事件失败", e);
            event.setException(e);
        }
    }
    
    @Override
    public EventType getSupportedEventType() {
        return EventType.CANAL;
    }
} 