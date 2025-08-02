package com.exchange.match.core.model;

/**
 * 强平模式枚举
 * 定义不同的强平策略
 */
public enum LiquidationMode {
    
    /**
     * 逐仓强平
     * 只强平当前交易对的仓位
     * 不影响其他交易对的仓位
     */
    ISOLATED("逐仓强平", "只强平当前交易对的仓位"),
    
    /**
     * 全仓强平
     * 强平时考虑所有交易对的仓位
     * 可能同时强平多个交易对的仓位
     */
    CROSS("全仓强平", "强平时考虑所有交易对的仓位");
    
    private final String name;
    private final String description;
    
    LiquidationMode(String name, String description) {
        this.name = name;
        this.description = description;
    }
    
    public String getName() {
        return name;
    }
    
    public String getDescription() {
        return description;
    }
    
    /**
     * 检查是否为逐仓强平
     */
    public boolean isIsolated() {
        return this == ISOLATED;
    }
    
    /**
     * 检查是否为全仓强平
     */
    public boolean isCross() {
        return this == CROSS;
    }
} 