package com.exchange.message.core.service.impl;

import com.exchange.message.api.dto.MessageSendRequest;
import com.exchange.message.api.dto.MessageSendResponse;
import com.exchange.message.api.enums.BusinessType;
import com.exchange.message.api.enums.MessageType;
import com.exchange.message.api.enums.PlatformType;
import com.exchange.message.core.service.BusinessMessageService;
import com.exchange.message.core.service.MessageService;
import com.exchange.message.core.service.MultiLanguageTemplateService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

/**
 * 业务消息服务实现
 */
@Service
public class BusinessMessageServiceImpl implements BusinessMessageService {
    
    private static final Logger log = LoggerFactory.getLogger(BusinessMessageServiceImpl.class);
    
    @Autowired
    private MessageService messageService;
    
    @Autowired
    private MultiLanguageTemplateService multiLanguageTemplateService;
    
    @Override
    public MessageSendResponse sendBusinessMessage(BusinessType businessType, String receiver, Map<String, Object> params) {
        try {
            log.info("发送业务消息: businessType={}, receiver={}", businessType, receiver);
            
            // 从参数中获取语言代码，默认为中文
            String languageCode = "zh_CN";
            if (params != null && params.containsKey("languageCode")) {
                languageCode = params.get("languageCode").toString();
            }
            
            // 根据业务类型构建消息请求
            MessageSendRequest request = buildMessageRequest(businessType, receiver, params, languageCode);
            
            return messageService.sendMessage(request);
        } catch (Exception e) {
            log.error("发送业务消息失败", e);
            return MessageSendResponse.failure("BUSINESS_MESSAGE_ERROR", "发送业务消息失败: " + e.getMessage());
        }
    }
    
    @Override
    public MessageSendResponse sendUserRegisterMessage(String phoneNumber, String verificationCode) {
        try {
            log.info("发送用户注册消息: phoneNumber={}", phoneNumber);
            
            Map<String, Object> params = new HashMap<>();
            params.put("code", verificationCode);
            params.put("expire", "5分钟");
            
            MessageSendRequest request = new MessageSendRequest();
            request.setMessageType(MessageType.SMS);
            request.setPlatformType(PlatformType.ALIYUN_SMS);
            request.setBusinessType(BusinessType.USER_REGISTER);
            request.setTemplateId("SMS_USER_REGISTER");
            request.setTemplateParams(params);
            request.setReceiver(phoneNumber);
            request.setBusinessId("user_register_" + System.currentTimeMillis());
            
            return messageService.sendMessage(request);
        } catch (Exception e) {
            log.error("发送用户注册消息失败", e);
            return MessageSendResponse.failure("USER_REGISTER_ERROR", "发送用户注册消息失败: " + e.getMessage());
        }
    }
    
    @Override
    public MessageSendResponse sendUserLoginMessage(String phoneNumber, String loginTime, String loginLocation) {
        try {
            log.info("发送用户登录消息: phoneNumber={}", phoneNumber);
            
            Map<String, Object> params = new HashMap<>();
            params.put("loginTime", loginTime);
            params.put("loginLocation", loginLocation);
            
            MessageSendRequest request = new MessageSendRequest();
            request.setMessageType(MessageType.SMS);
            request.setPlatformType(PlatformType.ALIYUN_SMS);
            request.setBusinessType(BusinessType.USER_LOGIN);
            request.setTemplateId("SMS_USER_LOGIN");
            request.setTemplateParams(params);
            request.setReceiver(phoneNumber);
            request.setBusinessId("user_login_" + System.currentTimeMillis());
            
            return messageService.sendMessage(request);
        } catch (Exception e) {
            log.error("发送用户登录消息失败", e);
            return MessageSendResponse.failure("USER_LOGIN_ERROR", "发送用户登录消息失败: " + e.getMessage());
        }
    }
    
    @Override
    public MessageSendResponse sendPasswordResetMessage(String email, String resetLink) {
        try {
            log.info("发送密码重置消息: email={}", email);
            
            Map<String, Object> params = new HashMap<>();
            params.put("resetLink", resetLink);
            params.put("expire", "30分钟");
            
            MessageSendRequest request = new MessageSendRequest();
            request.setMessageType(MessageType.EMAIL);
            request.setPlatformType(PlatformType.SMTP);
            request.setBusinessType(BusinessType.USER_PASSWORD_RESET);
            request.setTemplateId("EMAIL_PASSWORD_RESET");
            request.setTemplateParams(params);
            request.setReceiver(email);
            request.setSubject("密码重置");
            request.setBusinessId("password_reset_" + System.currentTimeMillis());
            
            return messageService.sendMessage(request);
        } catch (Exception e) {
            log.error("发送密码重置消息失败", e);
            return MessageSendResponse.failure("PASSWORD_RESET_ERROR", "发送密码重置消息失败: " + e.getMessage());
        }
    }
    
    @Override
    public MessageSendResponse sendOrderCreatedMessage(String userId, String orderId, String amount) {
        try {
            log.info("发送订单创建消息: userId={}, orderId={}", userId, orderId);
            
            Map<String, Object> params = new HashMap<>();
            params.put("orderId", orderId);
            params.put("amount", amount);
            
            MessageSendRequest request = new MessageSendRequest();
            request.setMessageType(MessageType.EMAIL);
            request.setPlatformType(PlatformType.SMTP);
            request.setBusinessType(BusinessType.ORDER_CREATED);
            request.setTemplateId("EMAIL_ORDER_CREATED");
            request.setTemplateParams(params);
            request.setReceiver(userId + "@example.com"); // 实际应该从用户服务获取邮箱
            request.setSubject("订单创建成功");
            request.setBusinessId("order_created_" + orderId);
            
            return messageService.sendMessage(request);
        } catch (Exception e) {
            log.error("发送订单创建消息失败", e);
            return MessageSendResponse.failure("ORDER_CREATED_ERROR", "发送订单创建消息失败: " + e.getMessage());
        }
    }
    
    @Override
    public MessageSendResponse sendOrderPaidMessage(String userId, String orderId, String paymentMethod) {
        try {
            log.info("发送订单支付消息: userId={}, orderId={}", userId, orderId);
            
            Map<String, Object> params = new HashMap<>();
            params.put("orderId", orderId);
            params.put("paymentMethod", paymentMethod);
            
            MessageSendRequest request = new MessageSendRequest();
            request.setMessageType(MessageType.EMAIL);
            request.setPlatformType(PlatformType.SMTP);
            request.setBusinessType(BusinessType.ORDER_PAID);
            request.setTemplateId("EMAIL_ORDER_PAID");
            request.setTemplateParams(params);
            request.setReceiver(userId + "@example.com");
            request.setSubject("订单支付成功");
            request.setBusinessId("order_paid_" + orderId);
            
            return messageService.sendMessage(request);
        } catch (Exception e) {
            log.error("发送订单支付消息失败", e);
            return MessageSendResponse.failure("ORDER_PAID_ERROR", "发送订单支付消息失败: " + e.getMessage());
        }
    }
    
    @Override
    public MessageSendResponse sendTradeExecutedMessage(String userId, String tradeId, String symbol, String quantity, String price) {
        try {
            log.info("发送交易执行消息: userId={}, tradeId={}", userId, tradeId);
            
            Map<String, Object> params = new HashMap<>();
            params.put("tradeId", tradeId);
            params.put("symbol", symbol);
            params.put("quantity", quantity);
            params.put("price", price);
            
            MessageSendRequest request = new MessageSendRequest();
            request.setMessageType(MessageType.EMAIL);
            request.setPlatformType(PlatformType.SMTP);
            request.setBusinessType(BusinessType.TRADE_EXECUTED);
            request.setTemplateId("EMAIL_TRADE_EXECUTED");
            request.setTemplateParams(params);
            request.setReceiver(userId + "@example.com");
            request.setSubject("交易执行成功");
            request.setBusinessId("trade_executed_" + tradeId);
            
            return messageService.sendMessage(request);
        } catch (Exception e) {
            log.error("发送交易执行消息失败", e);
            return MessageSendResponse.failure("TRADE_EXECUTED_ERROR", "发送交易执行消息失败: " + e.getMessage());
        }
    }
    
    @Override
    public MessageSendResponse sendRiskAlertMessage(String userId, String riskType, String riskLevel, String description) {
        try {
            log.info("发送风控预警消息: userId={}, riskType={}", userId, riskType);
            
            Map<String, Object> params = new HashMap<>();
            params.put("riskType", riskType);
            params.put("riskLevel", riskLevel);
            params.put("description", description);
            
            MessageSendRequest request = new MessageSendRequest();
            request.setMessageType(MessageType.TELEGRAM);
            request.setPlatformType(PlatformType.TELEGRAM);
            request.setBusinessType(BusinessType.RISK_ALERT);
            request.setTemplateId("TELEGRAM_RISK_ALERT");
            request.setTemplateParams(params);
            request.setReceiver(userId); // Telegram chat ID
            request.setSubject("风控预警");
            request.setBusinessId("risk_alert_" + System.currentTimeMillis());
            
            return messageService.sendMessage(request);
        } catch (Exception e) {
            log.error("发送风控预警消息失败", e);
            return MessageSendResponse.failure("RISK_ALERT_ERROR", "发送风控预警消息失败: " + e.getMessage());
        }
    }
    
    @Override
    public MessageSendResponse sendSystemMaintenanceMessage(String userId, String maintenanceTime, String duration) {
        try {
            log.info("发送系统维护消息: userId={}, maintenanceTime={}", userId, maintenanceTime);
            
            Map<String, Object> params = new HashMap<>();
            params.put("maintenanceTime", maintenanceTime);
            params.put("duration", duration);
            
            MessageSendRequest request = new MessageSendRequest();
            request.setMessageType(MessageType.LARK);
            request.setPlatformType(PlatformType.LARK);
            request.setBusinessType(BusinessType.SYSTEM_MAINTENANCE);
            request.setTemplateId("LARK_SYSTEM_MAINTENANCE");
            request.setTemplateParams(params);
            request.setReceiver(userId); // Lark chat ID
            request.setSubject("系统维护通知");
            request.setBusinessId("system_maintenance_" + System.currentTimeMillis());
            
            return messageService.sendMessage(request);
        } catch (Exception e) {
            log.error("发送系统维护消息失败", e);
            return MessageSendResponse.failure("SYSTEM_MAINTENANCE_ERROR", "发送系统维护消息失败: " + e.getMessage());
        }
    }
    
    private MessageSendRequest buildMessageRequest(BusinessType businessType, String receiver, Map<String, Object> params, String languageCode) {
        MessageSendRequest request = new MessageSendRequest();
        request.setBusinessType(businessType);
        request.setReceiver(receiver);
        request.setTemplateParams(params);
        request.setLanguageCode(languageCode);
        request.setBusinessId(businessType.name().toLowerCase() + "_" + System.currentTimeMillis());
        
        // 根据业务类型设置默认的消息类型和平台
        switch (businessType) {
            case USER_REGISTER:
            case USER_LOGIN:
                request.setMessageType(MessageType.SMS);
                request.setPlatformType(PlatformType.ALIYUN_SMS);
                request.setTemplateId("SMS_" + businessType.name());
                break;
            case USER_PASSWORD_RESET:
            case ORDER_CREATED:
            case ORDER_PAID:
            case TRADE_EXECUTED:
                request.setMessageType(MessageType.EMAIL);
                request.setPlatformType(PlatformType.SMTP);
                request.setTemplateId("EMAIL_" + businessType.name());
                break;
            case RISK_ALERT:
                request.setMessageType(MessageType.TELEGRAM);
                request.setPlatformType(PlatformType.TELEGRAM);
                request.setTemplateId("TELEGRAM_" + businessType.name());
                break;
            case SYSTEM_MAINTENANCE:
                request.setMessageType(MessageType.LARK);
                request.setPlatformType(PlatformType.LARK);
                request.setTemplateId("LARK_" + businessType.name());
                break;
            default:
                request.setMessageType(MessageType.EMAIL);
                request.setPlatformType(PlatformType.SMTP);
                request.setTemplateId("DEFAULT_" + businessType.name());
        }
        
        return request;
    }
} 