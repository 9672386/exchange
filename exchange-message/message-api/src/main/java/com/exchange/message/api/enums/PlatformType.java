package com.exchange.message.api.enums;

/**
 * 平台类型枚举
 */
public enum PlatformType {
    // SMS平台
    ALIYUN_SMS("阿里云短信"),
    TENCENT_SMS("腾讯云短信"),
    AWS_SNS("AWS短信"),
    
    // Email平台
    ALIYUN_EMAIL("阿里云邮件"),
    TENCENT_EMAIL("腾讯云邮件"),
    AWS_SES("AWS邮件"),
    SMTP("SMTP邮件"),
    
    // 即时通讯平台
    TELEGRAM("Telegram"),
    LARK("飞书");
    
    private final String description;
    
    PlatformType(String description) {
        this.description = description;
    }
    
    public String getDescription() {
        return description;
    }
} 