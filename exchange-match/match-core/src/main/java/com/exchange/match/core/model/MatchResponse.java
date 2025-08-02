package com.exchange.match.core.model;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 撮合响应信息模型
 * 包含成交信息、撤单信息、仓位信息和拒绝信息
 */
@Data
public class MatchResponse {
    
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
     * 订单方向
     */
    private OrderSide side;
    
    /**
     * 订单类型
     */
    private OrderType orderType;
    
    /**
     * 撮合状态
     */
    private MatchStatus status;
    
    /**
     * 订单价格
     */
    private BigDecimal orderPrice;
    
    /**
     * 订单数量
     */
    private BigDecimal orderQuantity;
    
    /**
     * 成交价格
     */
    private BigDecimal matchPrice;
    
    /**
     * 成交数量
     */
    private BigDecimal matchQuantity;
    
    /**
     * 剩余数量
     */
    private BigDecimal remainingQuantity;
    
    /**
     * 成交金额
     */
    private BigDecimal matchAmount;
    
    /**
     * 手续费
     */
    private BigDecimal fee;
    
    /**
     * 成交记录列表
     */
    private List<Trade> trades;
    
    /**
     * 仓位变化信息
     */
    private PositionChange positionChange;
    
    /**
     * 撤单信息
     */
    private CancelInfo cancelInfo;
    
    /**
     * 拒绝信息
     */
    private RejectInfo rejectInfo;
    
    /**
     * 错误信息
     */
    private String errorMessage;
    
    /**
     * 创建时间
     */
    private LocalDateTime createTime;
    
    /**
     * 处理时间
     */
    private LocalDateTime processTime;
    
    public MatchResponse() {
        this.createTime = LocalDateTime.now();
        this.processTime = LocalDateTime.now();
        this.status = MatchStatus.PENDING;
        this.matchQuantity = BigDecimal.ZERO;
        this.remainingQuantity = BigDecimal.ZERO;
        this.matchAmount = BigDecimal.ZERO;
        this.fee = BigDecimal.ZERO;
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
        return matchQuantity.compareTo(BigDecimal.ZERO) > 0 && 
               remainingQuantity.compareTo(BigDecimal.ZERO) > 0;
    }
    
    /**
     * 检查是否未成交
     */
    public boolean isUnfilled() {
        return matchQuantity.compareTo(BigDecimal.ZERO) == 0;
    }
    
    /**
     * 检查是否成功
     */
    public boolean isSuccess() {
        return status == MatchStatus.SUCCESS || status == MatchStatus.PARTIALLY_FILLED;
    }
    
    /**
     * 检查是否被拒绝
     */
    public boolean isRejected() {
        return status == MatchStatus.REJECTED;
    }
    
    /**
     * 检查是否被取消
     */
    public boolean isCancelled() {
        return status == MatchStatus.CANCELLED;
    }
    
    /**
     * 获取成交率
     */
    public BigDecimal getFillRate() {
        if (matchQuantity.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        
        BigDecimal totalQuantity = matchQuantity.add(remainingQuantity);
        if (totalQuantity.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        
        return matchQuantity.divide(totalQuantity, 4, BigDecimal.ROUND_HALF_UP);
    }
    
    /**
     * 仓位变化信息
     */
    @Data
    public static class PositionChange {
        /**
         * 用户ID
         */
        private Long userId;
        
        /**
         * 交易对
         */
        private String symbol;
        
        /**
         * 仓位方向
         */
        private PositionSide side;
        
        /**
         * 开平仓动作
         */
        private PositionAction positionAction;
        
        /**
         * 仓位数量变化
         */
        private BigDecimal quantityChange;
        
        /**
         * 仓位价值变化
         */
        private BigDecimal valueChange;
        
        /**
         * 更新后仓位数量
         */
        private BigDecimal newQuantity;
        
        /**
         * 更新后仓位价值
         */
        private BigDecimal newValue;
        
        /**
         * 更新后未实现盈亏
         */
        private BigDecimal newUnrealizedPnl;
        
        /**
         * 更新后已实现盈亏
         */
        private BigDecimal newRealizedPnl;
        
        /**
         * 平均成本
         */
        private BigDecimal averageCost;
        
        /**
         * 开仓均价
         */
        private BigDecimal openAveragePrice;
        
        /**
         * 平仓均价
         */
        private BigDecimal closeAveragePrice;
        
        /**
         * 已实现盈亏变化
         */
        private BigDecimal realizedPnlChange;
        
        /**
         * 更新时间
         */
        private LocalDateTime updateTime;
        
        public PositionChange() {
            this.updateTime = LocalDateTime.now();
        }
    }
    
    /**
     * 撤单信息
     */
    @Data
    public static class CancelInfo {
        /**
         * 撤单用户ID
         */
        private Long cancelUserId;
        
        /**
         * 撤单原因
         */
        private String cancelReason;
        
        /**
         * 撤单数量
         */
        private BigDecimal cancelQuantity;
        
        /**
         * 撤单时间
         */
        private LocalDateTime cancelTime;
        
        /**
         * 撤单前状态
         */
        private MatchStatus previousStatus;
        
        public CancelInfo() {
            this.cancelTime = LocalDateTime.now();
        }
    }
    
    /**
     * 拒绝信息
     */
    @Data
    public static class RejectInfo {
        /**
         * 拒绝原因代码
         */
        private String rejectCode;
        
        /**
         * 拒绝原因描述
         */
        private String rejectReason;
        
        /**
         * 拒绝时间
         */
        private LocalDateTime rejectTime;
        
        /**
         * 拒绝类型
         */
        private RejectType rejectType;
        
        /**
         * 拒绝类型枚举
         */
        public enum RejectType {
            INVALID_PRICE("价格无效"),
            INVALID_QUANTITY("数量无效"),
            INSUFFICIENT_BALANCE("余额不足"),
            INSUFFICIENT_POSITION("仓位不足"),
            INVALID_POSITION_ACTION("无效的开平仓操作"),
            ORDER_NOT_FOUND("订单不存在"),
            INSUFFICIENT_PERMISSION("权限不足"),
            INVALID_ORDER_STATUS("订单状态无效"),
            PRICE_OUT_OF_RANGE("价格超出范围"),
            QUANTITY_OUT_OF_RANGE("数量超出范围"),
            MARKET_CLOSED("市场关闭"),
            SYMBOL_NOT_TRADABLE("标的不可交易"),
            POST_ONLY_REJECTED("POST_ONLY订单会立即成交"),
            FOK_REJECTED("FOK订单无法全部成交"),
            SLIPPAGE_TOO_HIGH("滑点过大"),
            DEPTH_INSUFFICIENT("深度不足"),
            POSITION_IMBALANCE("多空不平衡"),
            SYSTEM_ERROR("系统错误");
            
            private final String description;
            
            RejectType(String description) {
                this.description = description;
            }
            
            public String getDescription() {
                return description;
            }
        }
        
        public RejectInfo() {
            this.rejectTime = LocalDateTime.now();
        }
    }
} 