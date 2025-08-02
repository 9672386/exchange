package com.exchange.match.core.model;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.math.BigDecimal;

/**
 * ADL（自动减仓）策略模型
 */
@Getter
@AllArgsConstructor
public enum ADLStrategy {
    
    /**
     * 无ADL策略
     * 直接强平，不进行ADL
     */
    NONE("无ADL", "直接强平，不进行ADL"),
    
    /**
     * 对手方优先
     * 优先减仓对手方持仓，减少市场冲击
     */
    COUNTERPARTY_FIRST("对手方优先", "优先减仓对手方持仓"),
    
    /**
     * 盈利优先
     * 优先减仓盈利持仓，保护亏损用户
     */
    PROFIT_FIRST("盈利优先", "优先减仓盈利持仓"),
    
    /**
     * 亏损优先
     * 优先减仓亏损持仓，减少系统风险
     */
    LOSS_FIRST("亏损优先", "优先减仓亏损持仓"),
    
    /**
     * 时间优先
     * 按持仓时间顺序减仓，先开仓的先减仓
     */
    TIME_FIRST("时间优先", "按持仓时间顺序减仓"),
    
    /**
     * 规模优先
     * 按持仓规模减仓，大持仓优先减仓
     */
    SIZE_FIRST("规模优先", "按持仓规模减仓"),
    
    /**
     * 混合策略
     * 综合考虑多个因素的混合减仓策略
     */
    HYBRID("混合策略", "综合考虑多个因素的混合减仓策略");
    
    /**
     * 策略名称
     */
    private final String name;
    
    /**
     * 策略描述
     */
    private final String description;
    
    /**
     * 获取ADL优先级
     * 数值越小优先级越高
     */
    public int getPriority() {
        switch (this) {
            case NONE:
                return 0; // 最高优先级，直接强平
            case COUNTERPARTY_FIRST:
                return 1;
            case PROFIT_FIRST:
                return 2;
            case LOSS_FIRST:
                return 3;
            case TIME_FIRST:
                return 4;
            case SIZE_FIRST:
                return 5;
            case HYBRID:
                return 6;
            default:
                return 7;
        }
    }
    
    /**
     * 检查是否为直接强平策略
     */
    public boolean isDirectLiquidation() {
        return this == NONE;
    }
    
    /**
     * 检查是否为ADL策略
     */
    public boolean isADLStrategy() {
        return this != NONE;
    }
    
    /**
     * 获取ADL减仓比例
     */
    public BigDecimal getADLRatio() {
        switch (this) {
            case COUNTERPARTY_FIRST:
                return BigDecimal.valueOf(0.3); // 减仓30%
            case PROFIT_FIRST:
                return BigDecimal.valueOf(0.4); // 减仓40%
            case LOSS_FIRST:
                return BigDecimal.valueOf(0.5); // 减仓50%
            case TIME_FIRST:
                return BigDecimal.valueOf(0.3); // 减仓30%
            case SIZE_FIRST:
                return BigDecimal.valueOf(0.4); // 减仓40%
            case HYBRID:
                return BigDecimal.valueOf(0.35); // 减仓35%
            default:
                return BigDecimal.ZERO;
        }
    }
    
    /**
     * 获取ADL执行间隔（毫秒）
     */
    public long getADLInterval() {
        switch (this) {
            case COUNTERPARTY_FIRST:
                return 1000; // 1秒
            case PROFIT_FIRST:
                return 2000; // 2秒
            case LOSS_FIRST:
                return 1500; // 1.5秒
            case TIME_FIRST:
                return 1000; // 1秒
            case SIZE_FIRST:
                return 1500; // 1.5秒
            case HYBRID:
                return 1200; // 1.2秒
            default:
                return 0;
        }
    }
    
    /**
     * 获取ADL最大执行次数
     */
    public int getMaxADLCount() {
        switch (this) {
            case COUNTERPARTY_FIRST:
                return 3;
            case PROFIT_FIRST:
                return 4;
            case LOSS_FIRST:
                return 5;
            case TIME_FIRST:
                return 3;
            case SIZE_FIRST:
                return 4;
            case HYBRID:
                return 4;
            default:
                return 0;
        }
    }
} 