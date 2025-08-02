package com.exchange.match.core.model;

/**
 * 仓位状态枚举
 */
public enum PositionStatus {
    ACTIVE("活跃"),
    CLOSED("已平仓"),
    LIQUIDATED("已强平");
    
    private final String description;
    
    PositionStatus(String description) {
        this.description = description;
    }
    
    public String getDescription() {
        return description;
    }
} 