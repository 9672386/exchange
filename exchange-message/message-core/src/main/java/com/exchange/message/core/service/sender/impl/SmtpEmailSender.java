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
 * SMTP邮件发送器
 */
@Service
public class SmtpEmailSender implements EmailSender {
    
    private static final Logger log = LoggerFactory.getLogger(SmtpEmailSender.class);
    
    @Override
    public MessageSendResponse send(MessageSendRequest request) {
        try {
            log.info("SMTP邮件发送: {}", request);
            
            String to = request.getReceiver();
            String subject = request.getSubject();
            String content = request.getContent();
            
            if (content != null && content.contains("<html>")) {
                return sendHtmlEmail(to, subject, content);
            } else {
                return sendSimpleEmail(to, subject, content);
            }
        } catch (Exception e) {
            log.error("SMTP邮件发送失败", e);
            return MessageSendResponse.failure("SMTP_EMAIL_ERROR", "SMTP邮件发送失败: " + e.getMessage());
        }
    }
    
    @Override
    public MessageSendResponse sendSimpleEmail(String to, String subject, String content) {
        try {
            log.info("发送SMTP简单邮件: to={}, subject={}", to, subject);
            
            // TODO: 集成Spring Mail
            String messageId = "SMTP_EMAIL_" + System.currentTimeMillis();
            
            return MessageSendResponse.success(messageId);
        } catch (Exception e) {
            log.error("发送SMTP简单邮件失败", e);
            return MessageSendResponse.failure("SMTP_SIMPLE_ERROR", "发送SMTP简单邮件失败: " + e.getMessage());
        }
    }
    
    @Override
    public MessageSendResponse sendHtmlEmail(String to, String subject, String htmlContent) {
        try {
            log.info("发送SMTP HTML邮件: to={}, subject={}", to, subject);
            
            // TODO: 集成Spring Mail with HTML support
            String messageId = "SMTP_HTML_" + System.currentTimeMillis();
            
            return MessageSendResponse.success(messageId);
        } catch (Exception e) {
            log.error("发送SMTP HTML邮件失败", e);
            return MessageSendResponse.failure("SMTP_HTML_ERROR", "发送SMTP HTML邮件失败: " + e.getMessage());
        }
    }
    
    @Override
    public MessageSendResponse sendEmailWithAttachment(String to, String subject, String content, String attachmentPath) {
        try {
            log.info("发送SMTP带附件邮件: to={}, subject={}, attachment={}", to, subject, attachmentPath);
            
            // TODO: 集成Spring Mail with attachment support
            String messageId = "SMTP_ATTACHMENT_" + System.currentTimeMillis();
            
            return MessageSendResponse.success(messageId);
        } catch (Exception e) {
            log.error("发送SMTP带附件邮件失败", e);
            return MessageSendResponse.failure("SMTP_ATTACHMENT_ERROR", "发送SMTP带附件邮件失败: " + e.getMessage());
        }
    }
    
    @Override
    public MessageSendResponse sendTemplateEmail(String to, String subject, String templateId, Map<String, Object> templateParams) {
        try {
            log.info("发送SMTP模板邮件: to={}, subject={}, templateId={}", to, subject, templateId);
            
            // TODO: 集成Thymeleaf模板引擎
            String messageId = "SMTP_TEMPLATE_" + System.currentTimeMillis();
            
            return MessageSendResponse.success(messageId);
        } catch (Exception e) {
            log.error("发送SMTP模板邮件失败", e);
            return MessageSendResponse.failure("SMTP_TEMPLATE_ERROR", "发送SMTP模板邮件失败: " + e.getMessage());
        }
    }
    
    @Override
    public String getPlatformType() {
        return PlatformType.SMTP.name();
    }
    
    @Override
    public String getMessageType() {
        return MessageType.EMAIL.name();
    }
    
    @Override
    public boolean supports(String platformType, String messageType) {
        return PlatformType.SMTP.name().equals(platformType) && MessageType.EMAIL.name().equals(messageType);
    }
} 