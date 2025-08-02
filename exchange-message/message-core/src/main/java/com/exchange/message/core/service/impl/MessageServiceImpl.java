package com.exchange.message.core.service.impl;

import com.exchange.message.api.dto.MessageSendRequest;
import com.exchange.message.api.dto.MessageSendResponse;
import com.exchange.message.api.enums.MessageType;
import com.exchange.message.core.service.MessageService;
import com.exchange.message.core.service.factory.MessageSenderFactory;
import com.exchange.message.core.service.MultiLanguageTemplateService;
import com.exchange.message.exception.MessageBusinessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * 消息服务实现
 */
@Service
public class MessageServiceImpl implements MessageService {
    
    private static final Logger log = LoggerFactory.getLogger(MessageServiceImpl.class);
    
    @Autowired
    private MessageSenderFactory messageSenderFactory;
    
    @Autowired
    private MultiLanguageTemplateService multiLanguageTemplateService;
    
    @Override
    public MessageSendResponse sendMessage(MessageSendRequest request) {
        try {
            log.info("发送消息: {}", request);
            
            // 参数验证
            if (request.getReceiver() == null || request.getReceiver().trim().isEmpty()) {
                throw MessageBusinessException.messageRecipientInvalid();
            }
            
            if (request.getMessageType() == null) {
                throw MessageBusinessException.messageContentInvalid();
            }
            
            // 如果指定了模板ID，进行多语言模板渲染
            if (request.getTemplateId() != null && !request.getTemplateId().trim().isEmpty()) {
                String languageCode = request.getLanguageCode() != null ? request.getLanguageCode() : "zh_CN";
                
                // 渲染模板内容
                String renderedContent = multiLanguageTemplateService.renderTemplate(
                    request.getTemplateId(), 
                    languageCode, 
                    request.getTemplateParams()
                );
                request.setContent(renderedContent);
                
                // 渲染模板标题
                String renderedSubject = multiLanguageTemplateService.renderTemplateSubject(
                    request.getTemplateId(), 
                    languageCode, 
                    request.getTemplateParams()
                );
                if (renderedSubject != null && !renderedSubject.trim().isEmpty()) {
                    request.setSubject(renderedSubject);
                }
            }
            
            return messageSenderFactory.sendMessage(request);
        } catch (MessageBusinessException e) {
            log.error("消息发送业务异常: {}", e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("消息发送失败", e);
            throw MessageBusinessException.messageSendFailed();
        }
    }
    
    @Override
    public List<MessageSendResponse> sendBatchMessage(MessageSendRequest request, List<String> receivers) {
        try {
            log.info("批量发送消息: {}, receivers: {}", request, receivers);
            
            if (receivers == null || receivers.isEmpty()) {
                throw MessageBusinessException.messageRecipientInvalid();
            }
            
            // 批量发送逻辑
            return receivers.stream()
                    .map(receiver -> {
                        MessageSendRequest batchRequest = new MessageSendRequest();
                        batchRequest.setReceiver(receiver);
                        batchRequest.setMessageType(request.getMessageType());
                        batchRequest.setPlatformType(request.getPlatformType());
                        batchRequest.setBusinessType(request.getBusinessType());
                        batchRequest.setTemplateId(request.getTemplateId());
                        batchRequest.setTemplateParams(request.getTemplateParams());
                        batchRequest.setContent(request.getContent());
                        batchRequest.setSubject(request.getSubject());
                        batchRequest.setBusinessId(request.getBusinessId());
                        
                        return sendMessage(batchRequest);
                    })
                    .collect(Collectors.toList());
        } catch (MessageBusinessException e) {
            log.error("批量消息发送业务异常: {}", e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("批量消息发送失败", e);
            throw MessageBusinessException.messageBatchSendFailed();
        }
    }
    
    @Override
    @Async
    public void sendMessageAsync(MessageSendRequest request) {
        try {
            log.info("异步发送消息: {}", request);
            sendMessage(request);
        } catch (Exception e) {
            log.error("异步消息发送失败", e);
            // 异步方法中不抛出异常，只记录日志
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
                throw MessageBusinessException.messageSendFailed();
            }
        });
    }
} 