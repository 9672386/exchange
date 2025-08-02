package com.exchange.match.core.event.handler;

import com.exchange.match.core.event.EventHandler;
import com.exchange.match.core.event.MatchEvent;
import com.exchange.match.enums.EventType;
import com.exchange.match.request.EventStopReq;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 停止事件处理器
 */
@Slf4j
@Component
public class StopEventHandler implements EventHandler {
    
    @Override
    public void handle(MatchEvent event) {
        try {
            EventStopReq stopReq = event.getStopReq();
            log.info("处理停止事件");
            
            // TODO: 实现具体的停止处理逻辑
            // 1. 停止撮合引擎
            // 2. 保存当前状态
            // 3. 清理内存数据
            // 4. 发送停止通知
            
            // 模拟处理结果
            event.setResult("停止处理成功");
            
        } catch (Exception e) {
            log.error("处理停止事件失败", e);
            event.setException(e);
        }
    }
    
    @Override
    public EventType getSupportedEventType() {
        return EventType.STOP;
    }
} 