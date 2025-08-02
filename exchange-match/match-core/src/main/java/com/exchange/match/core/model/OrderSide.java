package com.exchange.match.core.model;

/**
 * 订单方向枚举
 */
public enum OrderSide {
    BUY("买单"),
    SELL("卖单");
    
    private final String description;
    
    OrderSide(String description) {
        this.description = description;
    }
    
    public String getDescription() {
        return description;
    }
} 