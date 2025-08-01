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
 * 阿里云短信发送器
 */
@Service
public class AliyunSmsSender implements SmsSender {
    
    private static final Logger log = LoggerFactory.getLogger(AliyunSmsSender.class);
    
    @Override
    public MessageSendResponse send(MessageSendRequest request) {
        try {
            log.info("阿里云短信发送: {}", request);
            
            String phoneNumber = request.getReceiver();
            String templateCode = request.getTemplateId();
            Map<String, Object> templateParams = request.getTemplateParams();
            
            return sendSms(phoneNumber, templateCode, templateParams);
        } catch (Exception e) {
            log.error("阿里云短信发送失败", e);
            return MessageSendResponse.failure("ALIYUN_SMS_ERROR", "阿里云短信发送失败: " + e.getMessage());
        }
    }
    
    @Override
    public MessageSendResponse sendSms(String phoneNumber, String templateCode, Map<String, Object> templateParams) {
        try {
            log.info("发送阿里云短信: phoneNumber={}, templateCode={}, params={}", phoneNumber, templateCode, templateParams);
            
            // TODO: 集成阿里云短信SDK
            // 这里模拟发送成功
            String messageId = "ALIYUN_SMS_" + System.currentTimeMillis();
            
            return MessageSendResponse.success(messageId);
        } catch (Exception e) {
            log.error("发送阿里云短信失败", e);
            return MessageSendResponse.failure("ALIYUN_SMS_ERROR", "发送阿里云短信失败: " + e.getMessage());
        }
    }
    
    @Override
    public MessageSendResponse sendVerificationCode(String phoneNumber, String code) {
        try {
            log.info("发送阿里云验证码短信: phoneNumber={}, code={}", phoneNumber, code);
            
            Map<String, Object> params = Map.of("code", code, "expire", "5分钟");
            
            return sendSms(phoneNumber, "SMS_VERIFICATION_CODE", params);
        } catch (Exception e) {
            log.error("发送阿里云验证码短信失败", e);
            return MessageSendResponse.failure("ALIYUN_VERIFICATION_ERROR", "发送阿里云验证码短信失败: " + e.getMessage());
        }
    }
    
    @Override
    public MessageSendResponse sendNotification(String phoneNumber, String content) {
        try {
            log.info("发送阿里云通知短信: phoneNumber={}, content={}", phoneNumber, content);
            
            Map<String, Object> params = Map.of("content", content);
            
            return sendSms(phoneNumber, "SMS_NOTIFICATION", params);
        } catch (Exception e) {
            log.error("发送阿里云通知短信失败", e);
            return MessageSendResponse.failure("ALIYUN_NOTIFICATION_ERROR", "发送阿里云通知短信失败: " + e.getMessage());
        }
    }
    
    @Override
    public String getPlatformType() {
        return PlatformType.ALIYUN_SMS.name();
    }
    
    @Override
    public String getMessageType() {
        return MessageType.SMS.name();
    }
    
    @Override
    public boolean supports(String platformType, String messageType) {
        return PlatformType.ALIYUN_SMS.name().equals(platformType) && MessageType.SMS.name().equals(messageType);
    }
} 