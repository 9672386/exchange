package com.exchange.match.core.model;

/**
 * 订单状态枚举
 */
public enum OrderStatus {
    PENDING("待处理"),
    ACTIVE("活跃"),
    PARTIALLY_FILLED("部分成交"),
    FILLED("完全成交"),
    CANCELLED("已取消"),
    REJECTED("已拒绝"),
    EXPIRED("已过期");
    
    private final String description;
    
    OrderStatus(String description) {
        this.description = description;
    }
    
    public String getDescription() {
        return description;
    }
} 