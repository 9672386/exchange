package com.exchange.message.core.service.impl;

import com.exchange.message.api.dto.MessageSendRequest;
import com.exchange.message.api.dto.MessageSendResponse;
import com.exchange.message.api.enums.MessageType;
import com.exchange.message.core.service.MessageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * 消息服务实现
 */
@Service
public class MessageServiceImpl implements MessageService {
    
    private static final Logger log = LoggerFactory.getLogger(MessageServiceImpl.class);
    
    @Autowired
    private SmsServiceImpl smsService;
    
    @Autowired
    private EmailServiceImpl emailService;
    
    @Autowired
    private TelegramServiceImpl telegramService;
    
    @Autowired
    private LarkServiceImpl larkService;
    
    @Override
    public MessageSendResponse sendMessage(MessageSendRequest request) {
        try {
            log.info("发送消息: {}", request);
            
            switch (request.getMessageType()) {
                case SMS:
                    return smsService.sendSms(request);
                case EMAIL:
                    return emailService.sendEmail(request);
                case TELEGRAM:
                    return telegramService.sendTelegramMessage(request);
                case LARK:
                    return larkService.sendLarkMessage(request);
                default:
                    return MessageSendResponse.failure("UNSUPPORTED_MESSAGE_TYPE", "不支持的消息类型: " + request.getMessageType());
            }
        } catch (Exception e) {
            log.error("发送消息失败", e);
            return MessageSendResponse.failure("MESSAGE_SEND_ERROR", "发送消息失败: " + e.getMessage());
        }
    }
    
    @Override
    public List<MessageSendResponse> sendBatchMessage(MessageSendRequest request, List<String> receivers) {
        log.info("批量发送消息: receivers={}", receivers);
        
        return receivers.stream()
                .map(receiver -> {
                    MessageSendRequest batchRequest = new MessageSendRequest();
                    batchRequest.setMessageType(request.getMessageType());
                    batchRequest.setPlatformType(request.getPlatformType());
                    batchRequest.setBusinessType(request.getBusinessType());
                    batchRequest.setTemplateId(request.getTemplateId());
                    batchRequest.setTemplateParams(request.getTemplateParams());
                    batchRequest.setReceiver(receiver);
                    batchRequest.setContent(request.getContent());
                    batchRequest.setSubject(request.getSubject());
                    batchRequest.setBusinessId(request.getBusinessId());
                    
                    return sendMessage(batchRequest);
                })
                .collect(java.util.stream.Collectors.toList());
    }
    
    @Override
    @Async
    public void sendMessageAsync(MessageSendRequest request) {
        try {
            log.info("异步发送消息: {}", request);
            MessageSendResponse response = sendMessage(request);
            log.info("异步发送消息完成: messageId={}, success={}", response.getMessageId(), response.getSuccess());
        } catch (Exception e) {
            log.error("异步发送消息失败", e);
        }
    }
    
    @Override
    public CompletableFuture<MessageSendResponse> sendMessageAsyncWithResult(MessageSendRequest request) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                log.info("异步发送消息(带结果): {}", request);
                return sendMessage(request);
            } catch (Exception e) {
                log.error("异步发送消息失败", e);
                return MessageSendResponse.failure("ASYNC_SEND_ERROR", "异步发送消息失败: " + e.getMessage());
            }
        });
    }
} 