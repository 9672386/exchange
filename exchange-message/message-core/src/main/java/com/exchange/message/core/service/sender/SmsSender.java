package com.exchange.message.core.service.sender;

import com.exchange.message.api.dto.MessageSendResponse;

import java.util.Map;

/**
 * SMS发送器接口
 */
public interface SmsSender extends MessageSender {
    
    /**
     * 发送短信
     * @param phoneNumber 手机号
     * @param templateCode 模板代码
     * @param templateParams 模板参数
     * @return 发送响应
     */
    MessageSendResponse sendSms(String phoneNumber, String templateCode, Map<String, Object> templateParams);
    
    /**
     * 发送验证码短信
     * @param phoneNumber 手机号
     * @param code 验证码
     * @return 发送响应
     */
    MessageSendResponse sendVerificationCode(String phoneNumber, String code);
    
    /**
     * 发送通知短信
     * @param phoneNumber 手机号
     * @param content 短信内容
     * @return 发送响应
     */
    MessageSendResponse sendNotification(String phoneNumber, String content);
} 