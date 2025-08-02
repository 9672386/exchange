package com.exchange.match.core.matcher;

import com.exchange.match.core.model.*;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 抽象订单匹配器基类
 * 提取通用的撮合逻辑，子类只需要实现特定的订单类型逻辑
 */
@Slf4j
public abstract class AbstractOrderMatcher implements OrderMatcher {
    
    @Override
    public List<Trade> matchOrder(Order order, OrderBook orderBook, Symbol symbol) {
        List<Trade> trades = new ArrayList<>();
        
        // 子类特定的预处理逻辑
        if (!preMatch(order, orderBook, symbol)) {
            return trades;
        }
        
        // 执行通用撮合逻辑
        if (order.getSide() == OrderSide.BUY) {
            trades = matchBuyOrder(order, orderBook, symbol);
        } else {
            trades = matchSellOrder(order, orderBook, symbol);
        }
        
        // 子类特定的后处理逻辑
        postMatch(order, orderBook, symbol, trades);
        
        return trades;
    }
    
    /**
     * 撮合前预处理
     * 子类可以重写此方法实现特定的预处理逻辑
     * @param order 订单
     * @param orderBook 订单薄
     * @param symbol 交易标的
     * @return 是否继续撮合
     */
    protected boolean preMatch(Order order, OrderBook orderBook, Symbol symbol) {
        return true;
    }
    
    /**
     * 撮合后处理
     * 子类可以重写此方法实现特定的后处理逻辑
     * @param order 订单
     * @param orderBook 订单薄
     * @param symbol 交易标的
     * @param trades 成交记录列表
     */
    protected void postMatch(Order order, OrderBook orderBook, Symbol symbol, List<Trade> trades) {
        // 默认实现：将订单添加到订单薄（如果还有剩余数量）
        if (order.getRemainingQuantity().compareTo(BigDecimal.ZERO) > 0) {
            orderBook.addOrder(order);
        }
    }
    
    /**
     * 买单撮合（通用逻辑）
     */
    protected List<Trade> matchBuyOrder(Order buyOrder, OrderBook orderBook, Symbol symbol) {
        List<Trade> trades = new ArrayList<>();
        
        // 获取卖单队列
        for (java.util.Map.Entry<BigDecimal, java.util.LinkedList<Order>> entry : orderBook.getSellOrders().entrySet()) {
            BigDecimal sellPrice = entry.getKey();
            java.util.LinkedList<Order> sellOrders = entry.getValue();
            
            // 检查价格是否匹配
            if (buyOrder.getPrice().compareTo(sellPrice) < 0) {
                break; // 价格不匹配，停止撮合
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
        }
        
        return trades;
    }
    
    /**
     * 卖单撮合（通用逻辑）
     */
    protected List<Trade> matchSellOrder(Order sellOrder, OrderBook orderBook, Symbol symbol) {
        List<Trade> trades = new ArrayList<>();
        
        // 获取买单队列
        for (java.util.Map.Entry<BigDecimal, java.util.LinkedList<Order>> entry : orderBook.getBuyOrders().entrySet()) {
            BigDecimal buyPrice = entry.getKey();
            java.util.LinkedList<Order> buyOrders = entry.getValue();
            
            // 检查价格是否匹配
            if (sellOrder.getPrice().compareTo(buyPrice) > 0) {
                break; // 价格不匹配，停止撮合
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
        }
        
        return trades;
    }
    
    /**
     * 创建成交记录（通用逻辑）
     */
    protected Trade createTrade(Order buyOrder, Order sellOrder, BigDecimal price, 
                              BigDecimal quantity, Symbol symbol) {
        Trade trade = new Trade();
        trade.setTradeId(UUID.randomUUID().toString());
        trade.setSymbol(buyOrder.getSymbol());
        trade.setBuyOrderId(buyOrder.getOrderId());
        trade.setSellOrderId(sellOrder.getOrderId());
        trade.setBuyUserId(buyOrder.getUserId());
        trade.setSellUserId(sellOrder.getUserId());
        trade.setPrice(price);
        trade.setQuantity(quantity);
        trade.setSide(TradeSide.BUY);
        
        // 设置开平仓动作（仅合约交易）
        if (symbol.supportsPosition()) {
            trade.setBuyPositionAction(buyOrder.getPositionAction());
            trade.setSellPositionAction(sellOrder.getPositionAction());
            
            // 计算仓位变化
            trade.setBuyPositionChange(calculatePositionChange(buyOrder, quantity));
            trade.setSellPositionChange(calculatePositionChange(sellOrder, quantity));
        }
        
        // 计算成交金额和手续费
        trade.calculateAmount();
        trade.calculateFees(symbol.getFeeRate(), symbol.getFeeRate());
        
        return trade;
    }
    
    /**
     * 计算仓位变化
     */
    private BigDecimal calculatePositionChange(Order order, BigDecimal quantity) {
        // 对于现货交易，不计算仓位变化
        if (order.getPositionAction() == null) {
            return BigDecimal.ZERO;
        }
        
        // 使用新的计算逻辑（简化版本，不依赖memoryManager）
        PositionAction.PositionChangeResult result = PositionAction.calculatePositionChange(
            order.getSide(), 
            order.getPositionAction(), 
            null, // 暂时设为null，在service层会重新计算
            quantity
        );
        
        if (!result.isValid()) {
            throw new IllegalArgumentException("无效的开平仓操作: " + result.getReason());
        }
        
        return result.getQuantityChange();
    }
    
    /**
     * 计算可成交的总数量（通用逻辑）
     */
    protected BigDecimal calculateAvailableQuantity(Order order, OrderBook orderBook) {
        BigDecimal totalAvailable = BigDecimal.ZERO;
        
        if (order.getSide() == OrderSide.BUY) {
            // 买单：计算卖单队列中价格<=买单价格的总数量
            for (java.util.Map.Entry<BigDecimal, java.util.LinkedList<Order>> entry : orderBook.getSellOrders().entrySet()) {
                BigDecimal sellPrice = entry.getKey();
                if (order.getPrice().compareTo(sellPrice) >= 0) {
                    java.util.LinkedList<Order> sellOrders = entry.getValue();
                    for (Order sellOrder : sellOrders) {
                        if (sellOrder.getRemainingQuantity().compareTo(BigDecimal.ZERO) > 0) {
                            totalAvailable = totalAvailable.add(sellOrder.getRemainingQuantity());
                        }
                    }
                }
            }
        } else {
            // 卖单：计算买单队列中价格>=卖单价格的总数量
            for (java.util.Map.Entry<BigDecimal, java.util.LinkedList<Order>> entry : orderBook.getBuyOrders().entrySet()) {
                BigDecimal buyPrice = entry.getKey();
                if (order.getPrice().compareTo(buyPrice) <= 0) {
                    java.util.LinkedList<Order> buyOrders = entry.getValue();
                    for (Order buyOrder : buyOrders) {
                        if (buyOrder.getRemainingQuantity().compareTo(BigDecimal.ZERO) > 0) {
                            totalAvailable = totalAvailable.add(buyOrder.getRemainingQuantity());
                        }
                    }
                }
            }
        }
        
        return totalAvailable;
    }
} 