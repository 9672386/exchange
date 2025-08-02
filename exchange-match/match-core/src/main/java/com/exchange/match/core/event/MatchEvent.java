package com.exchange.match.core.event;

import com.exchange.match.enums.EventType;
import com.exchange.match.request.*;
import lombok.Data;

import java.io.Serializable;

/**
 * Disruptor事件对象，用于在RingBuffer中传递事件数据
 */
@Data
public class MatchEvent implements Serializable {
    
    /**
     * 事件类型
     */
    private EventType eventType;
    
    /**
     * 事件请求对象
     */
    private EventReq<?> eventReq;
    
    /**
     * 具体的事件请求对象
     */
    private EventNewOrderReq newOrderReq;
    private EventCanalReq canalReq;
    private EventClearReq clearReq;
    private EventSnapshotReq snapshotReq;
    private EventStopReq stopReq;
    private EventQueryOrderReq queryOrderReq;
    private EventQueryPositionReq queryPositionReq;
    
    /**
     * 事件处理结果
     */
    private Object result;
    
    /**
     * 异常信息
     */
    private Throwable exception;
    
    /**
     * 事件创建时间戳
     */
    private long timestamp;
    
    /**
     * 清理事件数据
     */
    public void clear() {
        this.eventType = null;
        this.eventReq = null;
        this.newOrderReq = null;
        this.canalReq = null;
        this.clearReq = null;
        this.snapshotReq = null;
        this.stopReq = null;
        this.queryOrderReq = null;
        this.queryPositionReq = null;
        this.result = null;
        this.exception = null;
        this.timestamp = 0;
    }
} 