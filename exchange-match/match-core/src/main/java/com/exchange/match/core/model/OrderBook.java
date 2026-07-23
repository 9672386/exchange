package com.exchange.match.core.model;

import com.exchange.match.enums.OrderSide;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 订单薄内存模型（撮合内部,价/量为定点 long raw）。
 * 管理买卖订单队列，支持快速撮合。
 *
 * <p><b>定点</b>:价格档 key = priceScale raw,数量 = baseScale raw。
 * 价格档用 {@code long} 自然序(买单 {@link Collections#reverseOrder()}),
 * 比较无需 BigDecimal。对外行情查询在 controller 边界转 BigDecimal。
 */
@Slf4j
@Data
public class OrderBook {

    /**
     * 交易对
     */
    private String symbol;

    /**
     * 买单队列（价格从高到低排序，同价格按时间排序）。
     *
     * <p>外层 Map 使用 {@link ConcurrentSkipListMap} 保证价格层级的并发安全；
     * 内层 List 使用 {@link CopyOnWriteArrayList}：
     * Disruptor 单线程写（addOrder/removeOrder/updateOrder）+ REST 多线程读（深度查询）。
     */
    private final NavigableMap<Long, CopyOnWriteArrayList<Order>> buyOrders =
            new ConcurrentSkipListMap<>(Collections.reverseOrder());

    /**
     * 卖单队列（价格从低到高排序，同价格按时间排序）。
     * 线程安全策略同 {@link #buyOrders}。
     */
    private final NavigableMap<Long, CopyOnWriteArrayList<Order>> sellOrders =
            new ConcurrentSkipListMap<>();

    /**
     * 订单ID到订单的映射
     */
    private final Map<String, Order> orderMap = new ConcurrentHashMap<>();

    /**
     * 用户ID到订单列表的映射。
     */
    private final Map<Long, List<Order>> userOrders = new ConcurrentHashMap<>();

    /**
     * 最新成交价（priceScale raw，0 表示尚无成交）
     */
    private long lastPrice;

    /**
     * 24小时最高价（priceScale raw，0 表示未设置——价格恒正,可用 0 作哨兵）
     */
    private long highPrice;

    /**
     * 24小时最低价（priceScale raw，0 表示未设置）
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

        userOrders.computeIfAbsent(order.getUserId(), k -> new CopyOnWriteArrayList<>()).add(order);

        NavigableMap<Long, CopyOnWriteArrayList<Order>> orders =
                order.getSide() == OrderSide.BUY ? buyOrders : sellOrders;

        orders.computeIfAbsent(order.getPrice(), k -> new CopyOnWriteArrayList<>()).add(order);

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

        List<Order> userOrderList = userOrders.get(order.getUserId());
        if (userOrderList != null) {
            userOrderList.removeIf(o -> o.getOrderId().equals(orderId));
            if (userOrderList.isEmpty()) {
                userOrders.remove(order.getUserId());
            }
        }

        NavigableMap<Long, CopyOnWriteArrayList<Order>> orders =
                order.getSide() == OrderSide.BUY ? buyOrders : sellOrders;

        CopyOnWriteArrayList<Order> priceLevel = orders.get(order.getPrice());
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
        if (oldOrder.getPrice() != order.getPrice()) {
            removeOrder(order.getOrderId());
            addOrder(order);
        } else {
            orderMap.put(order.getOrderId(), order);

            List<Order> userOrderList = userOrders.get(order.getUserId());
            if (userOrderList != null) {
                for (int i = 0; i < userOrderList.size(); i++) {
                    if (userOrderList.get(i).getOrderId().equals(order.getOrderId())) {
                        userOrderList.set(i, order);
                        break;
                    }
                }
            }

            NavigableMap<Long, CopyOnWriteArrayList<Order>> orders =
                    order.getSide() == OrderSide.BUY ? buyOrders : sellOrders;

            CopyOnWriteArrayList<Order> priceLevel = orders.get(order.getPrice());
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

        for (Map.Entry<Long, CopyOnWriteArrayList<Order>> entry : buyOrders.entrySet()) {
            if (count >= depth) break;

            long price = entry.getKey();
            long totalQuantity = entry.getValue().stream()
                    .mapToLong(Order::getQuantity)
                    .reduce(0L, Math::addExact);

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

        for (Map.Entry<Long, CopyOnWriteArrayList<Order>> entry : sellOrders.entrySet()) {
            if (count >= depth) break;

            long price = entry.getKey();
            long totalQuantity = entry.getValue().stream()
                    .mapToLong(Order::getQuantity)
                    .reduce(0L, Math::addExact);

            result.add(new PriceLevel(price, totalQuantity));
            count++;
        }

        return result;
    }

    /**
     * 获取最佳买价（priceScale raw；无买单返回 null）
     */
    public Long getBestBid() {
        return buyOrders.isEmpty() ? null : buyOrders.firstKey();
    }

    /**
     * 获取最佳卖价（priceScale raw；无卖单返回 null）
     */
    public Long getBestAsk() {
        return sellOrders.isEmpty() ? null : sellOrders.firstKey();
    }

    /**
     * 获取最佳买价数量（baseScale raw）
     */
    public long getBestBidQuantity() {
        if (buyOrders.isEmpty()) return 0L;

        return buyOrders.firstEntry().getValue().stream()
                .mapToLong(Order::getQuantity)
                .reduce(0L, Math::addExact);
    }

    /**
     * 获取最佳卖价数量（baseScale raw）
     */
    public long getBestAskQuantity() {
        if (sellOrders.isEmpty()) return 0L;

        return sellOrders.firstEntry().getValue().stream()
                .mapToLong(Order::getQuantity)
                .reduce(0L, Math::addExact);
    }

    /**
     * 更新成交价（priceScale raw）
     */
    public void updateLastPrice(long price) {
        this.lastPrice = price;
        this.lastUpdateTime = System.currentTimeMillis();

        // 价格恒正,0 作"未设置"哨兵
        if (highPrice == 0 || price > highPrice) {
            highPrice = price;
        }
        if (lowPrice == 0 || price < lowPrice) {
            lowPrice = price;
        }
    }

    /**
     * 增加成交量（baseScale raw）
     */
    public void addVolume(long quantity) {
        volume24h = Math.addExact(volume24h, quantity);
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
     * 获取买单队列（外层 ConcurrentSkipListMap + 内层 CopyOnWriteArrayList，读写均线程安全）。
     */
    public NavigableMap<Long, CopyOnWriteArrayList<Order>> getBuyOrders() {
        return buyOrders;
    }

    /**
     * 获取卖单队列（外层 ConcurrentSkipListMap + 内层 CopyOnWriteArrayList，读写均线程安全）。
     */
    public NavigableMap<Long, CopyOnWriteArrayList<Order>> getSellOrders() {
        return sellOrders;
    }

    private void updateLastUpdateTime() {
        this.lastUpdateTime = System.currentTimeMillis();
    }
}
