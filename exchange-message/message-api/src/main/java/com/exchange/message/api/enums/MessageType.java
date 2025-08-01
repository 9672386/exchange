package com.exchange.message.api.enums;

/**
 * 消息类型枚举
 */
public enum MessageType {
    SMS("短信"),
    EMAIL("邮件"),
    TELEGRAM("Telegram"),
    LARK("飞书");
    
    private final String description;
    
    MessageType(String description) {
        this.description = description;
    }
    
    public String getDescription() {
        return description;
    }
} 