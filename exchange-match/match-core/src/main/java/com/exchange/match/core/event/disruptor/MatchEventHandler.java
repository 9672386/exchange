package com.exchange.match.core.event.disruptor;

import com.exchange.match.core.event.MatchEvent;
import com.exchange.match.enums.EventType;
import com.lmax.disruptor.EventHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Disruptor事件处理器，用于处理RingBuffer中的事件
 */
@Slf4j
@Component
public class MatchEventHandler implements EventHandler<MatchEvent> {
    
    private final Map<EventType, com.exchange.match.core.event.EventHandler> eventHandlers;
    
    @Autowired
    public MatchEventHandler(List<com.exchange.match.core.event.EventHandler> handlers) {
        // 将事件处理器按类型分组
        this.eventHandlers = handlers.stream()
                .collect(Collectors.toMap(
                        com.exchange.match.core.event.EventHandler::getSupportedEventType,
                        handler -> handler
                ));
    }
    
    @Override
    public void onEvent(MatchEvent event, long sequence, boolean endOfBatch) {
        try {
            EventType eventType = event.getEventType();
            if (eventType == null) {
                log.error("事件类型为空，跳过处理");
                return;
            }
            
            com.exchange.match.core.event.EventHandler handler = eventHandlers.get(eventType);
            if (handler == null) {
                log.error("未找到事件类型 {} 的处理器", eventType);
                return;
            }
            
            log.debug("开始处理事件: type={}, sequence={}", eventType, sequence);
            
            // 调用对应的处理器处理事件
            handler.handle(event);
            
            log.debug("事件处理完成: type={}, sequence={}, result={}", 
                    eventType, sequence, event.getResult());
            
        } catch (Exception e) {
            log.error("处理事件失败: sequence={}", sequence, e);
            event.setException(e);
        }
    }
} 