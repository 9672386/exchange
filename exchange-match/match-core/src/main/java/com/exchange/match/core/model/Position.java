package com.exchange.match.core.model;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 仓位模型
 */
@Data
public class Position {
    
    /**
     * 用户ID
     */
    private Long userId;
    
    /**
     * 交易对
     */
    private String symbol;
    
    /**
     * 基础货币（如BTC）
     */
    private String baseCurrency;
    
    /**
     * 计价货币（如USDT）
     */
    private String quoteCurrency;
    
    /**
     * 持仓方向
     */
    private PositionSide side;
    
    /**
     * 仓位模式（逐仓/全仓）
     */
    private PositionMode positionMode;
    
    /**
     * 持仓数量
     */
    private BigDecimal quantity;
    
    /**
     * 平均开仓价格
     */
    private BigDecimal averagePrice;
    
    /**
     * 未实现盈亏
     */
    private BigDecimal unrealizedPnl;
    
    /**
     * 已实现盈亏
     */
    private BigDecimal realizedPnl;
    
    /**
     * 保证金
     * 逐仓模式：当前交易对的保证金
     * 全仓模式：共享的保证金
     */
    private BigDecimal margin;
    
    /**
     * 杠杆倍数
     */
    private BigDecimal leverage;
    
    /**
     * 强平价格
     */
    private BigDecimal liquidationPrice;
    
    /**
     * 仓位状态
     */
    private PositionStatus status;
    
    /**
     * 锁定数量
     */
    private BigDecimal lockedQuantity;
    
    /**
     * 可用数量
     */
    private BigDecimal availableQuantity;
    
    /**
     * 锁定状态
     */
    private PositionLockStatus lockStatus;
    
    /**
     * 创建时间
     */
    private LocalDateTime createTime;
    
    /**
     * 更新时间
     */
    private LocalDateTime updateTime;
    
    public Position() {
        this.createTime = LocalDateTime.now();
        this.updateTime = LocalDateTime.now();
        this.quantity = BigDecimal.ZERO;
        this.averagePrice = BigDecimal.ZERO;
        this.unrealizedPnl = BigDecimal.ZERO;
        this.realizedPnl = BigDecimal.ZERO;
        this.margin = BigDecimal.ZERO;
        this.leverage = BigDecimal.ONE;
        this.lockedQuantity = BigDecimal.ZERO;
        this.availableQuantity = BigDecimal.ZERO;
        this.lockStatus = PositionLockStatus.UNLOCKED;
        this.positionMode = PositionMode.ISOLATED; // 默认逐仓模式
    }
    
    /**
     * 开仓
     */
    public void openPosition(BigDecimal quantity, BigDecimal price) {
        if (this.quantity.compareTo(BigDecimal.ZERO) == 0) {
            // 首次开仓
            this.quantity = quantity;
            this.averagePrice = price;
            this.side = quantity.compareTo(BigDecimal.ZERO) > 0 ? PositionSide.LONG : PositionSide.SHORT;
        } else {
            // 加仓
            BigDecimal totalValue = this.quantity.multiply(this.averagePrice)
                    .add(quantity.multiply(price));
            this.quantity = this.quantity.add(quantity);
            this.averagePrice = totalValue.divide(this.quantity, 8, BigDecimal.ROUND_HALF_UP);
        }
        
        // 更新可用数量
        this.updateAvailableQuantity();
        this.updateTime = LocalDateTime.now();
    }
    
    /**
     * 平仓
     */
    public void closePosition(BigDecimal quantity, BigDecimal price) {
        if (this.quantity.compareTo(quantity) < 0) {
            throw new IllegalArgumentException("平仓数量不能大于持仓数量");
        }
        
        // 检查可用数量是否足够
        if (this.availableQuantity.compareTo(quantity) < 0) {
            throw new IllegalArgumentException("可用数量不足，无法平仓");
        }
        
        // 计算已实现盈亏
        BigDecimal pnl = price.subtract(this.averagePrice).multiply(quantity);
        if (this.side == PositionSide.SHORT) {
            pnl = pnl.negate();
        }
        this.realizedPnl = this.realizedPnl.add(pnl);
        
        // 更新持仓数量
        this.quantity = this.quantity.subtract(quantity);
        
        // 解锁对应的仓位
        this.unlockPosition(quantity);
        
        // 如果完全平仓，重置平均价格
        if (this.quantity.compareTo(BigDecimal.ZERO) == 0) {
            this.averagePrice = BigDecimal.ZERO;
        }
        
        this.updateTime = LocalDateTime.now();
    }
    
    /**
     * 更新未实现盈亏
     */
    public void updateUnrealizedPnl(BigDecimal currentPrice) {
        if (this.quantity.compareTo(BigDecimal.ZERO) == 0) {
            this.unrealizedPnl = BigDecimal.ZERO;
            return;
        }
        
        BigDecimal pnl = currentPrice.subtract(this.averagePrice).multiply(this.quantity);
        if (this.side == PositionSide.SHORT) {
            pnl = pnl.negate();
        }
        this.unrealizedPnl = pnl;
    }
    
    /**
     * 计算保证金率
     * 根据仓位模式采用不同的计算方式
     */
    public BigDecimal getMarginRatio() {
        if (this.margin.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        
        BigDecimal totalPnl = this.unrealizedPnl.add(this.realizedPnl);
        return totalPnl.divide(this.margin, 4, BigDecimal.ROUND_HALF_UP);
    }
    
    /**
     * 计算逐仓风险率
     * 只考虑当前交易对的保证金和盈亏
     */
    public BigDecimal getIsolatedRiskRatio() {
        if (this.margin.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.valueOf(1.0); // 100%风险率
        }
        
        // 逐仓风险率 = (保证金 - 未实现盈亏) / 保证金
        BigDecimal availableMargin = this.margin.subtract(this.unrealizedPnl);
        BigDecimal riskRatio = availableMargin.divide(this.margin, 4, BigDecimal.ROUND_HALF_UP);
        return BigDecimal.ONE.subtract(riskRatio);
    }
    
    /**
     * 计算全仓风险率
     * 需要传入总保证金和总盈亏
     */
    public BigDecimal getCrossRiskRatio(BigDecimal totalMargin, BigDecimal totalUnrealizedPnl) {
        if (totalMargin.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.valueOf(1.0); // 100%风险率
        }
        
        // 全仓风险率 = (总保证金 - 总未实现盈亏) / 总保证金
        BigDecimal availableMargin = totalMargin.subtract(totalUnrealizedPnl);
        BigDecimal riskRatio = availableMargin.divide(totalMargin, 4, BigDecimal.ROUND_HALF_UP);
        return BigDecimal.ONE.subtract(riskRatio);
    }
    
    /**
     * 检查是否会被强平
     * 根据仓位模式采用不同的判断逻辑
     */
    public boolean isLiquidatable() {
        if (this.positionMode.isIsolated()) {
            // 逐仓模式：只检查当前交易对的风险率
            return getIsolatedRiskRatio().compareTo(BigDecimal.valueOf(0.98)) >= 0;
        } else {
            // 全仓模式：需要传入总风险率进行判断
            // 这里只是占位，实际需要外部传入总风险率
            return false;
        }
    }
    
    /**
     * 获取总盈亏
     */
    public BigDecimal getTotalPnl() {
        return this.unrealizedPnl.add(this.realizedPnl);
    }
    
    /**
     * 获取仓位价值
     */
    public BigDecimal getPositionValue() {
        return this.quantity.multiply(this.averagePrice);
    }
    
    /**
     * 锁定仓位
     */
    public boolean lockPosition(BigDecimal lockQuantity, String orderId, String reason) {
        if (lockQuantity.compareTo(this.availableQuantity) > 0) {
            return false; // 可用数量不足
        }
        
        this.lockedQuantity = this.lockedQuantity.add(lockQuantity);
        this.availableQuantity = this.availableQuantity.subtract(lockQuantity);
        
        if (this.lockedQuantity.compareTo(this.quantity) >= 0) {
            this.lockStatus = PositionLockStatus.LOCKED;
        } else {
            this.lockStatus = PositionLockStatus.PARTIALLY_LOCKED;
        }
        
        this.updateTime = LocalDateTime.now();
        return true;
    }
    
    /**
     * 解锁仓位
     */
    public void unlockPosition(BigDecimal unlockQuantity) {
        BigDecimal actualUnlockQuantity = unlockQuantity.min(this.lockedQuantity);
        this.lockedQuantity = this.lockedQuantity.subtract(actualUnlockQuantity);
        this.availableQuantity = this.availableQuantity.add(actualUnlockQuantity);
        
        if (this.lockedQuantity.compareTo(BigDecimal.ZERO) <= 0) {
            this.lockStatus = PositionLockStatus.UNLOCKED;
        } else {
            this.lockStatus = PositionLockStatus.PARTIALLY_LOCKED;
        }
        
        this.updateTime = LocalDateTime.now();
    }
    
    /**
     * 检查是否可以平仓
     */
    public boolean canClose(BigDecimal closeQuantity) {
        return this.availableQuantity.compareTo(closeQuantity) >= 0;
    }

    /**
     * 更新可用数量（开仓时调用）
     */
    public void updateAvailableQuantity() {
        this.availableQuantity = this.quantity.subtract(this.lockedQuantity);
    }
    
    /**
     * 检查是否为逐仓模式
     */
    public boolean isIsolatedMode() {
        return this.positionMode.isIsolated();
    }
    
    /**
     * 检查是否为全仓模式
     */
    public boolean isCrossMode() {
        return this.positionMode.isCross();
    }
    
    /**
     * 设置仓位模式
     */
    public void setPositionMode(PositionMode positionMode) {
        this.positionMode = positionMode;
        this.updateTime = LocalDateTime.now();
    }
    
    /**
     * 获取风险计算模式
     */
    public RiskCalculationMode getRiskCalculationMode() {
        return this.positionMode.getRiskCalculationMode();
    }
    
    /**
     * 获取强平模式
     */
    public LiquidationMode getLiquidationMode() {
        return this.positionMode.getLiquidationMode();
    }
} 