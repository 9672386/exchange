package com.exchange.message.core.service.manager;

import com.exchange.message.api.dto.MessageSendRequest;
import com.exchange.message.api.dto.MessageSendResponse;
import com.exchange.message.api.enums.MessageType;
import com.exchange.message.api.enums.PlatformType;
import com.exchange.message.core.service.factory.MessageSenderFactory;
import com.exchange.message.core.service.sender.MessageSender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 消息发送器管理器
 * 提供负载均衡、故障转移、重试等功能
 */
@Component
public class MessageSenderManager {
    
    private static final Logger log = LoggerFactory.getLogger(MessageSenderManager.class);
    
    @Autowired
    private MessageSenderFactory messageSenderFactory;
    
    // 平台计数器，用于负载均衡
    private final Map<String, AtomicInteger> platformCounters = new ConcurrentHashMap<>();
    
    // 平台故障记录
    private final Map<String, Long> platformFailureTime = new ConcurrentHashMap<>();
    
    // 故障恢复时间（毫秒）
    private static final long FAILURE_RECOVERY_TIME = 5 * 60 * 1000; // 5分钟
    
    /**
     * 发送消息（带故障转移）
     * @param request 消息发送请求
     * @return 发送响应
     */
    public MessageSendResponse sendMessageWithFailover(MessageSendRequest request) {
        try {
            log.info("发送消息（带故障转移）: {}", request);
            
            // 尝试主要平台
            MessageSendResponse response = messageSenderFactory.sendMessage(request);
            if (response.getSuccess()) {
                return response;
            }
            
            // 主要平台失败，尝试备用平台
            log.warn("主要平台发送失败，尝试备用平台: {}", request.getPlatformType());
            return sendMessageWithBackupPlatform(request);
            
        } catch (Exception e) {
            log.error("发送消息失败", e);
            return MessageSendResponse.failure("SEND_FAILOVER_ERROR", "发送消息失败: " + e.getMessage());
        }
    }
    
    /**
     * 发送消息（带负载均衡）
     * @param request 消息发送请求
     * @return 发送响应
     */
    public MessageSendResponse sendMessageWithLoadBalance(MessageSendRequest request) {
        try {
            log.info("发送消息（带负载均衡）: {}", request);
            
            // 获取所有支持的平台
            List<MessageSender> senders = getAvailableSenders(request.getMessageType());
            if (senders.isEmpty()) {
                return MessageSendResponse.failure("NO_AVAILABLE_SENDER", "没有可用的发送器");
            }
            
            // 选择负载最低的平台
            MessageSender selectedSender = selectLowestLoadSender(senders);
            log.info("选择发送器: {}", selectedSender.getClass().getSimpleName());
            
            return selectedSender.send(request);
            
        } catch (Exception e) {
            log.error("发送消息失败", e);
            return MessageSendResponse.failure("SEND_LOADBALANCE_ERROR", "发送消息失败: " + e.getMessage());
        }
    }
    
    /**
     * 发送消息（带重试）
     * @param request 消息发送请求
     * @param maxRetries 最大重试次数
     * @return 发送响应
     */
    public MessageSendResponse sendMessageWithRetry(MessageSendRequest request, int maxRetries) {
        Exception lastException = null;
        
        for (int i = 0; i <= maxRetries; i++) {
            try {
                log.info("发送消息（第{}次尝试）: {}", i + 1, request);
                
                MessageSendResponse response = messageSenderFactory.sendMessage(request);
                if (response.getSuccess()) {
                    return response;
                }
                
                log.warn("发送失败，准备重试: {}", response.getErrorMessage());
                
            } catch (Exception e) {
                lastException = e;
                log.warn("发送异常，准备重试: {}", e.getMessage());
                
                // 记录平台故障
                recordPlatformFailure(request.getPlatformType().name());
            }
            
            // 等待一段时间后重试
            if (i < maxRetries) {
                try {
                    Thread.sleep(1000 * (i + 1)); // 递增等待时间
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
        
        log.error("发送消息失败，已重试{}次", maxRetries);
        return MessageSendResponse.failure("SEND_RETRY_ERROR", 
            "发送消息失败，已重试" + maxRetries + "次: " + (lastException != null ? lastException.getMessage() : "未知错误"));
    }
    
    /**
     * 获取可用的发送器
     * @param messageType 消息类型
     * @return 发送器列表
     */
    public List<MessageSender> getAvailableSenders(MessageType messageType) {
        return messageSenderFactory.getAllSenders().stream()
                .filter(sender -> sender.getMessageType().equals(messageType.name()))
                .filter(this::isPlatformAvailable)
                .toList();
    }
    
    /**
     * 检查平台是否可用
     * @param sender 发送器
     * @return 是否可用
     */
    private boolean isPlatformAvailable(MessageSender sender) {
        String platformKey = sender.getPlatformType();
        Long failureTime = platformFailureTime.get(platformKey);
        
        if (failureTime == null) {
            return true;
        }
        
        // 检查是否已过故障恢复时间
        long currentTime = System.currentTimeMillis();
        if (currentTime - failureTime > FAILURE_RECOVERY_TIME) {
            platformFailureTime.remove(platformKey);
            log.info("平台已恢复: {}", platformKey);
            return true;
        }
        
        log.debug("平台仍在故障期: {}", platformKey);
        return false;
    }
    
    /**
     * 选择负载最低的发送器
     * @param senders 发送器列表
     * @return 选中的发送器
     */
    private MessageSender selectLowestLoadSender(List<MessageSender> senders) {
        MessageSender selectedSender = senders.get(0);
        int minLoad = Integer.MAX_VALUE;
        
        for (MessageSender sender : senders) {
            String platformKey = sender.getPlatformType();
            AtomicInteger counter = platformCounters.computeIfAbsent(platformKey, k -> new AtomicInteger(0));
            int currentLoad = counter.get();
            
            if (currentLoad < minLoad) {
                minLoad = currentLoad;
                selectedSender = sender;
            }
        }
        
        // 增加选中平台的计数
        String selectedPlatform = selectedSender.getPlatformType();
        platformCounters.computeIfAbsent(selectedPlatform, k -> new AtomicInteger(0)).incrementAndGet();
        
        return selectedSender;
    }
    
    /**
     * 使用备用平台发送消息
     * @param request 消息发送请求
     * @return 发送响应
     */
    private MessageSendResponse sendMessageWithBackupPlatform(MessageSendRequest request) {
        // 根据消息类型选择备用平台
        PlatformType backupPlatform = getBackupPlatform(request.getMessageType(), request.getPlatformType());
        
        if (backupPlatform == null) {
            return MessageSendResponse.failure("NO_BACKUP_PLATFORM", "没有可用的备用平台");
        }
        
        // 创建备用请求
        MessageSendRequest backupRequest = new MessageSendRequest();
        backupRequest.setMessageType(request.getMessageType());
        backupRequest.setPlatformType(backupPlatform);
        backupRequest.setBusinessType(request.getBusinessType());
        backupRequest.setTemplateId(request.getTemplateId());
        backupRequest.setTemplateParams(request.getTemplateParams());
        backupRequest.setReceiver(request.getReceiver());
        backupRequest.setContent(request.getContent());
        backupRequest.setSubject(request.getSubject());
        backupRequest.setBusinessId(request.getBusinessId());
        
        log.info("使用备用平台发送: {}", backupPlatform);
        return messageSenderFactory.sendMessage(backupRequest);
    }
    
    /**
     * 获取备用平台
     * @param messageType 消息类型
     * @param currentPlatform 当前平台
     * @return 备用平台
     */
    private PlatformType getBackupPlatform(MessageType messageType, PlatformType currentPlatform) {
        switch (messageType) {
            case SMS:
                if (PlatformType.ALIYUN_SMS.equals(currentPlatform)) {
                    return PlatformType.TENCENT_SMS;
                } else if (PlatformType.TENCENT_SMS.equals(currentPlatform)) {
                    return PlatformType.AWS_SNS;
                } else if (PlatformType.AWS_SNS.equals(currentPlatform)) {
                    return PlatformType.ALIYUN_SMS;
                }
                break;
            case EMAIL:
                if (PlatformType.ALIYUN_EMAIL.equals(currentPlatform)) {
                    return PlatformType.SMTP;
                } else if (PlatformType.SMTP.equals(currentPlatform)) {
                    return PlatformType.TENCENT_EMAIL;
                } else if (PlatformType.TENCENT_EMAIL.equals(currentPlatform)) {
                    return PlatformType.AWS_SES;
                } else if (PlatformType.AWS_SES.equals(currentPlatform)) {
                    return PlatformType.ALIYUN_EMAIL;
                }
                break;
        }
        return null;
    }
    
    /**
     * 记录平台故障
     * @param platformType 平台类型
     */
    private void recordPlatformFailure(String platformType) {
        platformFailureTime.put(platformType, System.currentTimeMillis());
        log.warn("记录平台故障: {}", platformType);
    }
    
    /**
     * 获取平台统计信息
     * @return 统计信息
     */
    public Map<String, Object> getPlatformStatistics() {
        Map<String, Object> stats = new ConcurrentHashMap<>();
        
        // 平台计数器
        Map<String, Integer> counters = new ConcurrentHashMap<>();
        platformCounters.forEach((platform, counter) -> counters.put(platform, counter.get()));
        stats.put("platformCounters", counters);
        
        // 平台故障时间
        Map<String, Long> failures = new ConcurrentHashMap<>(platformFailureTime);
        stats.put("platformFailures", failures);
        
        // 可用发送器数量
        stats.put("availableSenders", messageSenderFactory.getAllSenders().size());
        
        return stats;
    }
} 