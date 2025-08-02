# 开平仓功能实现

## 概述

在期货交易中，开平仓是核心概念。开仓是指建立新的仓位，平仓是指关闭现有仓位。系统现在支持完整的开平仓功能，采用更清晰的设计架构。

## 设计架构

### 1. 分离关注点

新的设计将开平仓动作和买卖方向分离，通过组合计算最终的仓位变化：

- **PositionAction**：纯粹的开平仓动作（OPEN/CLOSE）
- **OrderSide**：买卖方向（BUY/SELL）
- **PositionSide**：仓位方向（LONG/SHORT）

### 2. PositionAction 枚举

```java
public enum PositionAction {
    OPEN("开仓"),      // 建立新仓位
    CLOSE("平仓");     // 关闭现有仓位
}
```

### 3. 仓位变化计算逻辑

```java
public static PositionChangeResult calculatePositionChange(
        OrderSide orderSide, 
        PositionAction positionAction, 
        PositionSide currentPositionSide,
        BigDecimal quantity) {
    
    if (positionAction == OPEN) {
        // 开仓逻辑
        if (orderSide == OrderSide.BUY) {
            // 买单开仓 = 开多仓
            return new PositionChangeResult(PositionSide.LONG, quantity, true, null);
        } else {
            // 卖单开仓 = 开空仓
            return new PositionChangeResult(PositionSide.SHORT, quantity, true, null);
        }
    } else if (positionAction == CLOSE) {
        // 平仓逻辑
        if (orderSide == OrderSide.BUY) {
            // 买单平仓 = 平空仓
            if (currentPositionSide == PositionSide.SHORT) {
                return new PositionChangeResult(PositionSide.SHORT, quantity.negate(), true, null);
            } else {
                return new PositionChangeResult(null, BigDecimal.ZERO, false, "买单平仓但当前无空仓");
            }
        } else {
            // 卖单平仓 = 平多仓
            if (currentPositionSide == PositionSide.LONG) {
                return new PositionChangeResult(PositionSide.LONG, quantity.negate(), true, null);
            } else {
                return new PositionChangeResult(null, BigDecimal.ZERO, false, "卖单平仓但当前无多仓");
            }
        }
    }
}
```

## 订单模型扩展

### 1. Order 模型添加开平仓字段

```java
@Data
public class Order {
    // ... 其他字段
    
    /**
     * 开平仓动作
     */
    private PositionAction positionAction;
    
    public Order() {
        // ... 其他初始化
        this.positionAction = PositionAction.OPEN; // 默认为开仓
    }
}
```

### 2. Trade 模型添加开平仓信息

```java
@Data
public class Trade {
    // ... 其他字段
    
    /**
     * 买方开平仓动作
     */
    private PositionAction buyPositionAction;
    
    /**
     * 卖方开平仓动作
     */
    private PositionAction sellPositionAction;
    
    /**
     * 买方仓位变化
     */
    private BigDecimal buyPositionChange;
    
    /**
     * 卖方仓位变化
     */
    private BigDecimal sellPositionChange;
}
```

## 撮合逻辑更新

### 1. 成交记录创建

```java
protected Trade createTrade(Order buyOrder, Order sellOrder, BigDecimal price, 
                          BigDecimal quantity, Symbol symbol) {
    Trade trade = new Trade();
    // ... 基本信息设置
    
    // 设置开平仓动作
    trade.setBuyPositionAction(buyOrder.getPositionAction());
    trade.setSellPositionAction(sellOrder.getPositionAction());
    
    // 计算仓位变化
    trade.setBuyPositionChange(calculatePositionChange(buyOrder, quantity));
    trade.setSellPositionChange(calculatePositionChange(sellOrder, quantity));
    
    return trade;
}

private BigDecimal calculatePositionChange(Order order, BigDecimal quantity) {
    // 获取当前仓位
    Position currentPosition = memoryManager.getPosition(order.getUserId(), order.getSymbol());
    PositionSide currentPositionSide = currentPosition != null ? currentPosition.getSide() : null;
    
    // 使用新的计算逻辑
    PositionAction.PositionChangeResult result = PositionAction.calculatePositionChange(
        order.getSide(), 
        order.getPositionAction(), 
        currentPositionSide,
        quantity
    );
    
    if (!result.isValid()) {
        throw new IllegalArgumentException("无效的开平仓操作: " + result.getReason());
    }
    
    return result.getQuantityChange();
}
```

### 2. 仓位更新逻辑

```java
private void updatePositionsFromTrade(Trade trade) {
    // 更新买方仓位
    Position buyPosition = memoryManager.getOrCreatePosition(trade.getBuyUserId(), trade.getSymbol());
    if (trade.getBuyPositionAction().isOpen()) {
        buyPosition.openPosition(trade.getQuantity(), trade.getPrice());
    } else {
        buyPosition.closePosition(trade.getQuantity(), trade.getPrice());
    }
    memoryManager.updatePosition(buyPosition);
    
    // 更新卖方仓位
    Position sellPosition = memoryManager.getOrCreatePosition(trade.getSellUserId(), trade.getSymbol());
    if (trade.getSellPositionAction().isOpen()) {
        sellPosition.openPosition(trade.getQuantity(), trade.getPrice());
    } else {
        sellPosition.closePosition(trade.getQuantity(), trade.getPrice());
    }
    memoryManager.updatePosition(sellPosition);
}
```

## 响应信息扩展

### 1. MatchResponse 仓位变化信息

```java
@Data
public static class PositionChange {
    // ... 基本信息
    
    /**
     * 开平仓动作
     */
    private PositionAction positionAction;
    
    /**
     * 开仓均价
     */
    private BigDecimal openAveragePrice;
    
    /**
     * 平仓均价
     */
    private BigDecimal closeAveragePrice;
    
    /**
     * 已实现盈亏变化
     */
    private BigDecimal realizedPnlChange;
}
```

### 2. 错误处理

```java
// 在submitOrder方法中捕获开平仓错误
try {
    response.setPositionChange(calculatePositionChange(order, trades));
} catch (IllegalArgumentException e) {
    response.setStatus(MatchStatus.REJECTED);
    response.setErrorMessage(e.getMessage());
    response.setRejectInfo(createRejectInfo(
        MatchResponse.RejectInfo.RejectType.INVALID_POSITION_ACTION,
        e.getMessage()
    ));
    return response;
}
```

## 使用示例

### 1. 开仓订单

```java
// 开多仓
Order openLongOrder = new Order();
openLongOrder.setOrderId("ORDER_001");
openLongOrder.setUserId(1001L);
openLongOrder.setSymbol("BTC/USDT");
openLongOrder.setSide(OrderSide.BUY);
openLongOrder.setType(OrderType.LIMIT);
openLongOrder.setPositionAction(PositionAction.OPEN);
openLongOrder.setPrice(new BigDecimal("50000"));
openLongOrder.setQuantity(new BigDecimal("1.0"));

MatchResponse response = matchEngineService.submitOrder(openLongOrder);

if (response.isSuccess()) {
    PositionChange posChange = response.getPositionChange();
    System.out.println("开仓动作: " + posChange.getPositionAction());
    System.out.println("仓位变化: " + posChange.getQuantityChange());
    System.out.println("新仓位数量: " + posChange.getNewQuantity());
}
```

### 2. 平仓订单

```java
// 平多仓
Order closeLongOrder = new Order();
closeLongOrder.setOrderId("ORDER_002");
closeLongOrder.setUserId(1001L);
closeLongOrder.setSymbol("BTC/USDT");
closeLongOrder.setSide(OrderSide.SELL);
closeLongOrder.setType(OrderType.LIMIT);
closeLongOrder.setPositionAction(PositionAction.CLOSE);
closeLongOrder.setPrice(new BigDecimal("51000"));
closeLongOrder.setQuantity(new BigDecimal("0.5"));

MatchResponse response = matchEngineService.submitOrder(closeLongOrder);

if (response.isSuccess()) {
    PositionChange posChange = response.getPositionChange();
    System.out.println("平仓动作: " + posChange.getPositionAction());
    System.out.println("仓位变化: " + posChange.getQuantityChange()); // 负数
    System.out.println("已实现盈亏: " + posChange.getRealizedPnlChange());
}
```

### 3. 自动判断开平仓

```java
// 根据当前仓位自动判断开平仓动作
Position currentPosition = memoryManager.getPosition(userId, symbol);
PositionAction action = PositionAction.determineAction(orderSide, currentPosition.getSide());

Order order = new Order();
order.setPositionAction(action);
// ... 其他订单设置
```

## 业务场景

### 1. 开仓场景

- **开多仓**：买单 + OPEN → 增加多仓
- **开空仓**：卖单 + OPEN → 增加空仓

### 2. 平仓场景

- **平多仓**：卖单 + CLOSE → 减少多仓
- **平空仓**：买单 + CLOSE → 减少空仓

### 3. 错误场景

- **买单平仓但无空仓**：返回错误
- **卖单平仓但无多仓**：返回错误

## 设计优势

### 1. 单一职责原则

- `PositionAction` 只负责开平仓动作
- `OrderSide` 只负责买卖方向
- 通过组合计算最终结果

### 2. 清晰的逻辑

```java
// 开仓逻辑
if (orderSide == BUY && positionAction == OPEN) {
    // 买单开仓 = 开多仓
    positionChange = quantity;
}

// 平仓逻辑
if (orderSide == SELL && positionAction == CLOSE) {
    // 卖单平仓 = 平多仓
    if (currentPosition == LONG) {
        positionChange = -quantity;
    } else {
        throw new IllegalArgumentException("卖单平仓但当前无多仓");
    }
}
```

### 3. 易于扩展

- 可以轻松添加新的开平仓动作
- 可以轻松添加新的验证规则
- 可以轻松添加新的错误类型

### 4. 错误处理

```java
// 统一的错误处理
if (!result.isValid()) {
    throw new IllegalArgumentException(result.getReason());
}
```

## 风险控制

### 1. 平仓验证

```java
// 验证平仓订单
if (order.getPositionAction() == CLOSE) {
    Position position = memoryManager.getPosition(order.getUserId(), order.getSymbol());
    if (position == null || position.getQuantity().compareTo(order.getQuantity()) < 0) {
        response.setStatus(MatchStatus.REJECTED);
        response.setRejectInfo(createRejectInfo(
            RejectType.INSUFFICIENT_POSITION,
            "仓位不足，无法平仓"
        ));
        return response;
    }
}
```

### 2. 开平仓一致性

```java
// 验证开平仓动作与订单方向的一致性
PositionAction.PositionChangeResult result = PositionAction.calculatePositionChange(
    order.getSide(), 
    order.getPositionAction(), 
    currentPositionSide,
    order.getQuantity()
);

if (!result.isValid()) {
    response.setStatus(MatchStatus.REJECTED);
    response.setRejectInfo(createRejectInfo(
        RejectType.INVALID_POSITION_ACTION,
        result.getReason()
    ));
    return response;
}
```

## 监控和日志

### 1. 开平仓日志

```java
log.info("开平仓成交: tradeId={}, buyAction={}, sellAction={}, quantity={}, price={}", 
        trade.getTradeId(), trade.getBuyPositionAction(), trade.getSellPositionAction(),
        trade.getQuantity(), trade.getPrice());
```

### 2. 错误日志

```java
if (response.isRejected()) {
    log.warn("订单被拒绝: orderId={}, reason={}", 
            response.getOrderId(), response.getErrorMessage());
}
```

## 总结

新的开平仓设计架构提供了：

1. **清晰的职责分离**：开平仓动作、买卖方向、仓位方向各司其职
2. **组合计算逻辑**：通过组合计算最终的仓位变化
3. **统一的错误处理**：统一的验证和错误处理机制
4. **易于扩展**：可以轻松添加新的功能和验证规则
5. **完整的业务支持**：支持所有期货交易的开平仓场景

这套机制为期货交易提供了完整、清晰、可扩展的开平仓支持。 