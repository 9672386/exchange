package com.exchange.match.core.model;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 仓位锁定记录模型
 */
@Data
public class PositionLock {
    
    /**
     * 锁定ID
     */
    private String lockId;
    
    /**
     * 用户ID
     */
    private Long userId;
    
    /**
     * 交易对
     */
    private String symbol;
    
    /**
     * 锁定数量
     */
    private BigDecimal lockQuantity;
    
    /**
     * 锁定原因
     */
    private String lockReason;
    
    /**
     * 关联订单ID
     */
    private String orderId;
    
    /**
     * 锁定时间
     */
    private LocalDateTime lockTime;
    
    /**
     * 预期解锁时间
     */
    private LocalDateTime expectedUnlockTime;
    
    /**
     * 实际解锁时间
     */
    private LocalDateTime unlockTime;
    
    /**
     * 锁定状态
     */
    private PositionLockStatus status;
    
    /**
     * 锁定类型
     */
    private LockType lockType;
    
    public PositionLock() {
        this.lockTime = LocalDateTime.now();
        this.status = PositionLockStatus.LOCKED;
    }
    
    /**
     * 锁定类型枚举
     */
    public enum LockType {
        CLOSE_ORDER("平仓订单锁定"),
        LIQUIDATION("强平锁定"),
        RISK_CONTROL("风控锁定"),
        SYSTEM_MAINTENANCE("系统维护锁定");
        
        private final String description;
        
        LockType(String description) {
            this.description = description;
        }
        
        public String getDescription() {
            return description;
        }
    }
    
    /**
     * 解锁仓位
     */
    public void unlock() {
        this.unlockTime = LocalDateTime.now();
        this.status = PositionLockStatus.UNLOCKED;
    }
    
    /**
     * 部分解锁
     */
    public void partialUnlock(BigDecimal unlockQuantity) {
        this.lockQuantity = this.lockQuantity.subtract(unlockQuantity);
        if (this.lockQuantity.compareTo(BigDecimal.ZERO) <= 0) {
            unlock();
        } else {
            this.status = PositionLockStatus.PARTIALLY_LOCKED;
        }
    }
    
    /**
     * 检查是否已解锁
     */
    public boolean isUnlocked() {
        return this.status == PositionLockStatus.UNLOCKED;
    }
    
    /**
     * 检查是否过期
     */
    public boolean isExpired() {
        return this.expectedUnlockTime != null && 
               LocalDateTime.now().isAfter(this.expectedUnlockTime);
    }
} 