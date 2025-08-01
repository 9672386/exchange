package com.exchange.message.web.controller;

import com.exchange.message.api.dto.MessageSendRequest;
import com.exchange.message.api.dto.MessageSendResponse;
import com.exchange.message.core.service.MessageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.util.List;

/**
 * 消息发送控制器
 */
@RestController
@RequestMapping("/api/message")
@Tag(name = "消息发送", description = "消息发送相关接口")
public class MessageController {
    
    @Autowired
    private MessageService messageService;
    
    @PostMapping("/send")
    @Operation(summary = "发送消息", description = "发送单条消息")
    public MessageSendResponse sendMessage(@Valid @RequestBody MessageSendRequest request) {
        return messageService.sendMessage(request);
    }
    
    @PostMapping("/send/batch")
    @Operation(summary = "批量发送消息", description = "批量发送消息")
    public List<MessageSendResponse> sendBatchMessage(
            @Valid @RequestBody MessageSendRequest request,
            @RequestParam List<String> receivers) {
        return messageService.sendBatchMessage(request, receivers);
    }
    
    @PostMapping("/send/async")
    @Operation(summary = "异步发送消息", description = "异步发送消息")
    public void sendMessageAsync(@Valid @RequestBody MessageSendRequest request) {
        messageService.sendMessageAsync(request);
    }
} 