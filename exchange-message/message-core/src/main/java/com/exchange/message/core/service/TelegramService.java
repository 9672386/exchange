package com.exchange.message.core.service;

import com.exchange.message.api.dto.MessageSendRequest;
import com.exchange.message.api.dto.MessageSendResponse;

/**
 * Telegram发送服务接口
 */
public interface TelegramService {
    
    /**
     * 发送Telegram消息
     * @param request 消息发送请求
     * @return 消息发送响应
     */
    MessageSendResponse sendTelegramMessage(MessageSendRequest request);
    
    /**
     * 发送文本消息
     * @param chatId 聊天ID
     * @param text 文本内容
     * @return 消息发送响应
     */
    MessageSendResponse sendTextMessage(String chatId, String text);
    
    /**
     * 发送Markdown消息
     * @param chatId 聊天ID
     * @param markdownText Markdown文本
     * @return 消息发送响应
     */
    MessageSendResponse sendMarkdownMessage(String chatId, String markdownText);
    
    /**
     * 发送图片消息
     * @param chatId 聊天ID
     * @param caption 图片说明
     * @param imageUrl 图片URL
     * @return 消息发送响应
     */
    MessageSendResponse sendPhotoMessage(String chatId, String caption, String imageUrl);
} 