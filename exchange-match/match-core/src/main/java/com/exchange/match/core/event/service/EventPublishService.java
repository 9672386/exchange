package com.exchange.match.core.event.service;

import com.exchange.match.core.event.MatchEvent;
import com.exchange.match.enums.EventType;
import com.exchange.match.request.*;
import com.lmax.disruptor.RingBuffer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * 事件发布服务
 */
@Slf4j
@Service
public class EventPublishService {
    
    @Autowired
    private RingBuffer<MatchEvent> ringBuffer;
    
    /**
     * 发布新订单事件
     */
    public void publishNewOrderEvent(EventNewOrderReq newOrderReq) {
        publishEvent(EventType.NEW_ORDER, event -> {
            event.setNewOrderReq(newOrderReq);
        });
    }
    
    /**
     * 发布撤单事件
     */
    public void publishCanalEvent(EventCanalReq canalReq) {
        publishEvent(EventType.CANAL, event -> {
            event.setCanalReq(canalReq);
        });
    }
    
    /**
     * 发布清理事件
     */
    public void publishClearEvent(EventClearReq clearReq) {
        publishEvent(EventType.CLEAR, event -> {
            event.setClearReq(clearReq);
        });
    }
    
    /**
     * 发布快照事件
     */
    public void publishSnapshotEvent(EventSnapshotReq snapshotReq) {
        publishEvent(EventType.SNAPSHOT, event -> {
            event.setSnapshotReq(snapshotReq);
        });
    }
    
    /**
     * 发布停止事件
     */
    public void publishStopEvent(EventStopReq stopReq) {
        publishEvent(EventType.STOP, event -> {
            event.setStopReq(stopReq);
        });
    }
    
    /**
     * 发布查询订单事件
     */
    public void publishQueryOrderEvent(EventQueryOrderReq queryOrderReq) {
        publishEvent(EventType.QUERY_ORDER, event -> {
            event.setQueryOrderReq(queryOrderReq);
        });
    }
    
    /**
     * 发布查询持仓事件
     */
    public void publishQueryPositionEvent(EventQueryPositionReq queryPositionReq) {
        publishEvent(EventType.QUERY_POSITION, event -> {
            event.setQueryPositionReq(queryPositionReq);
        });
    }
    
    /**
     * 发布强平事件
     */
    public void publishLiquidationEvent(EventLiquidationReq liquidationReq) {
        publishEvent(EventType.LIQUIDATION, event -> {
            event.setLiquidationReq(liquidationReq);
        });
    }
    
    /**
     * 通用事件发布方法
     */
    private void publishEvent(EventType eventType, EventConfigurer configurer) {
        long sequence = ringBuffer.next();
        try {
            MatchEvent event = ringBuffer.get(sequence);
            event.clear(); // 清理之前的数据
            
            event.setEventType(eventType);
            event.setTimestamp(System.currentTimeMillis());
            
            // 配置事件数据
            configurer.configure(event);
            
            log.debug("发布事件: type={}, sequence={}", eventType, sequence);
            
        } finally {
            ringBuffer.publish(sequence);
        }
    }
    
    /**
     * 事件配置器接口
     */
    @FunctionalInterface
    private interface EventConfigurer {
        void configure(MatchEvent event);
    }
} 