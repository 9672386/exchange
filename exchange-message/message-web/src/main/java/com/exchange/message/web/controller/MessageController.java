package com.exchange.message.web.controller;

import com.exchange.message.api.dto.MessageSendRequest;
import com.exchange.message.api.dto.MessageSendResponse;
import com.exchange.message.core.service.MessageService;
import com.exchange.message.core.service.BusinessMessageService;
import com.exchange.message.api.enums.BusinessType;
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
    
    @Autowired
    private BusinessMessageService businessMessageService;
    
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
    
    @PostMapping("/business/send")
    @Operation(summary = "发送业务消息", description = "根据业务类型发送消息")
    public MessageSendResponse sendBusinessMessage(
            @RequestParam BusinessType businessType,
            @RequestParam String receiver,
            @RequestBody java.util.Map<String, Object> params) {
        return businessMessageService.sendBusinessMessage(businessType, receiver, params);
    }
    
    @PostMapping("/business/user/register")
    @Operation(summary = "发送用户注册消息", description = "发送用户注册验证码")
    public MessageSendResponse sendUserRegisterMessage(
            @RequestParam String phoneNumber,
            @RequestParam String verificationCode) {
        return businessMessageService.sendUserRegisterMessage(phoneNumber, verificationCode);
    }
    
    @PostMapping("/business/user/login")
    @Operation(summary = "发送用户登录消息", description = "发送用户登录通知")
    public MessageSendResponse sendUserLoginMessage(
            @RequestParam String phoneNumber,
            @RequestParam String loginTime,
            @RequestParam String loginLocation) {
        return businessMessageService.sendUserLoginMessage(phoneNumber, loginTime, loginLocation);
    }
    
    @PostMapping("/business/order/created")
    @Operation(summary = "发送订单创建消息", description = "发送订单创建通知")
    public MessageSendResponse sendOrderCreatedMessage(
            @RequestParam String userId,
            @RequestParam String orderId,
            @RequestParam String amount) {
        return businessMessageService.sendOrderCreatedMessage(userId, orderId, amount);
    }
    
    @PostMapping("/business/trade/executed")
    @Operation(summary = "发送交易执行消息", description = "发送交易执行通知")
    public MessageSendResponse sendTradeExecutedMessage(
            @RequestParam String userId,
            @RequestParam String tradeId,
            @RequestParam String symbol,
            @RequestParam String quantity,
            @RequestParam String price) {
        return businessMessageService.sendTradeExecutedMessage(userId, tradeId, symbol, quantity, price);
    }
    
    @PostMapping("/business/risk/alert")
    @Operation(summary = "发送风控预警消息", description = "发送风控预警通知")
    public MessageSendResponse sendRiskAlertMessage(
            @RequestParam String userId,
            @RequestParam String riskType,
            @RequestParam String riskLevel,
            @RequestParam String description) {
        return businessMessageService.sendRiskAlertMessage(userId, riskType, riskLevel, description);
    }
} 