package com.exchange.match.core.model;

/**
 * 风险计算模式枚举
 * 定义不同的风险计算方式
 */
public enum RiskCalculationMode {
    
    /**
     * 逐仓风险计算
     * 每个交易对独立计算风险率
     * 只考虑当前交易对的保证金和盈亏
     */
    ISOLATED("逐仓风险计算", "基于单个交易对的保证金和盈亏计算风险"),
    
    /**
     * 全仓风险计算
     * 所有交易对共享保证金计算风险率
     * 考虑所有交易对的盈亏和保证金
     */
    CROSS("全仓风险计算", "基于所有交易对的共享保证金计算风险");
    
    private final String name;
    private final String description;
    
    RiskCalculationMode(String name, String description) {
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
     * 检查是否为逐仓风险计算
     */
    public boolean isIsolated() {
        return this == ISOLATED;
    }
    
    /**
     * 检查是否为全仓风险计算
     */
    public boolean isCross() {
        return this == CROSS;
    }
} 