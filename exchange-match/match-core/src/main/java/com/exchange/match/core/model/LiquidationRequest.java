package com.exchange.match.core.model;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 强平请求模型
 */
@Data
public class LiquidationRequest {
    
    /**
     * 强平请求ID
     */
    private String liquidationId;
    
    /**
     * 用户ID
     */
    private Long userId;
    
    /**
     * 交易对
     */
    private String symbol;
    
    /**
     * 强平类型
     */
    private LiquidationType liquidationType;
    
    /**
     * 强平数量（全部强平时为null）
     */
    private BigDecimal quantity;
    
    /**
     * 强平价格（市价强平时为null）
     */
    private BigDecimal price;
    
    /**
     * 强平方向（全部强平时为null）
     */
    private OrderSide side;
    
    /**
     * 强平原因
     */
    private String reason;
    
    /**
     * 触发强平的参数（如风险度、保证金等）
     */
    private String triggerParams;
    
    /**
     * 强平优先级
     */
    private Integer priority;
    
    /**
     * 是否紧急强平
     */
    private Boolean isEmergency;
    
    /**
     * 风险等级（由风控服务传入）
     */
    private RiskLevel riskLevel;
    
    /**
     * 是否需要风险降档（由风控服务传入）
     */
    private Boolean needRiskReduction;
    
    // ========== 风控服务传递的上下文信息 ==========
    
    /**
     * 触发强平时的指数价格
     */
    private BigDecimal indexPrice;
    
    /**
     * 用户资金余额
     */
    private BigDecimal balance;
    
    /**
     * 用户保证金
     */
    private BigDecimal margin;
    
    /**
     * 用户风险率
     */
    private BigDecimal riskRatio;
    
    /**
     * 未实现盈亏
     */
    private BigDecimal unrealizedPnl;
    
    /**
     * 已实现盈亏
     */
    private BigDecimal realizedPnl;
    
    /**
     * 维持保证金率
     */
    private BigDecimal maintenanceMarginRatio;
    
    /**
     * 初始保证金率
     */
    private BigDecimal initialMarginRatio;
    
    /**
     * 风控计算时的持仓信息
     */
    private String positionInfo;
    
    /**
     * 风控计算时的市场信息
     */
    private String marketInfo;
    
    /**
     * 风控计算时间戳
     */
    private Long riskCalculationTime;
    
    /**
     * 是否全部强平
     */
    private Boolean isFullLiquidation;
    
    /**
     * 强平状态
     */
    private LiquidationStatus status;
    
    /**
     * 创建时间
     */
    private LocalDateTime createTime;
    
    /**
     * 执行时间
     */
    private LocalDateTime executeTime;
    
    /**
     * 完成时间
     */
    private LocalDateTime completeTime;
    
    /**
     * 强平结果
     */
    private LiquidationResult result;
    
    public LiquidationRequest() {
        this.createTime = LocalDateTime.now();
        this.status = LiquidationStatus.PENDING;
        this.isEmergency = false;
        this.isFullLiquidation = false;
    }
    
    /**
     * 强平状态枚举
     */
    public enum LiquidationStatus {
        PENDING("待执行"),
        EXECUTING("执行中"),
        COMPLETED("已完成"),
        FAILED("执行失败"),
        CANCELLED("已取消");
        
        private final String description;
        
        LiquidationStatus(String description) {
            this.description = description;
        }
        
        public String getDescription() {
            return description;
        }
    }
    
    /**
     * 强平结果
     */
    @Data
    public static class LiquidationResult {
        /**
         * 强平成功数量
         */
        private BigDecimal successQuantity;
        
        /**
         * 强平失败数量
         */
        private BigDecimal failedQuantity;
        
        /**
         * 强平均价
         */
        private BigDecimal averagePrice;
        
        /**
         * 强平金额
         */
        private BigDecimal totalAmount;
        
        /**
         * 手续费
         */
        private BigDecimal fee;
        
        /**
         * 已实现盈亏
         */
        private BigDecimal realizedPnl;
        
        /**
         * 强平详情
         */
        private String details;
        
        /**
         * 错误信息
         */
        private String errorMessage;
        
        /**
         * 执行时间
         */
        private LocalDateTime executeTime;
        
        /**
         * 完成时间
         */
        private LocalDateTime completeTime;
    }
    
    /**
     * 设置强平优先级
     */
    public void setPriorityFromType() {
        this.priority = this.liquidationType.getPriority();
    }
    
    /**
     * 设置紧急强平标志
     */
    public void setEmergencyFromType() {
        this.isEmergency = this.liquidationType.isEmergencyLiquidation();
    }
    
    /**
     * 检查是否为全部强平
     */
    public boolean isFullLiquidation() {
        return this.isFullLiquidation != null && this.isFullLiquidation;
    }
    
    /**
     * 检查是否为部分强平
     */
    public boolean isPartialLiquidation() {
        return !isFullLiquidation() && this.quantity != null && this.quantity.compareTo(BigDecimal.ZERO) > 0;
    }
    
    /**
     * 检查是否为市价强平
     */
    public boolean isMarketLiquidation() {
        return this.price == null;
    }
    
    /**
     * 检查是否为限价强平
     */
    public boolean isLimitLiquidation() {
        return this.price != null;
    }
    
    /**
     * 获取强平描述
     */
    public String getLiquidationDescription() {
        StringBuilder sb = new StringBuilder();
        sb.append(this.liquidationType.getName());
        
        if (this.isFullLiquidation()) {
            sb.append("(全部)");
        } else if (this.isPartialLiquidation()) {
            sb.append("(部分: ").append(this.quantity).append(")");
        }
        
        if (this.isMarketLiquidation()) {
            sb.append(" - 市价");
        } else {
            sb.append(" - 限价(").append(this.price).append(")");
        }
        
        if (this.isEmergency) {
            sb.append(" - 紧急");
        }
        
        return sb.toString();
    }
} 