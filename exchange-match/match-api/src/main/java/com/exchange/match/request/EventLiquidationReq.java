package com.exchange.match.request;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 强平事件请求DTO
 */
@Data
public class EventLiquidationReq {
    
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
    private String liquidationType;
    
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
    private String side;
    
    /**
     * 强平原因
     */
    private String reason;
    
    /**
     * 触发强平的参数（如风险度、保证金等）
     */
    private String triggerParams;
    
    /**
     * 是否全部强平
     */
    private Boolean isFullLiquidation;
    
    /**
     * 是否紧急强平
     */
    private Boolean isEmergency;
    
    /**
     * 强平优先级
     */
    private Integer priority;
    
    /**
     * 风险等级（由风控服务传入）
     */
    private String riskLevel;
    
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
} 