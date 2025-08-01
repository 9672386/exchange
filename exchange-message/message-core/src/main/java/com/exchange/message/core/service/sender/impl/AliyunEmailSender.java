package com.exchange.message.core.service.sender.impl;

import com.exchange.message.api.dto.MessageSendRequest;
import com.exchange.message.api.dto.MessageSendResponse;
import com.exchange.message.api.enums.MessageType;
import com.exchange.message.api.enums.PlatformType;
import com.exchange.message.core.service.sender.EmailSender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * 阿里云邮件发送器
 */
@Service
public class AliyunEmailSender implements EmailSender {
    
    private static final Logger log = LoggerFactory.getLogger(AliyunEmailSender.class);
    
    @Override
    public MessageSendResponse send(MessageSendRequest request) {
        try {
            log.info("阿里云邮件发送: {}", request);
            
            String to = request.getReceiver();
            String subject = request.getSubject();
            String content = request.getContent();
            
            if (content != null && content.contains("<html>")) {
                return sendHtmlEmail(to, subject, content);
            } else {
                return sendSimpleEmail(to, subject, content);
            }
        } catch (Exception e) {
            log.error("阿里云邮件发送失败", e);
            return MessageSendResponse.failure("ALIYUN_EMAIL_ERROR", "阿里云邮件发送失败: " + e.getMessage());
        }
    }
    
    @Override
    public MessageSendResponse sendSimpleEmail(String to, String subject, String content) {
        try {
            log.info("发送阿里云简单邮件: to={}, subject={}", to, subject);
            
            // TODO: 集成阿里云邮件SDK
            String messageId = "ALIYUN_EMAIL_" + System.currentTimeMillis();
            
            return MessageSendResponse.success(messageId);
        } catch (Exception e) {
            log.error("发送阿里云简单邮件失败", e);
            return MessageSendResponse.failure("ALIYUN_SIMPLE_ERROR", "发送阿里云简单邮件失败: " + e.getMessage());
        }
    }
    
    @Override
    public MessageSendResponse sendHtmlEmail(String to, String subject, String htmlContent) {
        try {
            log.info("发送阿里云HTML邮件: to={}, subject={}", to, subject);
            
            // TODO: 集成阿里云邮件SDK with HTML support
            String messageId = "ALIYUN_HTML_" + System.currentTimeMillis();
            
            return MessageSendResponse.success(messageId);
        } catch (Exception e) {
            log.error("发送阿里云HTML邮件失败", e);
            return MessageSendResponse.failure("ALIYUN_HTML_ERROR", "发送阿里云HTML邮件失败: " + e.getMessage());
        }
    }
    
    @Override
    public MessageSendResponse sendEmailWithAttachment(String to, String subject, String content, String attachmentPath) {
        try {
            log.info("发送阿里云带附件邮件: to={}, subject={}, attachment={}", to, subject, attachmentPath);
            
            // TODO: 集成阿里云邮件SDK with attachment support
            String messageId = "ALIYUN_ATTACHMENT_" + System.currentTimeMillis();
            
            return MessageSendResponse.success(messageId);
        } catch (Exception e) {
            log.error("发送阿里云带附件邮件失败", e);
            return MessageSendResponse.failure("ALIYUN_ATTACHMENT_ERROR", "发送阿里云带附件邮件失败: " + e.getMessage());
        }
    }
    
    @Override
    public MessageSendResponse sendTemplateEmail(String to, String subject, String templateId, Map<String, Object> templateParams) {
        try {
            log.info("发送阿里云模板邮件: to={}, subject={}, templateId={}", to, subject, templateId);
            
            // TODO: 集成阿里云邮件SDK with template support
            String messageId = "ALIYUN_TEMPLATE_" + System.currentTimeMillis();
            
            return MessageSendResponse.success(messageId);
        } catch (Exception e) {
            log.error("发送阿里云模板邮件失败", e);
            return MessageSendResponse.failure("ALIYUN_TEMPLATE_ERROR", "发送阿里云模板邮件失败: " + e.getMessage());
        }
    }
    
    @Override
    public String getPlatformType() {
        return PlatformType.ALIYUN_EMAIL.name();
    }
    
    @Override
    public String getMessageType() {
        return MessageType.EMAIL.name();
    }
    
    @Override
    public boolean supports(String platformType, String messageType) {
        return PlatformType.ALIYUN_EMAIL.name().equals(platformType) && MessageType.EMAIL.name().equals(messageType);
    }
} 