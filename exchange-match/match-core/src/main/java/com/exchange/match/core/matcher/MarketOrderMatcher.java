package com.exchange.match.core.matcher;

import com.exchange.match.core.model.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.ArrayList;

/**
 * 市价单匹配器
 * 支持滑点控制和深度限制
 */
@Slf4j
@Component
public class MarketOrderMatcher extends AbstractOrderMatcher {
    
    @Override
    protected boolean preMatch(Order order, OrderBook orderBook, Symbol symbol) {
        // 市价单需要检查是否有对手方订单
        if (order.getSide() == OrderSide.BUY) {
            if (orderBook.getSellOrders().isEmpty()) {
                log.warn("市价买单无对手方订单: orderId={}", order.getOrderId());
                return false;
            }
        } else {
            if (orderBook.getBuyOrders().isEmpty()) {
                log.warn("市价卖单无对手方订单: orderId={}", order.getOrderId());
                return false;
            }
        }
        
        return true;
    }
    
    @Override
    protected List<Trade> matchBuyOrder(Order buyOrder, OrderBook orderBook, Symbol symbol) {
        List<Trade> trades = new ArrayList<>();
        int depthCount = 0;
        int maxDepth = symbol.getMarketMaxDepth();
        
        // 获取最优卖价
        BigDecimal bestAskPrice = orderBook.getBestAsk();
        if (bestAskPrice == null) {
            log.warn("市价买单无最优卖价: orderId={}", buyOrder.getOrderId());
            return trades;
        }
        
        // 计算最大可接受价格（滑点控制）
        BigDecimal maxAcceptablePrice = symbol.calculateMarketBuyMaxPrice(bestAskPrice);
        if (maxAcceptablePrice == null) {
            log.warn("市价买单无法计算最大可接受价格: orderId={}", buyOrder.getOrderId());
            return trades;
        }
        
        // 获取卖单队列
        for (java.util.Map.Entry<BigDecimal, java.util.LinkedList<Order>> entry : orderBook.getSellOrders().entrySet()) {
            BigDecimal sellPrice = entry.getKey();
            java.util.LinkedList<Order> sellOrders = entry.getValue();
            
            // 检查价格是否超过最大可接受价格（滑点控制）
            if (sellPrice.compareTo(maxAcceptablePrice) > 0) {
                log.debug("市价买单价格超过滑点限制: orderId={}, price={}, maxPrice={}", 
                        buyOrder.getOrderId(), sellPrice, maxAcceptablePrice);
                break;
            }
            
            // 检查深度限制
            if (depthCount >= maxDepth) {
                log.debug("市价买单达到最大深度限制: orderId={}, depth={}", 
                        buyOrder.getOrderId(), maxDepth);
                break;
            }
            
            // 撮合该价格层的订单（按时间顺序，先到先得）
            for (Order sellOrder : new ArrayList<>(sellOrders)) {
                if (buyOrder.getRemainingQuantity().compareTo(BigDecimal.ZERO) <= 0) {
                    break; // 买单已完全成交
                }
                
                if (sellOrder.getRemainingQuantity().compareTo(BigDecimal.ZERO) <= 0) {
                    continue; // 卖单已完全成交
                }
                
                // 计算成交数量
                BigDecimal matchQuantity = buyOrder.getRemainingQuantity()
                        .min(sellOrder.getRemainingQuantity());
                
                // 创建成交记录
                Trade trade = createTrade(buyOrder, sellOrder, sellPrice, matchQuantity, symbol);
                trades.add(trade);
                
                // 更新订单
                buyOrder.updateFilledQuantity(matchQuantity);
                sellOrder.updateFilledQuantity(matchQuantity);
                
                // 更新对手方订单薄
                orderBook.updateOrder(sellOrder);
                
                // 更新订单薄统计
                orderBook.updateLastPrice(sellPrice);
                orderBook.addVolume(matchQuantity);
            }
            
            depthCount++;
        }
        
        return trades;
    }
    
    @Override
    protected List<Trade> matchSellOrder(Order sellOrder, OrderBook orderBook, Symbol symbol) {
        List<Trade> trades = new ArrayList<>();
        int depthCount = 0;
        int maxDepth = symbol.getMarketMaxDepth();
        
        // 获取最优买价
        BigDecimal bestBidPrice = orderBook.getBestBid();
        if (bestBidPrice == null) {
            log.warn("市价卖单无最优买价: orderId={}", sellOrder.getOrderId());
            return trades;
        }
        
        // 计算最小可接受价格（滑点控制）
        BigDecimal minAcceptablePrice = symbol.calculateMarketSellMinPrice(bestBidPrice);
        if (minAcceptablePrice == null) {
            log.warn("市价卖单无法计算最小可接受价格: orderId={}", sellOrder.getOrderId());
            return trades;
        }
        
        // 获取买单队列
        for (java.util.Map.Entry<BigDecimal, java.util.LinkedList<Order>> entry : orderBook.getBuyOrders().entrySet()) {
            BigDecimal buyPrice = entry.getKey();
            java.util.LinkedList<Order> buyOrders = entry.getValue();
            
            // 检查价格是否低于最小可接受价格（滑点控制）
            if (buyPrice.compareTo(minAcceptablePrice) < 0) {
                log.debug("市价卖单价格低于滑点限制: orderId={}, price={}, minPrice={}", 
                        sellOrder.getOrderId(), buyPrice, minAcceptablePrice);
                break;
            }
            
            // 检查深度限制
            if (depthCount >= maxDepth) {
                log.debug("市价卖单达到最大深度限制: orderId={}, depth={}", 
                        sellOrder.getOrderId(), maxDepth);
                break;
            }
            
            // 撮合该价格层的订单（按时间顺序，先到先得）
            for (Order buyOrder : new ArrayList<>(buyOrders)) {
                if (sellOrder.getRemainingQuantity().compareTo(BigDecimal.ZERO) <= 0) {
                    break; // 卖单已完全成交
                }
                
                if (buyOrder.getRemainingQuantity().compareTo(BigDecimal.ZERO) <= 0) {
                    continue; // 买单已完全成交
                }
                
                // 计算成交数量
                BigDecimal matchQuantity = sellOrder.getRemainingQuantity()
                        .min(buyOrder.getRemainingQuantity());
                
                // 创建成交记录
                Trade trade = createTrade(buyOrder, sellOrder, buyPrice, matchQuantity, symbol);
                trades.add(trade);
                
                // 更新订单
                buyOrder.updateFilledQuantity(matchQuantity);
                sellOrder.updateFilledQuantity(matchQuantity);
                
                // 更新对手方订单薄
                orderBook.updateOrder(buyOrder);
                
                // 更新订单薄统计
                orderBook.updateLastPrice(buyPrice);
                orderBook.addVolume(matchQuantity);
            }
            
            depthCount++;
        }
        
        return trades;
    }
    
    @Override
    protected void postMatch(Order order, OrderBook orderBook, Symbol symbol, List<Trade> trades) {
        // 市价单不留在订单薄中，无论是否完全成交
        if (!trades.isEmpty()) {
            log.debug("市价单部分成交: orderId={}, filled={}, total={}", 
                    order.getOrderId(), order.getFilledQuantity(), order.getQuantity());
        } else {
            log.debug("市价单未成交: orderId={}", order.getOrderId());
        }
        // 不调用父类方法，即不将订单添加到订单薄
    }
    
    @Override
    public OrderType getSupportedOrderType() {
        return OrderType.MARKET;
    }
} 