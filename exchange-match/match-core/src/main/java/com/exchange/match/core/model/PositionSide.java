package com.exchange.match.core.model;

/**
 * 仓位方向枚举
 */
public enum PositionSide {
    LONG("多头"),
    SHORT("空头");
    
    private final String description;
    
    PositionSide(String description) {
        this.description = description;
    }
    
    public String getDescription() {
        return description;
    }
} 