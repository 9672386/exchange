package com.exchange.match.core.event.handler;

import com.exchange.match.core.event.EventHandler;
import com.exchange.match.core.event.MatchEvent;
import com.exchange.match.enums.EventType;
import com.exchange.match.request.EventQueryOrderReq;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 查询订单事件处理器
 */
@Slf4j
@Component
public class QueryOrderEventHandler implements EventHandler {
    
    @Override
    public void handle(MatchEvent event) {
        try {
            EventQueryOrderReq queryOrderReq = event.getQueryOrderReq();
            log.info("处理查询订单事件: symbol={}, userId={}, orderType={}", 
                    queryOrderReq.getSymbol(), queryOrderReq.getUserId(), queryOrderReq.getOrderType());
            
            // TODO: 实现具体的查询订单逻辑
            // 1. 验证用户权限
            // 2. 查询订单信息
            // 3. 返回订单详情
            
            // 模拟处理结果
            event.setResult("查询订单成功: " + queryOrderReq.getSymbol());
            
        } catch (Exception e) {
            log.error("处理查询订单事件失败", e);
            event.setException(e);
        }
    }
    
    @Override
    public EventType getSupportedEventType() {
        return EventType.QUERY_ORDER;
    }
} 