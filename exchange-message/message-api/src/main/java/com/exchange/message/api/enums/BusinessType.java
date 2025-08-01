package com.exchange.message.api.enums;

/**
 * 业务类型枚举
 */
public enum BusinessType {
    // 用户相关
    USER_REGISTER("用户注册"),
    USER_LOGIN("用户登录"),
    USER_PASSWORD_RESET("密码重置"),
    USER_VERIFICATION("用户验证"),
    
    // 交易相关
    ORDER_CREATED("订单创建"),
    ORDER_PAID("订单支付"),
    ORDER_CANCELLED("订单取消"),
    ORDER_COMPLETED("订单完成"),
    TRADE_EXECUTED("交易执行"),
    TRADE_FAILED("交易失败"),
    
    // 账户相关
    ACCOUNT_CREATED("账户创建"),
    ACCOUNT_FROZEN("账户冻结"),
    ACCOUNT_UNFROZEN("账户解冻"),
    BALANCE_CHANGED("余额变动"),
    WITHDRAWAL_REQUEST("提现申请"),
    WITHDRAWAL_APPROVED("提现批准"),
    WITHDRAWAL_REJECTED("提现拒绝"),
    
    // 风控相关
    RISK_ALERT("风控预警"),
    SUSPICIOUS_TRADE("可疑交易"),
    LIMIT_EXCEEDED("限额超限"),
    
    // 系统相关
    SYSTEM_MAINTENANCE("系统维护"),
    SYSTEM_UPGRADE("系统升级"),
    SYSTEM_ERROR("系统错误"),
    
    // 通知相关
    MARKET_UPDATE("市场更新"),
    PRICE_ALERT("价格预警"),
    NEWS_NOTIFICATION("新闻通知"),
    
    // 客服相关
    CUSTOMER_SERVICE("客服消息"),
    FEEDBACK_RECEIVED("反馈收到"),
    COMPLAINT_PROCESSED("投诉处理");
    
    private final String description;
    
    BusinessType(String description) {
        this.description = description;
    }
    
    public String getDescription() {
        return description;
    }
} 