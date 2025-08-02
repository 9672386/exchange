package com.exchange.match.core.model;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 持仓平衡检查模型
 * 1. 开平仓时检查多空持仓数量平衡
 * 2. 提供意愿查询接口
 */
@Data
public class PositionBalance {
    
    /**
     * 交易对
     */
    private String symbol;
    
    /**
     * 多仓总量
     */
    private BigDecimal longTotal;
    
    /**
     * 空仓总量
     */
    private BigDecimal shortTotal;
    
    /**
     * 净持仓（多仓 - 空仓）
     */
    private BigDecimal netPosition;
    
    /**
     * 多空比例
     */
    private BigDecimal longShortRatio;
    
    /**
     * 平衡状态
     */
    private BalanceStatus status;
    
    /**
     * 检查时间
     */
    private LocalDateTime checkTime;
    
    /**
     * 用户持仓映射
     */
    private Map<Long, Position> userPositions;
    
    public PositionBalance(String symbol) {
        this.symbol = symbol;
        this.longTotal = BigDecimal.ZERO;
        this.shortTotal = BigDecimal.ZERO;
        this.netPosition = BigDecimal.ZERO;
        this.longShortRatio = BigDecimal.ONE;
        this.status = BalanceStatus.BALANCED;
        this.checkTime = LocalDateTime.now();
        this.userPositions = new ConcurrentHashMap<>();
    }
    
    /**
     * 平衡状态枚举
     */
    public enum BalanceStatus {
        BALANCED("平衡"),
        UNBALANCED("不平衡"),
        EXTREME_UNBALANCED("极端不平衡");
        
        private final String description;
        
        BalanceStatus(String description) {
            this.description = description;
        }
        
        public String getDescription() {
            return description;
        }
        
        /**
         * 检查是否为平衡状态
         */
        public boolean isBalanced() {
            return this == BALANCED;
        }
        
        /**
         * 检查是否为不平衡状态
         */
        public boolean isUnbalanced() {
            return this != BALANCED;
        }
        
        /**
         * 检查是否为极端不平衡
         */
        public boolean isExtremeUnbalanced() {
            return this == EXTREME_UNBALANCED;
        }
    }
    
    /**
     * 更新持仓数据并计算平衡状态
     */
    public void updatePositions(Map<Long, Position> positions) {
        this.userPositions.clear();
        this.userPositions.putAll(positions);
        
        // 重新计算多空总量
        recalculateBalance();
    }
    
    /**
     * 添加单个持仓
     */
    public void addPosition(Position position) {
        this.userPositions.put(position.getUserId(), position);
        recalculateBalance();
    }
    
    /**
     * 移除持仓
     */
    public void removePosition(Long userId) {
        this.userPositions.remove(userId);
        recalculateBalance();
    }
    
    /**
     * 重新计算平衡状态
     */
    private void recalculateBalance() {
        this.longTotal = BigDecimal.ZERO;
        this.shortTotal = BigDecimal.ZERO;
        
        // 计算多空总量
        for (Position position : userPositions.values()) {
            if (position.getQuantity().compareTo(BigDecimal.ZERO) > 0) {
                if (position.getSide() == PositionSide.LONG) {
                    this.longTotal = this.longTotal.add(position.getQuantity());
                } else if (position.getSide() == PositionSide.SHORT) {
                    this.shortTotal = this.shortTotal.add(position.getQuantity());
                }
            }
        }
        
        // 计算净持仓
        this.netPosition = this.longTotal.subtract(this.shortTotal);
        
        // 计算多空比例
        if (this.shortTotal.compareTo(BigDecimal.ZERO) > 0) {
            this.longShortRatio = this.longTotal.divide(this.shortTotal, 4, BigDecimal.ROUND_HALF_UP);
        } else if (this.longTotal.compareTo(BigDecimal.ZERO) > 0) {
            this.longShortRatio = BigDecimal.valueOf(100); // 只有多仓
        } else {
            this.longShortRatio = BigDecimal.ONE; // 无持仓
        }
        
        // 判断平衡状态
        this.status = determineBalanceStatus();
        this.checkTime = LocalDateTime.now();
    }
    
    /**
     * 判断平衡状态
     */
    private BalanceStatus determineBalanceStatus() {
        // 检查净持仓是否为零（允许小的误差）
        BigDecimal tolerance = BigDecimal.valueOf(0.0001);
        if (this.netPosition.abs().compareTo(tolerance) <= 0) {
            return BalanceStatus.BALANCED;
        }
        
        // 计算不平衡比例
        BigDecimal totalPosition = this.longTotal.add(this.shortTotal);
        if (totalPosition.compareTo(BigDecimal.ZERO) == 0) {
            return BalanceStatus.BALANCED; // 无持仓
        }
        
        BigDecimal imbalanceRatio = this.netPosition.abs()
                .divide(totalPosition, 4, BigDecimal.ROUND_HALF_UP);
        
        if (imbalanceRatio.compareTo(BigDecimal.valueOf(0.1)) <= 0) {
            return BalanceStatus.BALANCED; // 不平衡比例 <= 10%
        } else if (imbalanceRatio.compareTo(BigDecimal.valueOf(0.3)) <= 0) {
            return BalanceStatus.UNBALANCED; // 10% < 不平衡比例 <= 30%
        } else {
            return BalanceStatus.EXTREME_UNBALANCED; // 不平衡比例 > 30%
        }
    }
    
    /**
     * 检查开平仓是否平衡
     * 开仓：增加对应方向的持仓
     * 平仓：减少对应方向的持仓
     */
    public boolean checkOpenCloseBalance(PositionAction action, OrderSide side, BigDecimal quantity) {
        if (action == null) {
            return true; // 现货交易不需要检查
        }
        
        BigDecimal newLongTotal = this.longTotal;
        BigDecimal newShortTotal = this.shortTotal;
        
        if (action.isOpen()) {
            // 开仓：增加对应方向的持仓
            if (side == OrderSide.BUY) {
                newLongTotal = newLongTotal.add(quantity);
            } else {
                newShortTotal = newShortTotal.add(quantity);
            }
        } else if (action.isClose()) {
            // 平仓：减少对应方向的持仓
            if (side == OrderSide.BUY) {
                // 买入平空仓
                if (newShortTotal.compareTo(quantity) < 0) {
                    return false; // 空仓不足
                }
                newShortTotal = newShortTotal.subtract(quantity);
            } else {
                // 卖出平多仓
                if (newLongTotal.compareTo(quantity) < 0) {
                    return false; // 多仓不足
                }
                newLongTotal = newLongTotal.subtract(quantity);
            }
        }
        
        // 检查新的净持仓是否平衡
        BigDecimal newNetPosition = newLongTotal.subtract(newShortTotal);
        BigDecimal tolerance = BigDecimal.valueOf(0.0001);
        
        return newNetPosition.abs().compareTo(tolerance) <= 0;
    }
    
    /**
     * 获取风险等级
     */
    public RiskLevel getRiskLevel() {
        switch (this.status) {
            case BALANCED:
                return RiskLevel.LOW;
            case UNBALANCED:
                return RiskLevel.MEDIUM;
            case EXTREME_UNBALANCED:
                return RiskLevel.HIGH;
            default:
                return RiskLevel.LOW;
        }
    }
    
    /**
     * 风险等级枚举
     */
    public enum RiskLevel {
        LOW("低风险"),
        MEDIUM("中风险"),
        HIGH("高风险"),
        EXTREME("极高风险");
        
        private final String description;
        
        RiskLevel(String description) {
            this.description = description;
        }
        
        public String getDescription() {
            return description;
        }
    }
    
    /**
     * 获取平衡建议
     */
    public String getBalanceAdvice() {
        switch (this.status) {
            case BALANCED:
                return "持仓平衡，可以正常交易";
            case UNBALANCED:
                if (this.netPosition.compareTo(BigDecimal.ZERO) > 0) {
                    return "多仓偏重，建议减少多仓或增加空仓";
                } else {
                    return "空仓偏重，建议减少空仓或增加多仓";
                }
            case EXTREME_UNBALANCED:
                return "极端不平衡，建议立即调整持仓";
            default:
                return "未知状态";
        }
    }
    
    /**
     * 获取不平衡详情
     */
    public String getImbalanceDetails() {
        return String.format("多仓总量: %s, 空仓总量: %s, 净持仓: %s, 多空比例: %s", 
                longTotal, shortTotal, netPosition, longShortRatio);
    }
    
    /**
     * 获取持仓统计信息
     */
    public String getPositionStats() {
        return String.format("总持仓数: %d, 多仓用户数: %d, 空仓用户数: %d", 
                userPositions.size(),
                userPositions.values().stream()
                        .filter(p -> p.getSide() == PositionSide.LONG && p.getQuantity().compareTo(BigDecimal.ZERO) > 0)
                        .count(),
                userPositions.values().stream()
                        .filter(p -> p.getSide() == PositionSide.SHORT && p.getQuantity().compareTo(BigDecimal.ZERO) > 0)
                        .count());
    }
} 