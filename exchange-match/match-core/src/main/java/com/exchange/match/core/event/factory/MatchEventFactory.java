package com.exchange.match.core.event.factory;

import com.exchange.match.core.event.MatchEvent;
import com.lmax.disruptor.EventFactory;

/**
 * Disruptor事件工厂，用于创建MatchEvent对象
 */
public class MatchEventFactory implements EventFactory<MatchEvent> {
    
    @Override
    public MatchEvent newInstance() {
        return new MatchEvent();
    }
} 