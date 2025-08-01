package com.exchange.message.core.service.factory;

import com.exchange.message.api.dto.MessageSendRequest;
import com.exchange.message.api.dto.MessageSendResponse;
import com.exchange.message.api.enums.MessageType;
import com.exchange.message.api.enums.PlatformType;
import com.exchange.message.core.service.sender.MessageSender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 消息发送器工厂
 */
@Component
public class MessageSenderFactory {
    
    private static final Logger log = LoggerFactory.getLogger(MessageSenderFactory.class);
    
    private final Map<String, MessageSender> senderMap = new ConcurrentHashMap<>();
    
    @Autowired
    public MessageSenderFactory(List<MessageSender> senders) {
        // 自动注入所有MessageSender实现
        for (MessageSender sender : senders) {
            String key = generateKey(sender.getMessageType(), sender.getPlatformType());
            senderMap.put(key, sender);
            log.info("注册消息发送器: {} -> {}", key, sender.getClass().getSimpleName());
        }
    }
    
    /**
     * 获取消息发送器
     * @param messageType 消息类型
     * @param platformType 平台类型
     * @return 消息发送器
     */
    public MessageSender getSender(String messageType, String platformType) {
        String key = generateKey(messageType, platformType);
        MessageSender sender = senderMap.get(key);
        
        if (sender == null) {
            log.warn("未找到消息发送器: messageType={}, platformType={}", messageType, platformType);
            throw new IllegalArgumentException("不支持的消息类型或平台: " + messageType + "/" + platformType);
        }
        
        return sender;
    }
    
    /**
     * 获取消息发送器
     * @param messageType 消息类型
     * @param platformType 平台类型
     * @return 消息发送器
     */
    public MessageSender getSender(MessageType messageType, PlatformType platformType) {
        return getSender(messageType.name(), platformType.name());
    }
    
    /**
     * 发送消息
     * @param request 消息发送请求
     * @return 发送响应
     */
    public MessageSendResponse sendMessage(MessageSendRequest request) {
        MessageSender sender = getSender(request.getMessageType().name(), request.getPlatformType().name());
        return sender.send(request);
    }
    
    /**
     * 检查是否支持该平台和消息类型
     * @param messageType 消息类型
     * @param platformType 平台类型
     * @return 是否支持
     */
    public boolean supports(String messageType, String platformType) {
        String key = generateKey(messageType, platformType);
        return senderMap.containsKey(key);
    }
    
    /**
     * 获取所有支持的发送器
     * @return 发送器列表
     */
    public List<MessageSender> getAllSenders() {
        return List.copyOf(senderMap.values());
    }
    
    /**
     * 生成发送器键
     * @param messageType 消息类型
     * @param platformType 平台类型
     * @return 键
     */
    private String generateKey(String messageType, String platformType) {
        return messageType + ":" + platformType;
    }
} 