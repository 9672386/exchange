package com.exchange.match.core.model;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 订单薄时间优先测试
 */
public class OrderBookTimePriorityTest {
    
    private OrderBook orderBook;
    
    @BeforeEach
    public void setUp() {
        orderBook = new OrderBook("BTC/USDT");
    }
    
    @Test
    public void testTimePriorityForBuyOrders() {
        // 创建相同价格的买单，但时间不同
        Order order1 = createOrder("BUY_001", 12345L, "BTC/USDT", OrderSide.BUY, 
                new BigDecimal("50000"), new BigDecimal("1.0"));
        order1.setCreateTime(LocalDateTime.now().minusSeconds(2));
        
        Order order2 = createOrder("BUY_002", 12346L, "BTC/USDT", OrderSide.BUY, 
                new BigDecimal("50000"), new BigDecimal("1.0"));
        order2.setCreateTime(LocalDateTime.now().minusSeconds(1));
        
        Order order3 = createOrder("BUY_003", 12347L, "BTC/USDT", OrderSide.BUY, 
                new BigDecimal("50000"), new BigDecimal("1.0"));
        order3.setCreateTime(LocalDateTime.now());
        
        // 按时间倒序添加订单（最晚的先添加）
        orderBook.addOrder(order3);
        orderBook.addOrder(order2);
        orderBook.addOrder(order1);
        
        // 验证订单按时间顺序排列（先到先得）
        List<PriceLevel> buyDepth = orderBook.getBuyDepth(1);
        assertEquals(1, buyDepth.size());
        assertEquals(new BigDecimal("50000"), buyDepth.get(0).getPrice());
        assertEquals(new BigDecimal("3.0"), buyDepth.get(0).getTotalQuantity());
        
        // 验证订单在LinkedList中的顺序
        List<Order> orders = orderBook.getBuyOrders().get(new BigDecimal("50000"));
        assertNotNull(orders);
        assertEquals(3, orders.size());
        
        // 验证第一个订单是最早的（order1）
        assertEquals("BUY_001", orders.get(0).getOrderId());
        assertEquals("BUY_002", orders.get(1).getOrderId());
        assertEquals("BUY_003", orders.get(2).getOrderId());
    }
    
    @Test
    public void testTimePriorityForSellOrders() {
        // 创建相同价格的卖单，但时间不同
        Order order1 = createOrder("SELL_001", 12345L, "BTC/USDT", OrderSide.SELL, 
                new BigDecimal("50001"), new BigDecimal("1.0"));
        order1.setCreateTime(LocalDateTime.now().minusSeconds(2));
        
        Order order2 = createOrder("SELL_002", 12346L, "BTC/USDT", OrderSide.SELL, 
                new BigDecimal("50001"), new BigDecimal("1.0"));
        order2.setCreateTime(LocalDateTime.now().minusSeconds(1));
        
        Order order3 = createOrder("SELL_003", 12347L, "BTC/USDT", OrderSide.SELL, 
                new BigDecimal("50001"), new BigDecimal("1.0"));
        order3.setCreateTime(LocalDateTime.now());
        
        // 按时间倒序添加订单（最晚的先添加）
        orderBook.addOrder(order3);
        orderBook.addOrder(order2);
        orderBook.addOrder(order1);
        
        // 验证订单按时间顺序排列（先到先得）
        List<PriceLevel> sellDepth = orderBook.getSellDepth(1);
        assertEquals(1, sellDepth.size());
        assertEquals(new BigDecimal("50001"), sellDepth.get(0).getPrice());
        assertEquals(new BigDecimal("3.0"), sellDepth.get(0).getTotalQuantity());
        
        // 验证订单在LinkedList中的顺序
        List<Order> orders = orderBook.getSellOrders().get(new BigDecimal("50001"));
        assertNotNull(orders);
        assertEquals(3, orders.size());
        
        // 验证第一个订单是最早的（order1）
        assertEquals("SELL_001", orders.get(0).getOrderId());
        assertEquals("SELL_002", orders.get(1).getOrderId());
        assertEquals("SELL_003", orders.get(2).getOrderId());
    }
    
    @Test
    public void testPriceTimePriority() {
        // 创建不同价格和时间的订单
        Order order1 = createOrder("BUY_001", 12345L, "BTC/USDT", OrderSide.BUY, 
                new BigDecimal("50000"), new BigDecimal("1.0"));
        order1.setCreateTime(LocalDateTime.now().minusSeconds(3));
        
        Order order2 = createOrder("BUY_002", 12346L, "BTC/USDT", OrderSide.BUY, 
                new BigDecimal("50001"), new BigDecimal("1.0"));
        order2.setCreateTime(LocalDateTime.now().minusSeconds(2));
        
        Order order3 = createOrder("BUY_003", 12347L, "BTC/USDT", OrderSide.BUY, 
                new BigDecimal("50000"), new BigDecimal("1.0"));
        order3.setCreateTime(LocalDateTime.now().minusSeconds(1));
        
        // 添加订单
        orderBook.addOrder(order1);
        orderBook.addOrder(order2);
        orderBook.addOrder(order3);
        
        // 验证价格优先（50001 > 50000）
        List<PriceLevel> buyDepth = orderBook.getBuyDepth(2);
        assertEquals(2, buyDepth.size());
        
        // 第一个价格层应该是50001
        assertEquals(new BigDecimal("50001"), buyDepth.get(0).getPrice());
        assertEquals(new BigDecimal("1.0"), buyDepth.get(0).getTotalQuantity());
        
        // 第二个价格层应该是50000
        assertEquals(new BigDecimal("50000"), buyDepth.get(1).getPrice());
        assertEquals(new BigDecimal("2.0"), buyDepth.get(1).getTotalQuantity());
        
        // 验证同价格内的时间优先
        List<Order> price50000Orders = orderBook.getBuyOrders().get(new BigDecimal("50000"));
        assertEquals(2, price50000Orders.size());
        assertEquals("BUY_001", price50000Orders.get(0).getOrderId()); // 最早的
        assertEquals("BUY_003", price50000Orders.get(1).getOrderId()); // 较晚的
    }
    
    @Test
    public void testOrderRemovalMaintainsTimePriority() {
        // 创建相同价格的订单
        Order order1 = createOrder("BUY_001", 12345L, "BTC/USDT", OrderSide.BUY, 
                new BigDecimal("50000"), new BigDecimal("1.0"));
        order1.setCreateTime(LocalDateTime.now().minusSeconds(2));
        
        Order order2 = createOrder("BUY_002", 12346L, "BTC/USDT", OrderSide.BUY, 
                new BigDecimal("50000"), new BigDecimal("1.0"));
        order2.setCreateTime(LocalDateTime.now().minusSeconds(1));
        
        Order order3 = createOrder("BUY_003", 12347L, "BTC/USDT", OrderSide.BUY, 
                new BigDecimal("50000"), new BigDecimal("1.0"));
        order3.setCreateTime(LocalDateTime.now());
        
        // 添加订单
        orderBook.addOrder(order1);
        orderBook.addOrder(order2);
        orderBook.addOrder(order3);
        
        // 移除中间的订单
        orderBook.removeOrder("BUY_002");
        
        // 验证剩余订单的顺序
        List<Order> orders = orderBook.getBuyOrders().get(new BigDecimal("50000"));
        assertEquals(2, orders.size());
        assertEquals("BUY_001", orders.get(0).getOrderId());
        assertEquals("BUY_003", orders.get(1).getOrderId());
        
        // 验证总数量
        List<PriceLevel> buyDepth = orderBook.getBuyDepth(1);
        assertEquals(new BigDecimal("2.0"), buyDepth.get(0).getTotalQuantity());
    }
    
    private Order createOrder(String orderId, Long userId, String symbol, OrderSide side, 
                            BigDecimal price, BigDecimal quantity) {
        Order order = new Order();
        order.setOrderId(orderId);
        order.setUserId(userId);
        order.setSymbol(symbol);
        order.setSide(side);
        order.setType(OrderType.LIMIT);
        order.setPrice(price);
        order.setQuantity(quantity);
        order.setStatus(OrderStatus.ACTIVE);
        return order;
    }
} 