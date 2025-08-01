package com.exchange.message.core.service.sender;

import com.exchange.message.api.dto.MessageSendRequest;
import com.exchange.message.api.dto.MessageSendResponse;

/**
 * 消息发送器接口
 */
public interface MessageSender {
    
    /**
     * 发送消息
     * @param request 消息发送请求
     * @return 发送响应
     */
    MessageSendResponse send(MessageSendRequest request);
    
    /**
     * 获取支持的平台类型
     * @return 平台类型
     */
    String getPlatformType();
    
    /**
     * 获取消息类型
     * @return 消息类型
     */
    String getMessageType();
    
    /**
     * 检查是否支持该平台和消息类型
     * @param platformType 平台类型
     * @param messageType 消息类型
     * @return 是否支持
     */
    boolean supports(String platformType, String messageType);
} 