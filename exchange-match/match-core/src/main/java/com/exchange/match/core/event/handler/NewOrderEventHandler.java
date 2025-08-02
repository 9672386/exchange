package com.exchange.match.core.event.handler;

import com.exchange.match.core.event.EventHandler;
import com.exchange.match.core.event.MatchEvent;
import com.exchange.match.enums.EventType;
import com.exchange.match.request.EventNewOrderReq;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 新订单事件处理器
 */
@Slf4j
@Component
public class NewOrderEventHandler implements EventHandler {
    
    @Override
    public void handle(MatchEvent event) {
        try {
            EventNewOrderReq newOrderReq = event.getNewOrderReq();
            log.info("处理新订单事件: orderId={}, userId={}", 
                    newOrderReq.getOrderId(), newOrderReq.getUserId());
            
            // TODO: 实现具体的订单处理逻辑
            // 1. 验证订单参数
            // 2. 检查用户权限
            // 3. 创建订单记录
            // 4. 发送到撮合引擎
            
            // 模拟处理结果
            event.setResult("新订单处理成功: " + newOrderReq.getOrderId());
            
        } catch (Exception e) {
            log.error("处理新订单事件失败", e);
            event.setException(e);
        }
    }
    
    @Override
    public EventType getSupportedEventType() {
        return EventType.NEW_ORDER;
    }
} 