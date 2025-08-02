package com.exchange.match.core.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 价格层级模型
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PriceLevel {
    
    /**
     * 价格
     */
    private BigDecimal price;
    
    /**
     * 总数量
     */
    private BigDecimal totalQuantity;
} 