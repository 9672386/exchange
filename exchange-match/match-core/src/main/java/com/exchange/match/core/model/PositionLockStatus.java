package com.exchange.match.core.model;

/**
 * 仓位锁定状态枚举
 */
public enum PositionLockStatus {
    UNLOCKED("未锁定"),
    LOCKED("已锁定"),
    PARTIALLY_LOCKED("部分锁定");
    
    private final String description;
    
    PositionLockStatus(String description) {
        this.description = description;
    }
    
    public String getDescription() {
        return description;
    }
    
    /**
     * 检查是否为锁定状态
     */
    public boolean isLocked() {
        return this == LOCKED || this == PARTIALLY_LOCKED;
    }
    
    /**
     * 检查是否为完全锁定
     */
    public boolean isFullyLocked() {
        return this == LOCKED;
    }
    
    /**
     * 检查是否为部分锁定
     */
    public boolean isPartiallyLocked() {
        return this == PARTIALLY_LOCKED;
    }
} 