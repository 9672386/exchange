package com.exchange.match.core.model;

/**
 * 撮合状态枚举
 */
public enum MatchStatus {
    PENDING("待处理"),
    SUCCESS("完全成交"),
    PARTIALLY_FILLED("部分成交"),
    REJECTED("拒绝"),
    CANCELLED("已取消");
    
    private final String description;
    
    MatchStatus(String description) {
        this.description = description;
    }
    
    public String getDescription() {
        return description;
    }
} 