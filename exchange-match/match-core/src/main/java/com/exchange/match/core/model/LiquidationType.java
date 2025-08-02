package com.exchange.match.core.model;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 强平类型枚举
 */
@Getter
@AllArgsConstructor
public enum LiquidationType {
    
    /**
     * 保证金不足强平
     * 用户保证金不足以维持当前持仓，系统自动强平
     */
    MARGIN_INSUFFICIENT("保证金不足强平", "用户保证金不足以维持当前持仓"),
    
    /**
     * 风险度超限强平
     * 用户风险度超过系统设定的最大风险度，触发强平
     */
    RISK_EXCEEDED("风险度超限强平", "用户风险度超过系统设定的最大风险度"),
    
    /**
     * 价格偏离强平
     * 价格偏离过大，触发强平以控制风险
     */
    PRICE_DEVIATION("价格偏离强平", "价格偏离过大，触发强平以控制风险"),
    
    /**
     * 系统风险强平
     * 系统检测到异常风险，主动触发强平
     */
    SYSTEM_RISK("系统风险强平", "系统检测到异常风险，主动触发强平"),
    
    /**
     * 监管强平
     * 监管要求或市场异常情况下的强制平仓
     */
    REGULATORY("监管强平", "监管要求或市场异常情况下的强制平仓"),
    
    /**
     * 手动强平
     * 管理员手动触发的强平
     */
    MANUAL("手动强平", "管理员手动触发的强平"),
    
    /**
     * 合约到期强平
     * 合约到期时的自动强平
     */
    EXPIRY("合约到期强平", "合约到期时的自动强平"),
    
    /**
     * 其他强平
     * 其他原因导致的强平
     */
    OTHER("其他强平", "其他原因导致的强平");
    
    /**
     * 强平类型名称
     */
    private final String name;
    
    /**
     * 强平原因描述
     */
    private final String description;
    
    /**
     * 是否为系统自动强平
     */
    public boolean isAutoLiquidation() {
        return this == MARGIN_INSUFFICIENT || 
               this == RISK_EXCEEDED || 
               this == PRICE_DEVIATION || 
               this == SYSTEM_RISK ||
               this == EXPIRY;
    }
    
    /**
     * 是否为手动强平
     */
    public boolean isManualLiquidation() {
        return this == MANUAL || this == REGULATORY;
    }
    
    /**
     * 是否为紧急强平
     */
    public boolean isEmergencyLiquidation() {
        return this == SYSTEM_RISK || this == REGULATORY;
    }
    
    /**
     * 获取强平优先级
     * 数值越小优先级越高
     */
    public int getPriority() {
        switch (this) {
            case SYSTEM_RISK:
                return 1; // 最高优先级
            case REGULATORY:
                return 2;
            case MARGIN_INSUFFICIENT:
                return 3;
            case RISK_EXCEEDED:
                return 4;
            case PRICE_DEVIATION:
                return 5;
            case EXPIRY:
                return 6;
            case MANUAL:
                return 7;
            case OTHER:
                return 8; // 最低优先级
            default:
                return 9;
        }
    }
} 