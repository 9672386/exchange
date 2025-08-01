package com.exchange.message.core.service.impl;

import com.exchange.message.api.dto.MessageSendRequest;
import com.exchange.message.api.dto.MessageSendResponse;
import com.exchange.message.core.service.TelegramService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Telegram服务实现
 */
@Service
public class TelegramServiceImpl implements TelegramService {
    
    private static final Logger log = LoggerFactory.getLogger(TelegramServiceImpl.class);
    
    @Override
    public MessageSendResponse sendTelegramMessage(MessageSendRequest request) {
        try {
            log.info("发送Telegram消息: {}", request);
            
            // 根据消息类型选择发送方式
            String content = request.getContent();
            if (content.startsWith("![") || content.contains("http")) {
                // 图片消息
                return sendPhotoMessage(request.getReceiver(), request.getSubject(), content);
            } else if (content.contains("**") || content.contains("*") || content.contains("`")) {
                // Markdown消息
                return sendMarkdownMessage(request.getReceiver(), content);
            } else {
                // 普通文本消息
                return sendTextMessage(request.getReceiver(), content);
            }
        } catch (Exception e) {
            log.error("发送Telegram消息失败", e);
            return MessageSendResponse.failure("TELEGRAM_SEND_ERROR", "发送Telegram消息失败: " + e.getMessage());
        }
    }
    
    @Override
    public MessageSendResponse sendTextMessage(String chatId, String text) {
        try {
            log.info("发送Telegram文本消息: chatId={}, text={}", chatId, text);
            
            // TODO: 集成Telegram Bot API
            String messageId = "TELEGRAM_TEXT_" + System.currentTimeMillis();
            
            return MessageSendResponse.success(messageId);
        } catch (Exception e) {
            log.error("发送Telegram文本消息失败", e);
            return MessageSendResponse.failure("TELEGRAM_TEXT_ERROR", "发送Telegram文本消息失败: " + e.getMessage());
        }
    }
    
    @Override
    public MessageSendResponse sendMarkdownMessage(String chatId, String markdownText) {
        try {
            log.info("发送Telegram Markdown消息: chatId={}", chatId);
            
            // TODO: 集成Telegram Bot API with Markdown support
            String messageId = "TELEGRAM_MARKDOWN_" + System.currentTimeMillis();
            
            return MessageSendResponse.success(messageId);
        } catch (Exception e) {
            log.error("发送Telegram Markdown消息失败", e);
            return MessageSendResponse.failure("TELEGRAM_MARKDOWN_ERROR", "发送Telegram Markdown消息失败: " + e.getMessage());
        }
    }
    
    @Override
    public MessageSendResponse sendPhotoMessage(String chatId, String caption, String imageUrl) {
        try {
            log.info("发送Telegram图片消息: chatId={}, caption={}, imageUrl={}", chatId, caption, imageUrl);
            
            // TODO: 集成Telegram Bot API for photo sending
            String messageId = "TELEGRAM_PHOTO_" + System.currentTimeMillis();
            
            return MessageSendResponse.success(messageId);
        } catch (Exception e) {
            log.error("发送Telegram图片消息失败", e);
            return MessageSendResponse.failure("TELEGRAM_PHOTO_ERROR", "发送Telegram图片消息失败: " + e.getMessage());
        }
    }
} 