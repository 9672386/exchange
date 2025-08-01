package com.exchange.message.core.service;

import com.exchange.message.api.dto.MessageSendRequest;
import com.exchange.message.api.dto.MessageSendResponse;

/**
 * 飞书发送服务接口
 */
public interface LarkService {
    
    /**
     * 发送飞书消息
     * @param request 消息发送请求
     * @return 消息发送响应
     */
    MessageSendResponse sendLarkMessage(MessageSendRequest request);
    
    /**
     * 发送文本消息
     * @param chatId 聊天ID
     * @param text 文本内容
     * @return 消息发送响应
     */
    MessageSendResponse sendTextMessage(String chatId, String text);
    
    /**
     * 发送富文本消息
     * @param chatId 聊天ID
     * @param title 标题
     * @param content 内容
     * @return 消息发送响应
     */
    MessageSendResponse sendRichTextMessage(String chatId, String title, String content);
    
    /**
     * 发送卡片消息
     * @param chatId 聊天ID
     * @param title 标题
     * @param content 内容
     * @param buttons 按钮列表
     * @return 消息发送响应
     */
    MessageSendResponse sendCardMessage(String chatId, String title, String content, java.util.List<String> buttons);
} 