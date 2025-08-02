package com.exchange.match.core.matcher;

import com.exchange.match.core.model.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.ArrayList;

/**
 * POST_ONLY订单匹配器
 * 只做挂单，不做吃单。如果订单会立即成交，则拒绝订单
 */
@Slf4j
@Component
public class PostOnlyOrderMatcher extends AbstractOrderMatcher {
    
    @Override
    protected boolean preMatch(Order order, OrderBook orderBook, Symbol symbol) {
        // POST_ONLY订单需要检查是否会立即成交
        if (order.getSide() == OrderSide.BUY) {
            // 买单：检查价格是否高于等于最优卖价
            BigDecimal bestAskPrice = orderBook.getBestAsk();
            if (bestAskPrice != null && order.getPrice().compareTo(bestAskPrice) >= 0) {
                log.warn("POST_ONLY买单会立即成交，拒绝订单: orderId={}, price={}, bestAsk={}", 
                        order.getOrderId(), order.getPrice(), bestAskPrice);
                return false;
            }
        } else {
            // 卖单：检查价格是否低于等于最优买价
            BigDecimal bestBidPrice = orderBook.getBestBid();
            if (bestBidPrice != null && order.getPrice().compareTo(bestBidPrice) <= 0) {
                log.warn("POST_ONLY卖单会立即成交，拒绝订单: orderId={}, price={}, bestBid={}", 
                        order.getOrderId(), order.getPrice(), bestBidPrice);
                return false;
            }
        }
        
        return true;
    }
    
    @Override
    protected List<Trade> matchBuyOrder(Order buyOrder, OrderBook orderBook, Symbol symbol) {
        // POST_ONLY订单不进行撮合，直接返回空列表
        // 订单会在postMatch中添加到订单薄
        return new ArrayList<>();
    }
    
    @Override
    protected List<Trade> matchSellOrder(Order sellOrder, OrderBook orderBook, Symbol symbol) {
        // POST_ONLY订单不进行撮合，直接返回空列表
        // 订单会在postMatch中添加到订单薄
        return new ArrayList<>();
    }
    
    @Override
    protected void postMatch(Order order, OrderBook orderBook, Symbol symbol, List<Trade> trades) {
        // POST_ONLY订单只做挂单，添加到订单薄
        if (order.getRemainingQuantity().compareTo(BigDecimal.ZERO) > 0) {
            orderBook.addOrder(order);
            log.debug("POST_ONLY订单已挂单: orderId={}, price={}, quantity={}", 
                    order.getOrderId(), order.getPrice(), order.getRemainingQuantity());
        }
    }
    
    @Override
    public OrderType getSupportedOrderType() {
        return OrderType.POST_ONLY;
    }
} 