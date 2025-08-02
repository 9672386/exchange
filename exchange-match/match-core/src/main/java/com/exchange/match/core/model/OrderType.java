package com.exchange.match.core.model;

/**
 * 订单类型枚举
 */
public enum OrderType {
    MARKET("市价单"),
    LIMIT("限价单"),
    FOK("全部成交或取消"),
    IOC("立即成交或取消"),
    POST_ONLY("只做挂单"),
    STOP("止损单"),
    STOP_LIMIT("止损限价单");
    
    private final String description;
    
    OrderType(String description) {
        this.description = description;
    }
    
    public String getDescription() {
        return description;
    }
} 