package com.exchange.message.core.service.impl;

import com.exchange.message.api.dto.MessageSendRequest;
import com.exchange.message.api.dto.MessageSendResponse;
import com.exchange.message.api.enums.PlatformType;
import com.exchange.message.core.service.SmsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * SMS服务实现
 */
@Service
public class SmsServiceImpl implements SmsService {
    
    private static final Logger log = LoggerFactory.getLogger(SmsServiceImpl.class);
    
    @Override
    public MessageSendResponse sendSms(MessageSendRequest request) {
        try {
            log.info("发送短信: {}", request);
            
            switch (request.getPlatformType()) {
                case ALIYUN_SMS:
                    return sendAliyunSms(request.getReceiver(), request.getTemplateId(), request.getTemplateParams());
                case TENCENT_SMS:
                    return sendTencentSms(request.getReceiver(), request.getTemplateId(), request.getTemplateParams());
                case AWS_SNS:
                    return sendAwsSms(request.getReceiver(), request.getContent());
                default:
                    return MessageSendResponse.failure("UNSUPPORTED_PLATFORM", "不支持的短信平台: " + request.getPlatformType());
            }
        } catch (Exception e) {
            log.error("发送短信失败", e);
            return MessageSendResponse.failure("SMS_SEND_ERROR", "发送短信失败: " + e.getMessage());
        }
    }
    
    @Override
    public MessageSendResponse sendAliyunSms(String phoneNumber, String templateCode, Map<String, Object> templateParams) {
        try {
            log.info("发送阿里云短信: phoneNumber={}, templateCode={}, params={}", phoneNumber, templateCode, templateParams);
            
            // TODO: 集成阿里云短信SDK
            // 这里模拟发送成功
            String messageId = "ALIYUN_" + System.currentTimeMillis();
            
            return MessageSendResponse.success(messageId);
        } catch (Exception e) {
            log.error("发送阿里云短信失败", e);
            return MessageSendResponse.failure("ALIYUN_SMS_ERROR", "发送阿里云短信失败: " + e.getMessage());
        }
    }
    
    @Override
    public MessageSendResponse sendTencentSms(String phoneNumber, String templateId, Map<String, Object> templateParams) {
        try {
            log.info("发送腾讯云短信: phoneNumber={}, templateId={}, params={}", phoneNumber, templateId, templateParams);
            
            // TODO: 集成腾讯云短信SDK
            // 这里模拟发送成功
            String messageId = "TENCENT_" + System.currentTimeMillis();
            
            return MessageSendResponse.success(messageId);
        } catch (Exception e) {
            log.error("发送腾讯云短信失败", e);
            return MessageSendResponse.failure("TENCENT_SMS_ERROR", "发送腾讯云短信失败: " + e.getMessage());
        }
    }
    
    @Override
    public MessageSendResponse sendAwsSms(String phoneNumber, String message) {
        try {
            log.info("发送AWS短信: phoneNumber={}, message={}", phoneNumber, message);
            
            // TODO: 集成AWS SNS SDK
            // 这里模拟发送成功
            String messageId = "AWS_" + System.currentTimeMillis();
            
            return MessageSendResponse.success(messageId);
        } catch (Exception e) {
            log.error("发送AWS短信失败", e);
            return MessageSendResponse.failure("AWS_SMS_ERROR", "发送AWS短信失败: " + e.getMessage());
        }
    }
} 