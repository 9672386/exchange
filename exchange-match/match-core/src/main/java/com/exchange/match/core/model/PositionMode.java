package com.exchange.match.core.model;

/**
 * 仓位模式枚举
 * 定义不同的仓位管理模式
 */
public enum PositionMode {
    
    /**
     * 逐仓模式
     * 每个交易对独立计算保证金和风险
     * 单个交易对的亏损不会影响其他交易对
     */
    ISOLATED("逐仓", "每个交易对独立管理仓位和风险"),
    
    /**
     * 全仓模式
     * 所有交易对共享保证金
     * 单个交易对的盈利可以弥补其他交易对的亏损
     */
    CROSS("全仓", "所有交易对共享保证金和风险");
    
    private final String name;
    private final String description;
    
    PositionMode(String name, String description) {
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
     * 检查是否为逐仓模式
     */
    public boolean isIsolated() {
        return this == ISOLATED;
    }
    
    /**
     * 检查是否为全仓模式
     */
    public boolean isCross() {
        return this == CROSS;
    }
    
    /**
     * 获取风险计算模式
     */
    public RiskCalculationMode getRiskCalculationMode() {
        return this == ISOLATED ? RiskCalculationMode.ISOLATED : RiskCalculationMode.CROSS;
    }
    
    /**
     * 获取强平模式
     */
    public LiquidationMode getLiquidationMode() {
        return this == ISOLATED ? LiquidationMode.ISOLATED : LiquidationMode.CROSS;
    }
} 