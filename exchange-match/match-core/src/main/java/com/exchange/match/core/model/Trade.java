package com.exchange.match.core.model;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 成交记录模型
 */
@Data
public class Trade {
    
    /**
     * 成交ID
     */
    private String tradeId;
    
    /**
     * 交易对
     */
    private String symbol;
    
    /**
     * 买方订单ID
     */
    private String buyOrderId;
    
    /**
     * 卖方订单ID
     */
    private String sellOrderId;
    
    /**
     * 买方用户ID
     */
    private Long buyUserId;
    
    /**
     * 卖方用户ID
     */
    private Long sellUserId;
    
    /**
     * 成交价格
     */
    private BigDecimal price;
    
    /**
     * 成交数量
     */
    private BigDecimal quantity;
    
    /**
     * 成交金额
     */
    private BigDecimal amount;
    
    /**
     * 买方手续费
     */
    private BigDecimal buyFee;
    
    /**
     * 卖方手续费
     */
    private BigDecimal sellFee;
    
    /**
     * 成交时间
     */
    private LocalDateTime tradeTime;
    
    /**
     * 成交方向
     */
    private TradeSide side;
    
    /**
     * 买方开平仓动作
     */
    private PositionAction buyPositionAction;
    
    /**
     * 卖方开平仓动作
     */
    private PositionAction sellPositionAction;
    
    /**
     * 买方仓位变化
     */
    private BigDecimal buyPositionChange;
    
    /**
     * 卖方仓位变化
     */
    private BigDecimal sellPositionChange;
    
    public Trade() {
        this.tradeTime = LocalDateTime.now();
    }
    
    /**
     * 计算成交金额
     */
    public void calculateAmount() {
        this.amount = this.price.multiply(this.quantity);
    }
    
    /**
     * 计算手续费
     */
    public void calculateFees(BigDecimal buyFeeRate, BigDecimal sellFeeRate) {
        this.buyFee = this.amount.multiply(buyFeeRate);
        this.sellFee = this.amount.multiply(sellFeeRate);
    }
} 