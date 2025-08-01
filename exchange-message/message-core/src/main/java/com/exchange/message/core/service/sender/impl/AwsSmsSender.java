package com.exchange.message.core.service.sender.impl;

import com.exchange.message.api.dto.MessageSendRequest;
import com.exchange.message.api.dto.MessageSendResponse;
import com.exchange.message.api.enums.MessageType;
import com.exchange.message.api.enums.PlatformType;
import com.exchange.message.core.service.sender.SmsSender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * AWS短信发送器
 */
@Service
public class AwsSmsSender implements SmsSender {
    
    private static final Logger log = LoggerFactory.getLogger(AwsSmsSender.class);
    
    @Override
    public MessageSendResponse send(MessageSendRequest request) {
        try {
            log.info("AWS短信发送: {}", request);
            
            String phoneNumber = request.getReceiver();
            String content = request.getContent();
            
            return sendSms(phoneNumber, "AWS_SMS_TEMPLATE", Map.of("message", content));
        } catch (Exception e) {
            log.error("AWS短信发送失败", e);
            return MessageSendResponse.failure("AWS_SMS_ERROR", "AWS短信发送失败: " + e.getMessage());
        }
    }
    
    @Override
    public MessageSendResponse sendSms(String phoneNumber, String templateId, Map<String, Object> templateParams) {
        try {
            log.info("发送AWS短信: phoneNumber={}, templateId={}, params={}", phoneNumber, templateId, templateParams);
            
            // TODO: 集成AWS SNS SDK
            // 这里模拟发送成功
            String messageId = "AWS_SMS_" + System.currentTimeMillis();
            
            return MessageSendResponse.success(messageId);
        } catch (Exception e) {
            log.error("发送AWS短信失败", e);
            return MessageSendResponse.failure("AWS_SMS_ERROR", "发送AWS短信失败: " + e.getMessage());
        }
    }
    
    @Override
    public MessageSendResponse sendVerificationCode(String phoneNumber, String code) {
        try {
            log.info("发送AWS验证码短信: phoneNumber={}, code={}", phoneNumber, code);
            
            Map<String, Object> params = Map.of("code", code, "expire", "5分钟");
            
            return sendSms(phoneNumber, "AWS_VERIFICATION_CODE", params);
        } catch (Exception e) {
            log.error("发送AWS验证码短信失败", e);
            return MessageSendResponse.failure("AWS_VERIFICATION_ERROR", "发送AWS验证码短信失败: " + e.getMessage());
        }
    }
    
    @Override
    public MessageSendResponse sendNotification(String phoneNumber, String content) {
        try {
            log.info("发送AWS通知短信: phoneNumber={}, content={}", phoneNumber, content);
            
            Map<String, Object> params = Map.of("content", content);
            
            return sendSms(phoneNumber, "AWS_NOTIFICATION", params);
        } catch (Exception e) {
            log.error("发送AWS通知短信失败", e);
            return MessageSendResponse.failure("AWS_NOTIFICATION_ERROR", "发送AWS通知短信失败: " + e.getMessage());
        }
    }
    
    @Override
    public String getPlatformType() {
        return PlatformType.AWS_SNS.name();
    }
    
    @Override
    public String getMessageType() {
        return MessageType.SMS.name();
    }
    
    @Override
    public boolean supports(String platformType, String messageType) {
        return PlatformType.AWS_SNS.name().equals(platformType) && MessageType.SMS.name().equals(messageType);
    }
} 