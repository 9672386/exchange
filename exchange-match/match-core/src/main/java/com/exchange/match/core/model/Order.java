package com.exchange.match.core.model;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 订单模型
 */
@Data
public class Order {
    
    /**
     * 订单ID
     */
    private String orderId;
    
    /**
     * 用户ID
     */
    private Long userId;
    
    /**
     * 交易对
     */
    private String symbol;
    
    /**
     * 订单方向（买/卖）
     */
    private OrderSide side;
    
    /**
     * 订单类型
     */
    private OrderType type;
    
    /**
     * 开平仓动作
     */
    private PositionAction positionAction;
    
    /**
     * 价格
     */
    private BigDecimal price;
    
    /**
     * 数量
     */
    private BigDecimal quantity;
    
    /**
     * 已成交数量
     */
    private BigDecimal filledQuantity;
    
    /**
     * 剩余数量
     */
    private BigDecimal remainingQuantity;
    
    /**
     * 订单状态
     */
    private OrderStatus status;
    
    /**
     * 创建时间
     */
    private LocalDateTime createTime;
    
    /**
     * 更新时间
     */
    private LocalDateTime updateTime;
    
    /**
     * 成交时间
     */
    private LocalDateTime fillTime;
    
    /**
     * 客户端订单ID
     */
    private String clientOrderId;
    
    /**
     * 杠杆倍数
     */
    private BigDecimal leverage;
    
    /**
     * 备注
     */
    private String remark;
    
    public Order() {
        this.createTime = LocalDateTime.now();
        this.updateTime = LocalDateTime.now();
        this.filledQuantity = BigDecimal.ZERO;
        this.remainingQuantity = quantity;
        this.positionAction = null; // 默认为null，根据交易类型设置
        this.leverage = BigDecimal.ONE; // 默认杠杆为1倍
    }
    
    /**
     * 检查订单是否可以成交
     */
    public boolean canMatch(BigDecimal matchPrice) {
        if (status != OrderStatus.ACTIVE) {
            return false;
        }
        
        if (remainingQuantity.compareTo(BigDecimal.ZERO) <= 0) {
            return false;
        }
        
        if (side == OrderSide.BUY) {
            return matchPrice.compareTo(price) <= 0;
        } else {
            return matchPrice.compareTo(price) >= 0;
        }
    }
    
    /**
     * 更新成交数量
     */
    public void updateFilledQuantity(BigDecimal fillQuantity) {
        this.filledQuantity = this.filledQuantity.add(fillQuantity);
        this.remainingQuantity = this.quantity.subtract(this.filledQuantity);
        this.updateTime = LocalDateTime.now();
        
        if (this.remainingQuantity.compareTo(BigDecimal.ZERO) <= 0) {
            this.status = OrderStatus.FILLED;
            this.fillTime = LocalDateTime.now();
        }
    }
    
    /**
     * 取消订单
     */
    public void cancel() {
        this.status = OrderStatus.CANCELLED;
        this.updateTime = LocalDateTime.now();
    }
    
    /**
     * 获取成交率
     */
    public BigDecimal getFillRate() {
        if (quantity.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        return filledQuantity.divide(quantity, 4, BigDecimal.ROUND_HALF_UP);
    }
    
    /**
     * 检查是否完全成交
     */
    public boolean isFullyFilled() {
        return remainingQuantity.compareTo(BigDecimal.ZERO) == 0;
    }
    
    /**
     * 检查是否部分成交
     */
    public boolean isPartiallyFilled() {
        return filledQuantity.compareTo(BigDecimal.ZERO) > 0 && 
               remainingQuantity.compareTo(BigDecimal.ZERO) > 0;
    }
} 