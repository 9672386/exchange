package com.exchange.match.core.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 价格层级模型（撮合内部,定点 long raw）。
 *
 * <p>{@code price}=priceScale raw,{@code totalQuantity}=baseScale raw;
 * 对外行情查询在 controller 边界转 BigDecimal。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PriceLevel {

    /**
     * 价格（priceScale 下的定点 raw）
     */
    private long price;

    /**
     * 总数量（baseScale 下的定点 raw）
     */
    private long totalQuantity;
} 