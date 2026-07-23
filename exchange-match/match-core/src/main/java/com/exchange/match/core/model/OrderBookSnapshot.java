package com.exchange.match.core.model;

import lombok.Data;

import java.util.List;

/**
 * 订单薄快照模型（撮合内部,价/量为定点 long raw;对外行情在 controller 边界转 BigDecimal）。
 */
@Data
public class OrderBookSnapshot {

    /**
     * 交易对
     */
    private String symbol;

    /**
     * 最新成交价（priceScale raw）
     */
    private long lastPrice;

    /**
     * 24小时最高价（priceScale raw）
     */
    private long highPrice;

    /**
     * 24小时最低价（priceScale raw）
     */
    private long lowPrice;

    /**
     * 24小时成交量（baseScale raw）
     */
    private long volume24h;
    
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