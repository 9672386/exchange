package com.exchange.match.core.model;

import com.exchange.match.enums.EventType;
import com.exchange.match.request.*;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 状态变动事件
 * 只对内存状态产生实际影响的事件才会被持久化
 */
@Data
public class StateChangeEvent implements Serializable {
    
    /**
     * 全局命令ID
     */
    private long commandId;
    
    /**
     * 事件类型
     */
    private EventType eventType;
    
    /**
     * 时间戳
     */
    private long timestamp;
    
    /**
     * 创建时间
     */
    private LocalDateTime createTime;
    
    /**
     * 原始请求（包含完整请求信息）
     */
    private Object originalRequest;
    
    /**
     * 处理结果
     */
    private Object result;
    
    /**
     * 是否成功
     */
    private boolean success;
    
    /**
     * 错误信息
     */
    private String errorMessage;
    
    /**
     * 创建状态变动事件
     */
    public static StateChangeEvent create(long commandId, EventType eventType, Object originalRequest, Object result, boolean success, String errorMessage) {
        StateChangeEvent event = new StateChangeEvent();
        event.setCommandId(commandId);
        event.setEventType(eventType);
        event.setTimestamp(System.currentTimeMillis());
        event.setCreateTime(LocalDateTime.now());
        event.setOriginalRequest(originalRequest);
        event.setResult(result);
        event.setSuccess(success);
        event.setErrorMessage(errorMessage);
        return event;
    }
    
    /**
     * 创建成功事件
     */
    public static StateChangeEvent createSuccess(long commandId, EventType eventType, Object originalRequest, Object result) {
        return create(commandId, eventType, originalRequest, result, true, null);
    }
    
    /**
     * 创建失败事件
     */
    public static StateChangeEvent createFailure(long commandId, EventType eventType, Object originalRequest, String errorMessage) {
        return create(commandId, eventType, originalRequest, null, false, errorMessage);
    }
} 