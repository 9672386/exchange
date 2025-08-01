package com.exchange.message.core.service;

import com.exchange.message.api.dto.MessageSendResponse;
import com.exchange.message.api.enums.BusinessType;

import java.util.Map;

/**
 * 业务消息服务接口
 */
public interface BusinessMessageService {
    
    /**
     * 根据业务类型发送消息
     * @param businessType 业务类型
     * @param receiver 接收者
     * @param params 业务参数
     * @return 消息发送响应
     */
    MessageSendResponse sendBusinessMessage(BusinessType businessType, String receiver, Map<String, Object> params);
    
    /**
     * 发送用户注册消息
     * @param phoneNumber 手机号
     * @param verificationCode 验证码
     * @return 消息发送响应
     */
    MessageSendResponse sendUserRegisterMessage(String phoneNumber, String verificationCode);
    
    /**
     * 发送用户登录消息
     * @param phoneNumber 手机号
     * @param loginTime 登录时间
     * @param loginLocation 登录地点
     * @return 消息发送响应
     */
    MessageSendResponse sendUserLoginMessage(String phoneNumber, String loginTime, String loginLocation);
    
    /**
     * 发送密码重置消息
     * @param email 邮箱
     * @param resetLink 重置链接
     * @return 消息发送响应
     */
    MessageSendResponse sendPasswordResetMessage(String email, String resetLink);
    
    /**
     * 发送订单创建消息
     * @param userId 用户ID
     * @param orderId 订单ID
     * @param amount 订单金额
     * @return 消息发送响应
     */
    MessageSendResponse sendOrderCreatedMessage(String userId, String orderId, String amount);
    
    /**
     * 发送订单支付消息
     * @param userId 用户ID
     * @param orderId 订单ID
     * @param paymentMethod 支付方式
     * @return 消息发送响应
     */
    MessageSendResponse sendOrderPaidMessage(String userId, String orderId, String paymentMethod);
    
    /**
     * 发送交易执行消息
     * @param userId 用户ID
     * @param tradeId 交易ID
     * @param symbol 交易对
     * @param quantity 数量
     * @param price 价格
     * @return 消息发送响应
     */
    MessageSendResponse sendTradeExecutedMessage(String userId, String tradeId, String symbol, String quantity, String price);
    
    /**
     * 发送风控预警消息
     * @param userId 用户ID
     * @param riskType 风险类型
     * @param riskLevel 风险等级
     * @param description 风险描述
     * @return 消息发送响应
     */
    MessageSendResponse sendRiskAlertMessage(String userId, String riskType, String riskLevel, String description);
    
    /**
     * 发送系统维护消息
     * @param userId 用户ID
     * @param maintenanceTime 维护时间
     * @param duration 维护时长
     * @return 消息发送响应
     */
    MessageSendResponse sendSystemMaintenanceMessage(String userId, String maintenanceTime, String duration);
} 