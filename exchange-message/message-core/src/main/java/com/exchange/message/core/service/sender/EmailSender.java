package com.exchange.message.core.service.sender;

import com.exchange.message.api.dto.MessageSendResponse;

import java.util.Map;

/**
 * Email发送器接口
 */
public interface EmailSender extends MessageSender {
    
    /**
     * 发送简单邮件
     * @param to 收件人
     * @param subject 主题
     * @param content 内容
     * @return 发送响应
     */
    MessageSendResponse sendSimpleEmail(String to, String subject, String content);
    
    /**
     * 发送HTML邮件
     * @param to 收件人
     * @param subject 主题
     * @param htmlContent HTML内容
     * @return 发送响应
     */
    MessageSendResponse sendHtmlEmail(String to, String subject, String htmlContent);
    
    /**
     * 发送带附件的邮件
     * @param to 收件人
     * @param subject 主题
     * @param content 内容
     * @param attachmentPath 附件路径
     * @return 发送响应
     */
    MessageSendResponse sendEmailWithAttachment(String to, String subject, String content, String attachmentPath);
    
    /**
     * 发送模板邮件
     * @param to 收件人
     * @param subject 主题
     * @param templateId 模板ID
     * @param templateParams 模板参数
     * @return 发送响应
     */
    MessageSendResponse sendTemplateEmail(String to, String subject, String templateId, Map<String, Object> templateParams);
} 