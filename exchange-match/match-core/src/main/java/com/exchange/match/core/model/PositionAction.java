package com.exchange.match.core.model;

import java.math.BigDecimal;

/**
 * 开平仓动作枚举
 * 纯粹的开平仓动作，不包含买卖方向信息
 */
public enum PositionAction {
    OPEN("开仓"),      // 建立新仓位
    CLOSE("平仓");     // 关闭现有仓位
    
    private final String description;
    
    PositionAction(String description) {
        this.description = description;
    }
    
    public String getDescription() {
        return description;
    }
    
    /**
     * 检查是否为开仓
     */
    public boolean isOpen() {
        return this == OPEN;
    }
    
    /**
     * 检查是否为平仓
     */
    public boolean isClose() {
        return this == CLOSE;
    }
    
    /**
     * 根据订单方向、开平仓动作和当前仓位计算最终的仓位变化
     */
    public static PositionChangeResult calculatePositionChange(
            OrderSide orderSide, 
            PositionAction positionAction, 
            PositionSide currentPositionSide,
            BigDecimal quantity) {
        
        PositionSide newPositionSide = null;
        BigDecimal positionChange = BigDecimal.ZERO;
        boolean isValid = true;
        String reason = null;
        
        if (positionAction == OPEN) {
            // 开仓逻辑
            if (orderSide == OrderSide.BUY) {
                // 买单开仓 = 开多仓
                newPositionSide = PositionSide.LONG;
                positionChange = quantity;
            } else {
                // 卖单开仓 = 开空仓
                newPositionSide = PositionSide.SHORT;
                positionChange = quantity;
            }
        } else if (positionAction == CLOSE) {
            // 平仓逻辑
            if (orderSide == OrderSide.BUY) {
                // 买单平仓 = 平空仓
                if (currentPositionSide == PositionSide.SHORT) {
                    newPositionSide = PositionSide.SHORT;
                    positionChange = quantity.negate(); // 减少空仓
                } else {
                    isValid = false;
                    reason = "买单平仓但当前无空仓";
                }
            } else {
                // 卖单平仓 = 平多仓
                if (currentPositionSide == PositionSide.LONG) {
                    newPositionSide = PositionSide.LONG;
                    positionChange = quantity.negate(); // 减少多仓
                } else {
                    isValid = false;
                    reason = "卖单平仓但当前无多仓";
                }
            }
        }
        
        return new PositionChangeResult(newPositionSide, positionChange, isValid, reason);
    }
    
    /**
     * 根据订单方向和当前仓位自动判断开平仓动作
     */
    public static PositionAction determineAction(OrderSide orderSide, PositionSide currentPositionSide) {
        if (orderSide == OrderSide.BUY) {
            // 买单
            if (currentPositionSide == PositionSide.LONG) {
                return OPEN; // 持有多仓，继续开多
            } else if (currentPositionSide == PositionSide.SHORT) {
                return CLOSE; // 持有空仓，平空
            } else {
                return OPEN; // 无仓位，开多
            }
        } else {
            // 卖单
            if (currentPositionSide == PositionSide.LONG) {
                return CLOSE; // 持有多仓，平多
            } else if (currentPositionSide == PositionSide.SHORT) {
                return OPEN; // 持有空仓，继续开空
            } else {
                return OPEN; // 无仓位，开空
            }
        }
    }
    
    /**
     * 仓位变化结果
     */
    public static class PositionChangeResult {
        private final PositionSide positionSide;
        private final BigDecimal quantityChange;
        private final boolean isValid;
        private final String reason;
        
        public PositionChangeResult(PositionSide positionSide, BigDecimal quantityChange, 
                                 boolean isValid, String reason) {
            this.positionSide = positionSide;
            this.quantityChange = quantityChange;
            this.isValid = isValid;
            this.reason = reason;
        }
        
        public PositionSide getPositionSide() {
            return positionSide;
        }
        
        public BigDecimal getQuantityChange() {
            return quantityChange;
        }
        
        public boolean isValid() {
            return isValid;
        }
        
        public String getReason() {
            return reason;
        }
    }
} 