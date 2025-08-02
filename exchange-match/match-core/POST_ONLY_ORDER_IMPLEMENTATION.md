# POST_ONLY订单实现

## 概述

POST_ONLY订单是一种特殊的订单类型，只做挂单（Maker），不做吃单（Taker）。如果订单会立即成交，则拒绝该订单。这种订单类型通常用于：

1. **降低手续费**：Maker订单通常享受更低的手续费率
2. **提供流动性**：主动为市场提供流动性
3. **避免滑点**：确保订单不会立即成交，避免意外滑点

## 订单类型定义

在 `OrderType` 枚举中添加了：

```java
POST_ONLY("只做挂单")
```

## 核心逻辑

### 1. 预处理检查

```java
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
```

### 2. 撮合逻辑

```java
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
```

### 3. 后处理逻辑

```java
@Override
protected void postMatch(Order order, OrderBook orderBook, Symbol symbol, List<Trade> trades) {
    // POST_ONLY订单只做挂单，添加到订单薄
    if (order.getRemainingQuantity().compareTo(BigDecimal.ZERO) > 0) {
        orderBook.addOrder(order);
        log.debug("POST_ONLY订单已挂单: orderId={}, price={}, quantity={}", 
                order.getOrderId(), order.getPrice(), order.getRemainingQuantity());
    }
}
```

## 价格检查规则

### 1. 买单检查

- **条件**：订单价格 >= 最优卖价
- **结果**：拒绝订单
- **原因**：会立即成交，不符合POST_ONLY要求

**示例：**
```
最优卖价：100 USDT
POST_ONLY买单价格：101 USDT
结果：拒绝订单（会立即成交）
```

### 2. 卖单检查

- **条件**：订单价格 <= 最优买价
- **结果**：拒绝订单
- **原因**：会立即成交，不符合POST_ONLY要求

**示例：**
```
最优买价：100 USDT
POST_ONLY卖单价格：99 USDT
结果：拒绝订单（会立即成交）
```

## 使用场景

### 1. 降低手续费

```java
// 用户希望享受Maker手续费率
Order postOnlyOrder = new Order();
postOnlyOrder.setType(OrderType.POST_ONLY);
postOnlyOrder.setPrice(new BigDecimal("99.5")); // 低于最优买价
postOnlyOrder.setSide(OrderSide.SELL);
// 订单会被挂单，享受Maker费率
```

### 2. 提供流动性

```java
// 做市商提供流动性
Order liquidityOrder = new Order();
liquidityOrder.setType(OrderType.POST_ONLY);
liquidityOrder.setPrice(new BigDecimal("100.5")); // 高于最优卖价
liquidityOrder.setSide(OrderSide.BUY);
// 订单会被挂单，为市场提供流动性
```

### 3. 避免意外成交

```java
// 用户不希望订单立即成交
Order safeOrder = new Order();
safeOrder.setType(OrderType.POST_ONLY);
safeOrder.setPrice(new BigDecimal("98.0")); // 远低于最优买价
safeOrder.setSide(OrderSide.SELL);
// 确保订单不会立即成交
```

## 错误处理

### 1. 订单拒绝

当POST_ONLY订单会立即成交时：

```java
log.warn("POST_ONLY买单会立即成交，拒绝订单: orderId={}, price={}, bestAsk={}", 
        order.getOrderId(), order.getPrice(), bestAskPrice);
```

### 2. 成功挂单

当POST_ONLY订单成功挂单时：

```java
log.debug("POST_ONLY订单已挂单: orderId={}, price={}, quantity={}", 
        order.getOrderId(), order.getPrice(), order.getRemainingQuantity());
```

## 与其他订单类型的区别

| 订单类型 | 是否立即成交 | 是否挂单 | 手续费率 |
|---------|-------------|---------|---------|
| MARKET | 是 | 否 | Taker费率 |
| LIMIT | 可能 | 是 | 根据成交方式 |
| POST_ONLY | 否 | 是 | Maker费率 |
| FOK | 是（全部成交） | 否 | Taker费率 |
| IOC | 是（部分成交） | 否 | Taker费率 |

## 最佳实践

### 1. 价格设置

```java
// 买单：价格设置为低于最优卖价
BigDecimal bestAsk = orderBook.getBestAsk();
BigDecimal postOnlyPrice = bestAsk.subtract(new BigDecimal("0.01")); // 低1分钱

// 卖单：价格设置为高于最优买价
BigDecimal bestBid = orderBook.getBestBid();
BigDecimal postOnlyPrice = bestBid.add(new BigDecimal("0.01")); // 高1分钱
```

### 2. 监控和告警

```java
// 监控POST_ONLY订单拒绝率
if (postOnlyRejectRate > 0.1) { // 拒绝率超过10%
    log.warn("POST_ONLY订单拒绝率过高: {}", postOnlyRejectRate);
}
```

### 3. 用户体验

```java
// 向用户提供清晰的错误信息
if (orderRejected) {
    return new OrderResponse(
        OrderStatus.REJECTED,
        "POST_ONLY订单会立即成交，已拒绝。请调整价格后重试。"
    );
}
```

## 总结

POST_ONLY订单类型通过以下方式为用户提供价值：

1. **手续费优惠**：享受Maker费率，降低交易成本
2. **流动性提供**：主动为市场提供流动性
3. **风险控制**：避免意外成交，保护用户利益
4. **价格控制**：确保订单按预期价格挂单

这种订单类型特别适合做市商和希望降低交易成本的用户使用。 