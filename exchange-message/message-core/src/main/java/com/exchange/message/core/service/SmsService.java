package com.exchange.message.core.service;

import com.exchange.message.api.dto.MessageSendRequest;
import com.exchange.message.api.dto.MessageSendResponse;

/**
 * 短信发送服务接口
 */
public interface SmsService {
    
    /**
     * 发送短信
     * @param request 消息发送请求
     * @return 消息发送响应
     */
    MessageSendResponse sendSms(MessageSendRequest request);
    
    /**
     * 发送阿里云短信
     * @param phoneNumber 手机号
     * @param templateCode 模板代码
     * @param templateParams 模板参数
     * @return 消息发送响应
     */
    MessageSendResponse sendAliyunSms(String phoneNumber, String templateCode, java.util.Map<String, String> templateParams);
    
    /**
     * 发送腾讯云短信
     * @param phoneNumber 手机号
     * @param templateId 模板ID
     * @param templateParams 模板参数
     * @return 消息发送响应
     */
    MessageSendResponse sendTencentSms(String phoneNumber, String templateId, java.util.List<String> templateParams);
    
    /**
     * 发送AWS短信
     * @param phoneNumber 手机号
     * @param message 消息内容
     * @return 消息发送响应
     */
    MessageSendResponse sendAwsSms(String phoneNumber, String message);
} 