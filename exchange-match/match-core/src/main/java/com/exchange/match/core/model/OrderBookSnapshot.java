package com.exchange.match.core.model;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 订单薄快照模型
 */
@Data
public class OrderBookSnapshot {
    
    /**
     * 交易对
     */
    private String symbol;
    
    /**
     * 最新成交价
     */
    private BigDecimal lastPrice;
    
    /**
     * 24小时最高价
     */
    private BigDecimal highPrice;
    
    /**
     * 24小时最低价
     */
    private BigDecimal lowPrice;
    
    /**
     * 24小时成交量
     */
    private BigDecimal volume24h;
    
    /**
     * 创建时间
     */
    private long createTime;
    
    /**
     * 最后更新时间
     */
    private long lastUpdateTime;
    
    /**
     * 买单深度
     */
    private List<PriceLevel> buyDepth;
    
    /**
     * 卖单深度
     */
    private List<PriceLevel> sellDepth;
} 