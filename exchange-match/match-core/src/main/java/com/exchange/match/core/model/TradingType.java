package com.exchange.match.core.model;

/**
 * 交易类型枚举
 */
public enum TradingType {
    SPOT("现货"),
    FUTURES("合约"),
    PERPETUAL("永续合约");
    
    private final String description;
    
    TradingType(String description) {
        this.description = description;
    }
    
    public String getDescription() {
        return description;
    }
    
    /**
     * 检查是否为现货交易
     */
    public boolean isSpot() {
        return this == SPOT;
    }
    
    /**
     * 检查是否为合约交易
     */
    public boolean isFutures() {
        return this == FUTURES || this == PERPETUAL;
    }
    
    /**
     * 检查是否为永续合约
     */
    public boolean isPerpetual() {
        return this == PERPETUAL;
    }
    
    /**
     * 检查是否支持仓位管理
     */
    public boolean supportsPosition() {
        return isFutures();
    }
    
    /**
     * 检查是否支持杠杆
     */
    public boolean supportsLeverage() {
        return isFutures();
    }
    
    /**
     * 检查是否支持做空
     */
    public boolean supportsShort() {
        return isFutures();
    }
} 