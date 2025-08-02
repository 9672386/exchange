package com.exchange.match.core.memory;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 内存统计信息
 */
@Data
public class MemoryStats {
    
    /**
     * 订单薄数量
     */
    private int orderBookCount;
    
    /**
     * 仓位数量
     */
    private int positionCount;
    
    /**
     * 标的数量
     */
    private int symbolCount;
    
    /**
     * 总订单数量
     */
    private long totalOrderCount;
    
    /**
     * 总成交数量
     */
    private long totalTradeCount;
    
    /**
     * 24小时总成交量
     */
    private BigDecimal totalVolume24h;
    
    /**
     * 统计时间戳
     */
    private long timestamp;
    
    public MemoryStats() {
        this.timestamp = System.currentTimeMillis();
        this.totalVolume24h = BigDecimal.ZERO;
    }
} 