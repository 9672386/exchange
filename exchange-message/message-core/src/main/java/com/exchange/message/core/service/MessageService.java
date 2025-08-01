package com.exchange.message.core.service;

import com.exchange.message.api.dto.MessageSendRequest;
import com.exchange.message.api.dto.MessageSendResponse;

/**
 * 消息发送服务接口
 */
public interface MessageService {
    
    /**
     * 发送消息
     * @param request 消息发送请求
     * @return 消息发送响应
     */
    MessageSendResponse sendMessage(MessageSendRequest request);
    
    /**
     * 批量发送消息
     * @param request 消息发送请求
     * @param receivers 接收者列表
     * @return 消息发送响应列表
     */
    java.util.List<MessageSendResponse> sendBatchMessage(MessageSendRequest request, java.util.List<String> receivers);
    
    /**
     * 异步发送消息
     * @param request 消息发送请求
     */
    void sendMessageAsync(MessageSendRequest request);
} 