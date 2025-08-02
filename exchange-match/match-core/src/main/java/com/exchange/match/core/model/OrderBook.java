package com.exchange.match.core.model;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListMap;

/**
 * 订单薄内存模型
 * 管理买卖订单队列，支持快速撮合
 */
@Slf4j
@Data
public class OrderBook {
    
    /**
     * 交易对
     */
    private String symbol;
    
    /**
     * 买单队列（价格从高到低排序，同价格按时间排序）
     */
    private final NavigableMap<BigDecimal, LinkedList<Order>> buyOrders = new ConcurrentSkipListMap<>(Collections.reverseOrder());
    
    /**
     * 卖单队列（价格从低到高排序，同价格按时间排序）
     */
    private final NavigableMap<BigDecimal, LinkedList<Order>> sellOrders = new ConcurrentSkipListMap<>();
    
    /**
     * 订单ID到订单的映射
     */
    private final Map<String, Order> orderMap = new ConcurrentHashMap<>();
    
    /**
     * 用户ID到订单列表的映射
     */
    private final Map<Long, List<Order>> userOrders = new ConcurrentHashMap<>();
    
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
    
    public OrderBook(String symbol) {
        this.symbol = symbol;
        this.createTime = System.currentTimeMillis();
        this.lastUpdateTime = this.createTime;
    }
    
    /**
     * 添加订单
     */
    public void addOrder(Order order) {
        orderMap.put(order.getOrderId(), order);
        
        // 添加到用户订单列表
        userOrders.computeIfAbsent(order.getUserId(), k -> new ArrayList<>()).add(order);
        
        // 添加到价格队列（按时间顺序添加到链表末尾）
        NavigableMap<BigDecimal, LinkedList<Order>> orders = 
                order.getSide() == OrderSide.BUY ? buyOrders : sellOrders;
        
        orders.computeIfAbsent(order.getPrice(), k -> new LinkedList<>()).addLast(order);
        
        updateLastUpdateTime();
        log.debug("添加订单: symbol={}, orderId={}, price={}, quantity={}, createTime={}", 
                symbol, order.getOrderId(), order.getPrice(), order.getQuantity(), order.getCreateTime());
    }
    
    /**
     * 移除订单
     */
    public void removeOrder(String orderId) {
        Order order = orderMap.remove(orderId);
        if (order == null) {
            return;
        }
        
        // 从用户订单列表移除
        List<Order> userOrderList = userOrders.get(order.getUserId());
        if (userOrderList != null) {
            userOrderList.removeIf(o -> o.getOrderId().equals(orderId));
            if (userOrderList.isEmpty()) {
                userOrders.remove(order.getUserId());
            }
        }
        
        // 从价格队列移除
        NavigableMap<BigDecimal, LinkedList<Order>> orders = 
                order.getSide() == OrderSide.BUY ? buyOrders : sellOrders;
        
        LinkedList<Order> priceLevel = orders.get(order.getPrice());
        if (priceLevel != null) {
            priceLevel.removeIf(o -> o.getOrderId().equals(orderId));
            if (priceLevel.isEmpty()) {
                orders.remove(order.getPrice());
            }
        }
        
        updateLastUpdateTime();
        log.debug("移除订单: symbol={}, orderId={}", symbol, orderId);
    }
    
    /**
     * 更新订单
     */
    public void updateOrder(Order order) {
        Order oldOrder = orderMap.get(order.getOrderId());
        if (oldOrder == null) {
            return;
        }
        
        // 如果价格发生变化，需要重新排序
        if (!oldOrder.getPrice().equals(order.getPrice())) {
            removeOrder(order.getOrderId());
            addOrder(order);
        } else {
            // 直接更新订单
            orderMap.put(order.getOrderId(), order);
            
            // 更新用户订单列表
            List<Order> userOrderList = userOrders.get(order.getUserId());
            if (userOrderList != null) {
                for (int i = 0; i < userOrderList.size(); i++) {
                    if (userOrderList.get(i).getOrderId().equals(order.getOrderId())) {
                        userOrderList.set(i, order);
                        break;
                    }
                }
            }
            
            // 更新价格队列
            NavigableMap<BigDecimal, LinkedList<Order>> orders = 
                    order.getSide() == OrderSide.BUY ? buyOrders : sellOrders;
            
            LinkedList<Order> priceLevel = orders.get(order.getPrice());
            if (priceLevel != null) {
                for (int i = 0; i < priceLevel.size(); i++) {
                    if (priceLevel.get(i).getOrderId().equals(order.getOrderId())) {
                        priceLevel.set(i, order);
                        break;
                    }
                }
            }
        }
        
        updateLastUpdateTime();
        log.debug("更新订单: symbol={}, orderId={}, price={}, quantity={}", 
                symbol, order.getOrderId(), order.getPrice(), order.getQuantity());
    }
    
    /**
     * 获取订单
     */
    public Order getOrder(String orderId) {
        return orderMap.get(orderId);
    }
    
    /**
     * 获取用户的所有订单
     */
    public List<Order> getUserOrders(Long userId) {
        return userOrders.getOrDefault(userId, new ArrayList<>());
    }
    
    /**
     * 获取买单深度
     */
    public List<PriceLevel> getBuyDepth(int depth) {
        List<PriceLevel> result = new ArrayList<>();
        int count = 0;
        
        for (Map.Entry<BigDecimal, LinkedList<Order>> entry : buyOrders.entrySet()) {
            if (count >= depth) break;
            
            BigDecimal price = entry.getKey();
            BigDecimal totalQuantity = entry.getValue().stream()
                    .map(Order::getQuantity)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            
            result.add(new PriceLevel(price, totalQuantity));
            count++;
        }
        
        return result;
    }
    
    /**
     * 获取卖单深度
     */
    public List<PriceLevel> getSellDepth(int depth) {
        List<PriceLevel> result = new ArrayList<>();
        int count = 0;
        
        for (Map.Entry<BigDecimal, LinkedList<Order>> entry : sellOrders.entrySet()) {
            if (count >= depth) break;
            
            BigDecimal price = entry.getKey();
            BigDecimal totalQuantity = entry.getValue().stream()
                    .map(Order::getQuantity)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            
            result.add(new PriceLevel(price, totalQuantity));
            count++;
        }
        
        return result;
    }
    
    /**
     * 获取最佳买价
     */
    public BigDecimal getBestBid() {
        return buyOrders.isEmpty() ? null : buyOrders.firstKey();
    }
    
    /**
     * 获取最佳卖价
     */
    public BigDecimal getBestAsk() {
        return sellOrders.isEmpty() ? null : sellOrders.firstKey();
    }
    
    /**
     * 获取最佳买价数量
     */
    public BigDecimal getBestBidQuantity() {
        if (buyOrders.isEmpty()) return BigDecimal.ZERO;
        
        return buyOrders.firstEntry().getValue().stream()
                .map(Order::getQuantity)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
    
    /**
     * 获取最佳卖价数量
     */
    public BigDecimal getBestAskQuantity() {
        if (sellOrders.isEmpty()) return BigDecimal.ZERO;
        
        return sellOrders.firstEntry().getValue().stream()
                .map(Order::getQuantity)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
    
    /**
     * 更新成交价
     */
    public void updateLastPrice(BigDecimal price) {
        this.lastPrice = price;
        this.lastUpdateTime = System.currentTimeMillis();
        
        // 更新最高最低价
        if (highPrice == null || price.compareTo(highPrice) > 0) {
            highPrice = price;
        }
        if (lowPrice == null || price.compareTo(lowPrice) < 0) {
            lowPrice = price;
        }
    }
    
    /**
     * 增加成交量
     */
    public void addVolume(BigDecimal quantity) {
        if (volume24h == null) {
            volume24h = BigDecimal.ZERO;
        }
        volume24h = volume24h.add(quantity);
    }
    
    /**
     * 获取订单薄快照
     */
    public OrderBookSnapshot getSnapshot() {
        OrderBookSnapshot snapshot = new OrderBookSnapshot();
        snapshot.setSymbol(symbol);
        snapshot.setLastPrice(lastPrice);
        snapshot.setHighPrice(highPrice);
        snapshot.setLowPrice(lowPrice);
        snapshot.setVolume24h(volume24h);
        snapshot.setCreateTime(createTime);
        snapshot.setLastUpdateTime(lastUpdateTime);
        snapshot.setBuyDepth(getBuyDepth(20));
        snapshot.setSellDepth(getSellDepth(20));
        return snapshot;
    }
    
    /**
     * 获取订单数量
     */
    public int getOrderCount() {
        return orderMap.size();
    }
    
    /**
     * 清空订单薄
     */
    public void clear() {
        buyOrders.clear();
        sellOrders.clear();
        orderMap.clear();
        userOrders.clear();
        updateLastUpdateTime();
        log.info("清空订单薄: symbol={}", symbol);
    }
    
    /**
     * 获取买单队列
     */
    public NavigableMap<BigDecimal, LinkedList<Order>> getBuyOrders() {
        return buyOrders;
    }
    
    /**
     * 获取卖单队列
     */
    public NavigableMap<BigDecimal, LinkedList<Order>> getSellOrders() {
        return sellOrders;
    }
    
    private void updateLastUpdateTime() {
        this.lastUpdateTime = System.currentTimeMillis();
    }
} 