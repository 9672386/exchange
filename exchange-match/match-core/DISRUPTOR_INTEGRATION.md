# Disruptor 撮合引擎整合

## 概述

将撮合逻辑整合到Disruptor事件中，利用Disruptor的高性能特性来提升撮合引擎的并发处理能力。

## 架构设计

### 1. 事件驱动架构

```
客户端请求 → Disruptor RingBuffer → 事件处理器 → 撮合引擎 → 响应结果
```

### 2. 核心组件

- **MatchEvent**: Disruptor事件对象
- **EventHandler**: 事件处理器接口
- **NewOrderEventHandler**: 新订单事件处理器
- **DisruptorService**: Disruptor服务管理

## 事件处理器整合

### 1. NewOrderEventHandler 整合

```java
@Slf4j
@Component
public class NewOrderEventHandler implements EventHandler {
    
    @Autowired
    private MemoryManager memoryManager;
    
    @Autowired
    private OrderMatcherFactory orderMatcherFactory;
    
    @Override
    public void handle(MatchEvent event) {
        try {
            EventNewOrderReq newOrderReq = event.getNewOrderReq();
            
            // 创建订单对象
            Order order = createOrderFromRequest(newOrderReq);
            
            // 执行撮合逻辑
            MatchResponse response = processOrder(order);
            
            // 设置处理结果
            event.setResult(response);
            
        } catch (Exception e) {
            log.error("处理新订单事件失败", e);
            event.setException(e);
        }
    }
}
```

### 2. CanalEventHandler 整合

```java
@Slf4j
@Component
public class CanalEventHandler implements EventHandler {
    
    @Autowired
    private MemoryManager memoryManager;
    
    @Override
    public void handle(MatchEvent event) {
        try {
            EventCanalReq canalReq = event.getCanalReq();
            
            // 执行撤单逻辑
            MatchResponse response = processCancelOrder(canalReq);
            
            // 设置处理结果
            event.setResult(response);
            
        } catch (Exception e) {
            log.error("处理撤单事件失败", e);
            event.setException(e);
        }
    }
}
```

### 3. QueryOrderEventHandler 整合

```java
@Slf4j
@Component
public class QueryOrderEventHandler implements EventHandler {
    
    @Autowired
    private MemoryManager memoryManager;
    
    @Override
    public void handle(MatchEvent event) {
        try {
            EventQueryOrderReq queryOrderReq = event.getQueryOrderReq();
            
            // 执行查询订单逻辑
            QueryOrderResponse response = processQueryOrder(queryOrderReq);
            
            // 设置处理结果
            event.setResult(response);
            
        } catch (Exception e) {
            log.error("处理查询订单事件失败", e);
            event.setException(e);
        }
    }
}
```

### 4. QueryPositionEventHandler 整合

```java
@Slf4j
@Component
public class QueryPositionEventHandler implements EventHandler {
    
    @Autowired
    private MemoryManager memoryManager;
    
    @Override
    public void handle(MatchEvent event) {
        try {
            EventQueryPositionReq queryPositionReq = event.getQueryPositionReq();
            
            // 执行查询持仓逻辑
            QueryPositionResponse response = processQueryPosition(queryPositionReq);
            
            // 设置处理结果
            event.setResult(response);
            
        } catch (Exception e) {
            log.error("处理查询持仓事件失败", e);
            event.setException(e);
        }
    }
}
```

### 2. 撮合逻辑整合

```java
/**
 * 处理订单撮合逻辑
 */
private MatchResponse processOrder(Order order) {
    MatchResponse response = new MatchResponse();
    
    try {
        // 1. 验证标的
        Symbol symbol = memoryManager.getSymbol(order.getSymbol());
        if (!validateSymbol(symbol, response)) {
            return response;
        }
        
        // 2. 验证订单参数
        if (!validateOrder(order, symbol, response)) {
            return response;
        }
        
        // 3. 格式化价格和数量
        formatOrder(order, symbol);
        
        // 4. 设置开平仓动作
        setPositionAction(order, symbol);
        
        // 5. 执行撮合
        List<Trade> trades = executeMatching(order, symbol);
        
        // 6. 更新响应信息
        updateResponse(response, order, trades, symbol);
        
    } catch (Exception e) {
        handleError(response, e);
    }
    
    return response;
}
```

## 事件请求对象

### 1. EventNewOrderReq 扩展

```java
@Data
public class EventNewOrderReq implements Serializable {
    private String orderId;
    private long userId;
    private String symbol;
    private String side; // 使用字符串，在handler中转换
    private String orderType;
    private BigDecimal price;
    private BigDecimal quantity;
    private String positionAction;
    private String clientOrderId;
    private String remark;
}
```

### 2. 字符串到枚举转换

```java
private Order createOrderFromRequest(EventNewOrderReq newOrderReq) {
    Order order = new Order();
    order.setOrderId(newOrderReq.getOrderId());
    order.setUserId(newOrderReq.getUserId());
    order.setSymbol(newOrderReq.getSymbol());
    
    // 转换字符串为枚举
    if (newOrderReq.getSide() != null) {
        order.setSide(OrderSide.valueOf(newOrderReq.getSide()));
    }
    if (newOrderReq.getOrderType() != null) {
        order.setType(OrderType.valueOf(newOrderReq.getOrderType()));
    }
    if (newOrderReq.getPositionAction() != null) {
        order.setPositionAction(PositionAction.valueOf(newOrderReq.getPositionAction()));
    }
    
    order.setPrice(newOrderReq.getPrice());
    order.setQuantity(newOrderReq.getQuantity());
    order.setClientOrderId(newOrderReq.getClientOrderId());
    order.setRemark(newOrderReq.getRemark());
    return order;
}
```

## 撮合逻辑模块化

### 1. 验证模块

```java
/**
 * 验证标的
 */
private boolean validateSymbol(Symbol symbol, MatchResponse response) {
    if (symbol == null) {
        response.setStatus(MatchStatus.REJECTED);
        response.setErrorMessage("标的不存在");
        response.setRejectInfo(createRejectInfo(
            MatchResponse.RejectInfo.RejectType.SYMBOL_NOT_TRADABLE,
            "标的不存在"
        ));
        return false;
    }
    
    if (!symbol.isTradeable()) {
        response.setStatus(MatchStatus.REJECTED);
        response.setErrorMessage("标的不可交易");
        response.setRejectInfo(createRejectInfo(
            MatchResponse.RejectInfo.RejectType.MARKET_CLOSED,
            "标的不可交易"
        ));
        return false;
    }
    
    return true;
}

/**
 * 验证订单参数
 */
private boolean validateOrder(Order order, Symbol symbol, MatchResponse response) {
    if (!symbol.isValidPrice(order.getPrice())) {
        response.setStatus(MatchStatus.REJECTED);
        response.setErrorMessage("价格无效");
        response.setRejectInfo(createRejectInfo(
            MatchResponse.RejectInfo.RejectType.INVALID_PRICE,
            "价格无效"
        ));
        return false;
    }
    
    if (!symbol.isValidQuantity(order.getQuantity())) {
        response.setStatus(MatchStatus.REJECTED);
        response.setErrorMessage("数量无效");
        response.setRejectInfo(createRejectInfo(
            MatchResponse.RejectInfo.RejectType.INVALID_QUANTITY,
            "数量无效"
        ));
        return false;
    }
    
    return true;
}
```

### 2. 撮合执行模块

```java
/**
 * 执行订单撮合
 */
private List<Trade> executeMatching(Order order, Symbol symbol) {
    // 获取订单薄
    OrderBook orderBook = memoryManager.getOrCreateOrderBook(order.getSymbol());
    
    // 根据订单类型获取对应的匹配器
    OrderMatcher matcher = orderMatcherFactory.getMatcher(order.getType());
    
    // 执行撮合
    List<Trade> trades = matcher.matchOrder(order, orderBook, symbol);
    
    // 更新仓位（仅合约交易）
    if (symbol.supportsPosition()) {
        for (Trade trade : trades) {
            updatePositionsFromTrade(trade);
        }
    }
    
    return trades;
}
```

### 3. 响应更新模块

```java
/**
 * 更新响应信息
 */
private void updateResponse(MatchResponse response, Order order, List<Trade> trades, Symbol symbol) {
    response.setTrades(trades);
    response.setMatchQuantity(order.getFilledQuantity());
    response.setRemainingQuantity(order.getRemainingQuantity());
    
    // 计算成交价格和金额
    if (!trades.isEmpty()) {
        BigDecimal totalAmount = trades.stream()
                .map(Trade::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalQuantity = trades.stream()
                .map(Trade::getQuantity)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        response.setMatchAmount(totalAmount);
        response.setMatchPrice(totalAmount.divide(totalQuantity, symbol.getPricePrecision(), BigDecimal.ROUND_HALF_UP));
        response.setFee(symbol.calculateFee(totalAmount, BigDecimal.ONE));
    }
    
    // 更新状态
    if (order.isFullyFilled()) {
        response.setStatus(MatchStatus.SUCCESS);
    } else if (order.isPartiallyFilled()) {
        response.setStatus(MatchStatus.PARTIALLY_FILLED);
    } else {
        response.setStatus(MatchStatus.PENDING);
    }
    
    // 更新仓位变化信息（仅合约交易）
    if (!trades.isEmpty() && symbol.supportsPosition()) {
        try {
            response.setPositionChange(calculatePositionChange(order, trades));
        } catch (IllegalArgumentException e) {
            response.setStatus(MatchStatus.REJECTED);
            response.setErrorMessage(e.getMessage());
            response.setRejectInfo(createRejectInfo(
                MatchResponse.RejectInfo.RejectType.INVALID_POSITION_ACTION,
                e.getMessage()
            ));
        }
    }
}
```

## 性能优化

### 1. 内存管理

```java
/**
 * 事件清理
 */
public void clear() {
    this.eventType = null;
    this.eventReq = null;
    this.newOrderReq = null;
    this.result = null;
    this.exception = null;
    this.timestamp = 0;
}
```

### 2. 异常处理

```java
/**
 * 错误处理
 */
private void handleError(MatchResponse response, Exception e) {
    log.error("处理订单失败", e);
    response.setStatus(MatchStatus.REJECTED);
    response.setErrorMessage("系统错误: " + e.getMessage());
    response.setRejectInfo(createRejectInfo(
        MatchResponse.RejectInfo.RejectType.SYSTEM_ERROR,
        "系统错误: " + e.getMessage()
    ));
}
```

### 3. 日志优化

```java
// 使用结构化日志
log.info("处理新订单事件: orderId={}, userId={}, symbol={}", 
        newOrderReq.getOrderId(), newOrderReq.getUserId(), newOrderReq.getSymbol());

log.info("新订单处理完成: orderId={}, status={}, filled={}, remaining={}", 
        order.getOrderId(), response.getStatus(), 
        response.getMatchQuantity(), response.getRemainingQuantity());
```

## 使用示例

### 1. 新订单事件

```java
// 创建新订单事件
MatchEvent event = new MatchEvent();
event.setEventType(EventType.NEW_ORDER);

EventNewOrderReq newOrderReq = new EventNewOrderReq();
newOrderReq.setOrderId("ORDER_001");
newOrderReq.setUserId(1001L);
newOrderReq.setSymbol("BTC/USDT");
newOrderReq.setSide("BUY");
newOrderReq.setOrderType("LIMIT");
newOrderReq.setPrice(new BigDecimal("50000"));
newOrderReq.setQuantity(new BigDecimal("1.0"));
newOrderReq.setPositionAction("OPEN");

event.setNewOrderReq(newOrderReq);

// 发布事件
disruptorService.publishEvent(event);

// 获取处理结果
MatchResponse response = (MatchResponse) event.getResult();
if (response.isSuccess()) {
    System.out.println("订单处理成功");
    System.out.println("成交数量: " + response.getMatchQuantity());
    System.out.println("成交金额: " + response.getMatchAmount());
} else {
    System.out.println("订单处理失败: " + response.getErrorMessage());
}
```

### 2. 撤单事件

```java
// 创建撤单事件
MatchEvent cancelEvent = new MatchEvent();
cancelEvent.setEventType(EventType.CANAL);

EventCanalReq canalReq = new EventCanalReq();
canalReq.setOrderId("ORDER_001");
canalReq.setUserId(1001L);
canalReq.setSymbol("BTC/USDT");

cancelEvent.setCanalReq(canalReq);

// 发布事件
disruptorService.publishEvent(cancelEvent);

// 获取处理结果
MatchResponse cancelResponse = (MatchResponse) cancelEvent.getResult();
if (cancelResponse.isCancelled()) {
    System.out.println("撤单成功");
    System.out.println("撤单数量: " + cancelResponse.getCancelInfo().getCancelQuantity());
} else {
    System.out.println("撤单失败: " + cancelResponse.getErrorMessage());
}
```

### 3. 查询订单事件

```java
// 创建查询订单事件
MatchEvent queryOrderEvent = new MatchEvent();
queryOrderEvent.setEventType(EventType.QUERY_ORDER);

EventQueryOrderReq queryOrderReq = new EventQueryOrderReq();
queryOrderReq.setUserId(1001L);
queryOrderReq.setSymbol("BTC/USDT");
queryOrderReq.setOrderType(-1); // -1表示查询所有类型

queryOrderEvent.setQueryOrderReq(queryOrderReq);

// 发布事件
disruptorService.publishEvent(queryOrderEvent);

// 获取处理结果
QueryOrderEventHandler.QueryOrderResponse queryOrderResponse = 
    (QueryOrderEventHandler.QueryOrderResponse) queryOrderEvent.getResult();
if (queryOrderResponse.isSuccess()) {
    System.out.println("查询订单成功");
    System.out.println("订单数量: " + queryOrderResponse.getTotalCount());
    for (Order order : queryOrderResponse.getOrders()) {
        System.out.println("订单: " + order.getOrderId() + ", 状态: " + order.getStatus());
    }
} else {
    System.out.println("查询订单失败: " + queryOrderResponse.getErrorMessage());
}
```

### 4. 查询持仓事件

```java
// 创建查询持仓事件
MatchEvent queryPositionEvent = new MatchEvent();
queryPositionEvent.setEventType(EventType.QUERY_POSITION);

EventQueryPositionReq queryPositionReq = new EventQueryPositionReq();
queryPositionReq.setUserId(1001L);
queryPositionReq.setSymbol("BTC/USDT");

queryPositionEvent.setQueryPositionReq(queryPositionReq);

// 发布事件
disruptorService.publishEvent(queryPositionEvent);

// 获取处理结果
QueryPositionEventHandler.QueryPositionResponse queryPositionResponse = 
    (QueryPositionEventHandler.QueryPositionResponse) queryPositionEvent.getResult();
if (queryPositionResponse.isSuccess()) {
    System.out.println("查询持仓成功");
    System.out.println("持仓数量: " + queryPositionResponse.getTotalCount());
    for (Position position : queryPositionResponse.getPositions()) {
        System.out.println("持仓: " + position.getSymbol() + ", 数量: " + position.getQuantity());
    }
} else {
    System.out.println("查询持仓失败: " + queryPositionResponse.getErrorMessage());
}
```

## 监控和指标

### 1. 性能指标

```java
// 事件处理时间
long startTime = System.currentTimeMillis();
// ... 处理逻辑
long endTime = System.currentTimeMillis();
long processingTime = endTime - startTime;

// 记录性能指标
metrics.recordEventProcessingTime(EventType.NEW_ORDER, processingTime);
```

### 2. 错误监控

```java
// 错误计数
if (event.getException() != null) {
    metrics.incrementErrorCount(EventType.NEW_ORDER);
    log.error("事件处理失败", event.getException());
}
```

### 3. 吞吐量监控

```java
// 事件处理计数
metrics.incrementEventCount(EventType.NEW_ORDER);
```

## 总结

Disruptor整合的优势：

1. **高性能**: 利用Disruptor的无锁队列提升并发性能
2. **低延迟**: 减少线程切换和锁竞争
3. **高吞吐**: 支持高并发事件处理
4. **模块化**: 将撮合逻辑模块化，便于维护和扩展
5. **可监控**: 提供详细的性能指标和错误监控

这种架构确保了撮合引擎能够处理高并发的订单请求，同时保持低延迟和高可靠性。 