package com.exchange.match.core.event;

import com.exchange.match.enums.EventType;

/**
 * 事件处理器接口
 */
public interface EventHandler {
    
    /**
     * 处理事件
     * @param event 事件对象
     */
    void handle(MatchEvent event);
    
    /**
     * 获取支持的事件类型
     * @return 事件类型
     */
    EventType getSupportedEventType();
    
    /**
     * 检查是否支持指定的事件类型
     * @param eventType 事件类型
     * @return 是否支持
     */
    default boolean supports(EventType eventType) {
        return getSupportedEventType() == eventType;
    }
} 