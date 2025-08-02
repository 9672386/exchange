package com.exchange.match.core.matcher;

import com.exchange.match.core.model.Order;
import com.exchange.match.core.model.OrderBook;
import com.exchange.match.core.model.OrderType;
import com.exchange.match.core.model.Symbol;
import com.exchange.match.core.model.Trade;

import java.util.List;

/**
 * 订单匹配器接口
 * 定义不同订单类型的匹配策略
 */
public interface OrderMatcher {
    
    /**
     * 执行订单匹配
     * @param order 待匹配的订单
     * @param orderBook 订单薄
     * @param symbol 交易标的
     * @return 匹配结果（成交记录列表）
     */
    List<Trade> matchOrder(Order order, OrderBook orderBook, Symbol symbol);
    
    /**
     * 获取支持的订单类型
     * @return 订单类型
     */
    OrderType getSupportedOrderType();
    
    /**
     * 检查是否支持指定订单类型
     * @param orderType 订单类型
     * @return 是否支持
     */
    default boolean supports(OrderType orderType) {
        return getSupportedOrderType() == orderType;
    }
} 