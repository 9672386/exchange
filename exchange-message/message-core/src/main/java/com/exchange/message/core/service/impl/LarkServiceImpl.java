package com.exchange.message.core.service.impl;

import com.exchange.message.api.dto.MessageSendRequest;
import com.exchange.message.api.dto.MessageSendResponse;
import com.exchange.message.core.service.LarkService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Lark服务实现
 */
@Service
public class LarkServiceImpl implements LarkService {
    
    private static final Logger log = LoggerFactory.getLogger(LarkServiceImpl.class);
    
    @Override
    public MessageSendResponse sendLarkMessage(MessageSendRequest request) {
        try {
            log.info("发送Lark消息: {}", request);
            
            // 根据消息类型选择发送方式
            String content = request.getContent();
            if (content.contains("buttons") || content.contains("card")) {
                // 卡片消息
                return sendCardMessage(request.getReceiver(), request.getSubject(), content, null);
            } else if (content.contains("rich_text") || content.contains("**")) {
                // 富文本消息
                return sendRichTextMessage(request.getReceiver(), request.getSubject(), content);
            } else {
                // 普通文本消息
                return sendTextMessage(request.getReceiver(), content);
            }
        } catch (Exception e) {
            log.error("发送Lark消息失败", e);
            return MessageSendResponse.failure("LARK_SEND_ERROR", "发送Lark消息失败: " + e.getMessage());
        }
    }
    
    @Override
    public MessageSendResponse sendTextMessage(String chatId, String text) {
        try {
            log.info("发送Lark文本消息: chatId={}, text={}", chatId, text);
            
            // TODO: 集成Lark SDK
            String messageId = "LARK_TEXT_" + System.currentTimeMillis();
            
            return MessageSendResponse.success(messageId);
        } catch (Exception e) {
            log.error("发送Lark文本消息失败", e);
            return MessageSendResponse.failure("LARK_TEXT_ERROR", "发送Lark文本消息失败: " + e.getMessage());
        }
    }
    
    @Override
    public MessageSendResponse sendRichTextMessage(String chatId, String title, String content) {
        try {
            log.info("发送Lark富文本消息: chatId={}, title={}", chatId, title);
            
            // TODO: 集成Lark SDK for rich text
            String messageId = "LARK_RICH_TEXT_" + System.currentTimeMillis();
            
            return MessageSendResponse.success(messageId);
        } catch (Exception e) {
            log.error("发送Lark富文本消息失败", e);
            return MessageSendResponse.failure("LARK_RICH_TEXT_ERROR", "发送Lark富文本消息失败: " + e.getMessage());
        }
    }
    
    @Override
    public MessageSendResponse sendCardMessage(String chatId, String title, String content, List<String> buttons) {
        try {
            log.info("发送Lark卡片消息: chatId={}, title={}", chatId, title);
            
            // TODO: 集成Lark SDK for card message
            String messageId = "LARK_CARD_" + System.currentTimeMillis();
            
            return MessageSendResponse.success(messageId);
        } catch (Exception e) {
            log.error("发送Lark卡片消息失败", e);
            return MessageSendResponse.failure("LARK_CARD_ERROR", "发送Lark卡片消息失败: " + e.getMessage());
        }
    }
} 