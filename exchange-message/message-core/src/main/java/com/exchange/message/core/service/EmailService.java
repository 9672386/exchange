package com.exchange.message.core.service;

import com.exchange.message.api.dto.MessageSendRequest;
import com.exchange.message.api.dto.MessageSendResponse;

/**
 * 邮件发送服务接口
 */
public interface EmailService {
    
    /**
     * 发送邮件
     * @param request 消息发送请求
     * @return 消息发送响应
     */
    MessageSendResponse sendEmail(MessageSendRequest request);
    
    /**
     * 发送简单邮件
     * @param to 收件人
     * @param subject 主题
     * @param content 内容
     * @return 消息发送响应
     */
    MessageSendResponse sendSimpleEmail(String to, String subject, String content);
    
    /**
     * 发送HTML邮件
     * @param to 收件人
     * @param subject 主题
     * @param htmlContent HTML内容
     * @return 消息发送响应
     */
    MessageSendResponse sendHtmlEmail(String to, String subject, String htmlContent);
    
    /**
     * 发送带附件的邮件
     * @param to 收件人
     * @param subject 主题
     * @param content 内容
     * @param attachmentPath 附件路径
     * @return 消息发送响应
     */
    MessageSendResponse sendEmailWithAttachment(String to, String subject, String content, String attachmentPath);
} 