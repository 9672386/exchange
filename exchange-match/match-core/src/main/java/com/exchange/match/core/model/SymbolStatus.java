package com.exchange.match.core.model;

/**
 * 标的状态枚举
 */
public enum SymbolStatus {
    ACTIVE("活跃"),
    INACTIVE("非活跃"),
    SUSPENDED("暂停交易"),
    DELISTED("已下架");
    
    private final String description;
    
    SymbolStatus(String description) {
        this.description = description;
    }
    
    public String getDescription() {
        return description;
    }
} 