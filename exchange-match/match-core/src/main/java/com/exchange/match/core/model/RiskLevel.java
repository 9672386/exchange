package com.exchange.match.core.model;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.math.BigDecimal;

/**
 * 风险等级模型
 */
@Getter
@AllArgsConstructor
public enum RiskLevel {
    
    /**
     * 正常等级
     * 风险度 < 80%
     */
    NORMAL("正常", BigDecimal.valueOf(0.8), "正常交易，无限制"),
    
    /**
     * 预警等级
     * 风险度 80% - 90%
     */
    WARNING("预警", BigDecimal.valueOf(0.9), "限制开仓，允许平仓"),
    
    /**
     * 危险等级
     * 风险度 90% - 95%
     */
    DANGER("危险", BigDecimal.valueOf(0.95), "禁止开仓，强制减仓"),
    
    /**
     * 紧急等级
     * 风险度 95% - 98%
     */
    EMERGENCY("紧急", BigDecimal.valueOf(0.98), "禁止所有交易，立即强平"),
    
    /**
     * 爆仓等级
     * 风险度 >= 98%
     */
    LIQUIDATION("爆仓", BigDecimal.valueOf(1.0), "立即全部强平");
    
    /**
     * 风险等级名称
     */
    private final String name;
    
    /**
     * 风险度阈值
     */
    private final BigDecimal threshold;
    
    /**
     * 风险等级描述
     */
    private final String description;
    
    /**
     * 根据风险度获取风险等级
     */
    public static RiskLevel getRiskLevel(BigDecimal riskRatio) {
        if (riskRatio.compareTo(LIQUIDATION.threshold) >= 0) {
            return LIQUIDATION;
        } else if (riskRatio.compareTo(EMERGENCY.threshold) >= 0) {
            return EMERGENCY;
        } else if (riskRatio.compareTo(DANGER.threshold) >= 0) {
            return DANGER;
        } else if (riskRatio.compareTo(WARNING.threshold) >= 0) {
            return WARNING;
        } else {
            return NORMAL;
        }
    }
    
    /**
     * 检查是否允许开仓
     */
    public boolean canOpenPosition() {
        return this == NORMAL || this == WARNING;
    }
    
    /**
     * 检查是否允许平仓
     */
    public boolean canClosePosition() {
        return this != LIQUIDATION;
    }
    
    /**
     * 检查是否需要强制减仓
     */
    public boolean needForceReduction() {
        return this == DANGER || this == EMERGENCY;
    }
    
    /**
     * 检查是否需要立即强平
     */
    public boolean needImmediateLiquidation() {
        return this == EMERGENCY || this == LIQUIDATION;
    }
    
    /**
     * 获取减仓比例
     */
    public BigDecimal getReductionRatio() {
        switch (this) {
            case DANGER:
                return BigDecimal.valueOf(0.3); // 减仓30%
            case EMERGENCY:
                return BigDecimal.valueOf(0.5); // 减仓50%
            case LIQUIDATION:
                return BigDecimal.valueOf(1.0); // 全部强平
            default:
                return BigDecimal.ZERO;
        }
    }
    
    /**
     * 获取风险等级优先级
     * 数值越大优先级越高
     */
    public int getPriority() {
        switch (this) {
            case NORMAL:
                return 1;
            case WARNING:
                return 2;
            case DANGER:
                return 3;
            case EMERGENCY:
                return 4;
            case LIQUIDATION:
                return 5;
            default:
                return 0;
        }
    }
} 