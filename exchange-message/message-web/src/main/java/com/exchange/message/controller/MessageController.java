package com.exchange.message.controller;

import com.exchange.common.response.ApiResponse;
import com.exchange.common.response.ResponseUtils;
import com.exchange.message.api.dto.MessageSendRequest;
import com.exchange.message.api.dto.MessageSendResponse;
import com.exchange.message.core.service.MessageService;
import com.exchange.message.core.service.BusinessMessageService;
import com.exchange.message.api.enums.BusinessType;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
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
    public ApiResponse<MessageSendResponse> sendMessage(@Valid @RequestBody MessageSendRequest request) {
        try {
            MessageSendResponse response = messageService.sendMessage(request);
            if (response.getSuccess()) {
                return ResponseUtils.success("消息发送成功", response);
            } else {
                return ResponseUtils.error(8000, "消息发送失败", response);
            }
        } catch (Exception e) {
            return ResponseUtils.error(8000, "消息发送失败: " + e.getMessage());
        }
    }
    
    @PostMapping("/send/batch")
    @Operation(summary = "批量发送消息", description = "批量发送消息")
    public ApiResponse<List<MessageSendResponse>> sendBatchMessage(
            @Valid @RequestBody MessageSendRequest request,
            @RequestParam List<String> receivers) {
        try {
            List<MessageSendResponse> responses = messageService.sendBatchMessage(request, receivers);
            return ResponseUtils.success("批量消息发送成功", responses);
        } catch (Exception e) {
            return ResponseUtils.error(8014, "批量消息发送失败: " + e.getMessage());
        }
    }
    
    @PostMapping("/send/async")
    @Operation(summary = "异步发送消息", description = "异步发送消息")
    public ApiResponse<Void> sendMessageAsync(@Valid @RequestBody MessageSendRequest request) {
        try {
            messageService.sendMessageAsync(request);
            return ResponseUtils.success();
        } catch (Exception e) {
            return ResponseUtils.error(8000, "异步消息发送失败: " + e.getMessage());
        }
    }
    
    @PostMapping("/business/send")
    @Operation(summary = "发送业务消息", description = "根据业务类型发送消息")
    public ApiResponse<MessageSendResponse> sendBusinessMessage(
            @RequestParam BusinessType businessType,
            @RequestParam String receiver,
            @Parameter(description = "语言代码", example = "zh_CN")
            @RequestParam(defaultValue = "zh_CN") String languageCode,
            @RequestBody java.util.Map<String, Object> params) {
        try {
            // 将语言代码添加到参数中
            params.put("languageCode", languageCode);
            
            MessageSendResponse response = businessMessageService.sendBusinessMessage(businessType, receiver, params);
            if (response.getSuccess()) {
                return ResponseUtils.success("业务消息发送成功", response);
            } else {
                return ResponseUtils.error(8000, "业务消息发送失败", response);
            }
        } catch (Exception e) {
            return ResponseUtils.error(8000, "业务消息发送失败: " + e.getMessage());
        }
    }
    
    @PostMapping("/business/user/register")
    @Operation(summary = "发送用户注册消息", description = "发送用户注册验证码")
    public ApiResponse<MessageSendResponse> sendUserRegisterMessage(
            @RequestParam String phoneNumber,
            @RequestParam String verificationCode) {
        try {
            MessageSendResponse response = businessMessageService.sendUserRegisterMessage(phoneNumber, verificationCode);
            if (response.getSuccess()) {
                return ResponseUtils.success("用户注册消息发送成功", response);
            } else {
                return ResponseUtils.error(8000, "用户注册消息发送失败", response);
            }
        } catch (Exception e) {
            return ResponseUtils.error(8000, "用户注册消息发送失败: " + e.getMessage());
        }
    }
    
    @PostMapping("/business/user/login")
    @Operation(summary = "发送用户登录消息", description = "发送用户登录通知")
    public ApiResponse<MessageSendResponse> sendUserLoginMessage(
            @RequestParam String phoneNumber,
            @RequestParam String loginTime,
            @RequestParam String loginLocation) {
        try {
            MessageSendResponse response = businessMessageService.sendUserLoginMessage(phoneNumber, loginTime, loginLocation);
            if (response.getSuccess()) {
                return ResponseUtils.success("用户登录消息发送成功", response);
            } else {
                return ResponseUtils.error(8000, "用户登录消息发送失败", response);
            }
        } catch (Exception e) {
            return ResponseUtils.error(8000, "用户登录消息发送失败: " + e.getMessage());
        }
    }
    
    @PostMapping("/business/order/created")
    @Operation(summary = "发送订单创建消息", description = "发送订单创建通知")
    public ApiResponse<MessageSendResponse> sendOrderCreatedMessage(
            @RequestParam String userId,
            @RequestParam String orderId,
            @RequestParam String amount) {
        try {
            MessageSendResponse response = businessMessageService.sendOrderCreatedMessage(userId, orderId, amount);
            if (response.getSuccess()) {
                return ResponseUtils.success("订单创建消息发送成功", response);
            } else {
                return ResponseUtils.error(8000, "订单创建消息发送失败", response);
            }
        } catch (Exception e) {
            return ResponseUtils.error(8000, "订单创建消息发送失败: " + e.getMessage());
        }
    }
    
    @PostMapping("/business/trade/executed")
    @Operation(summary = "发送交易执行消息", description = "发送交易执行通知")
    public ApiResponse<MessageSendResponse> sendTradeExecutedMessage(
            @RequestParam String userId,
            @RequestParam String tradeId,
            @RequestParam String symbol,
            @RequestParam String quantity,
            @RequestParam String price) {
        try {
            MessageSendResponse response = businessMessageService.sendTradeExecutedMessage(userId, tradeId, symbol, quantity, price);
            if (response.getSuccess()) {
                return ResponseUtils.success("交易执行消息发送成功", response);
            } else {
                return ResponseUtils.error(8000, "交易执行消息发送失败", response);
            }
        } catch (Exception e) {
            return ResponseUtils.error(8000, "交易执行消息发送失败: " + e.getMessage());
        }
    }
    
    @PostMapping("/business/risk/alert")
    @Operation(summary = "发送风控预警消息", description = "发送风控预警通知")
    public ApiResponse<MessageSendResponse> sendRiskAlertMessage(
            @RequestParam String userId,
            @RequestParam String riskType,
            @RequestParam String riskLevel,
            @RequestParam String description) {
        try {
            MessageSendResponse response = businessMessageService.sendRiskAlertMessage(userId, riskType, riskLevel, description);
            if (response.getSuccess()) {
                return ResponseUtils.success("风控预警消息发送成功", response);
            } else {
                return ResponseUtils.error(8000, "风控预警消息发送失败", response);
            }
        } catch (Exception e) {
            return ResponseUtils.error(8000, "风控预警消息发送失败: " + e.getMessage());
        }
    }
} 