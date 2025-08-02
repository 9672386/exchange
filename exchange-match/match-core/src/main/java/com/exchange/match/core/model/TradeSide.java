package com.exchange.match.core.model;

/**
 * 成交方向枚举
 */
public enum TradeSide {
    BUY("买入"),
    SELL("卖出");
    
    private final String description;
    
    TradeSide(String description) {
        this.description = description;
    }
    
    public String getDescription() {
        return description;
    }
} 