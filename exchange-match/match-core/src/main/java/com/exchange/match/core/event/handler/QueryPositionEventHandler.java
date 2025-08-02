package com.exchange.match.core.event.handler;

import com.exchange.match.core.event.EventHandler;
import com.exchange.match.core.event.MatchEvent;
import com.exchange.match.enums.EventType;
import com.exchange.match.request.EventQueryPositionReq;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 查询持仓事件处理器
 */
@Slf4j
@Component
public class QueryPositionEventHandler implements EventHandler {
    
    @Override
    public void handle(MatchEvent event) {
        try {
            EventQueryPositionReq queryPositionReq = event.getQueryPositionReq();
            log.info("处理查询持仓事件: symbol={}, userId={}", 
                    queryPositionReq.getSymbol(), queryPositionReq.getUserId());
            
            // TODO: 实现具体的查询持仓逻辑
            // 1. 验证用户权限
            // 2. 查询持仓信息
            // 3. 返回持仓详情
            
            // 模拟处理结果
            event.setResult("查询持仓成功: " + queryPositionReq.getSymbol());
            
        } catch (Exception e) {
            log.error("处理查询持仓事件失败", e);
            event.setException(e);
        }
    }
    
    @Override
    public EventType getSupportedEventType() {
        return EventType.QUERY_POSITION;
    }
} 