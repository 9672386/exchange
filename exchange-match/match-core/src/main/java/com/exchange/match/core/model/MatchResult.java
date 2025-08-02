package com.exchange.match.core.model;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 撮合结果模型
 */
@Data
public class MatchResult {
    
    /**
     * 订单ID
     */
    private String orderId;
    
    /**
     * 撮合状态
     */
    private MatchStatus status;
    
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
     * 成交记录
     */
    private List<Trade> trades;
    
    /**
     * 手续费
     */
    private BigDecimal fee;
    
    /**
     * 错误信息
     */
    private String errorMessage;
    
    /**
     * 创建时间
     */
    private LocalDateTime createTime;
    
    public MatchResult() {
        this.createTime = LocalDateTime.now();
        this.status = MatchStatus.PENDING;
        this.matchQuantity = BigDecimal.ZERO;
        this.remainingQuantity = BigDecimal.ZERO;
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
} 