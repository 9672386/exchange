# 完整响应信息实现

## 概述

撮合引擎现在返回完整的响应信息，包含成交信息、撤单信息、仓位信息和拒绝信息，为用户提供详细的操作反馈。

## 响应信息模型

### 1. MatchResponse 主响应模型

```java
@Data
public class MatchResponse {
    // 基本信息
    private String orderId;           // 订单ID
    private Long userId;              // 用户ID
    private String symbol;            // 交易对
    private OrderSide side;           // 订单方向
    private OrderType orderType;      // 订单类型
    private MatchStatus status;       // 撮合状态
    
    // 价格和数量信息
    private BigDecimal orderPrice;    // 订单价格
    private BigDecimal orderQuantity; // 订单数量
    private BigDecimal matchPrice;    // 成交价格
    private BigDecimal matchQuantity; // 成交数量
    private BigDecimal remainingQuantity; // 剩余数量
    private BigDecimal matchAmount;   // 成交金额
    private BigDecimal fee;           // 手续费
    
    // 详细信息
    private List<Trade> trades;      // 成交记录列表
    private PositionChange positionChange; // 仓位变化信息
    private CancelInfo cancelInfo;    // 撤单信息
    private RejectInfo rejectInfo;    // 拒绝信息
    
    // 时间信息
    private LocalDateTime createTime; // 创建时间
    private LocalDateTime processTime; // 处理时间
}
```

### 2. 仓位变化信息 (PositionChange)

```java
@Data
public static class PositionChange {
    private Long userId;              // 用户ID
    private String symbol;            // 交易对
    private PositionSide side;        // 仓位方向
    private BigDecimal quantityChange; // 仓位数量变化
    private BigDecimal valueChange;   // 仓位价值变化
    private BigDecimal newQuantity;   // 更新后仓位数量
    private BigDecimal newValue;      // 更新后仓位价值
    private BigDecimal newUnrealizedPnl; // 更新后未实现盈亏
    private BigDecimal newRealizedPnl;   // 更新后已实现盈亏
    private BigDecimal averageCost;   // 平均成本
    private LocalDateTime updateTime; // 更新时间
}
```

### 3. 撤单信息 (CancelInfo)

```java
@Data
public static class CancelInfo {
    private Long cancelUserId;        // 撤单用户ID
    private String cancelReason;      // 撤单原因
    private BigDecimal cancelQuantity; // 撤单数量
    private LocalDateTime cancelTime; // 撤单时间
    private MatchStatus previousStatus; // 撤单前状态
}
```

### 4. 拒绝信息 (RejectInfo)

```java
@Data
public static class RejectInfo {
    private String rejectCode;        // 拒绝原因代码
    private String rejectReason;      // 拒绝原因描述
    private LocalDateTime rejectTime; // 拒绝时间
    private RejectType rejectType;    // 拒绝类型
    
    public enum RejectType {
        INVALID_PRICE("价格无效"),
        INVALID_QUANTITY("数量无效"),
        INSUFFICIENT_BALANCE("余额不足"),
        INSUFFICIENT_POSITION("仓位不足"),
        PRICE_OUT_OF_RANGE("价格超出范围"),
        QUANTITY_OUT_OF_RANGE("数量超出范围"),
        MARKET_CLOSED("市场关闭"),
        SYMBOL_NOT_TRADABLE("标的不可交易"),
        POST_ONLY_REJECTED("POST_ONLY订单会立即成交"),
        FOK_REJECTED("FOK订单无法全部成交"),
        SLIPPAGE_TOO_HIGH("滑点过大"),
        DEPTH_INSUFFICIENT("深度不足"),
        SYSTEM_ERROR("系统错误");
    }
}
```

## 使用示例

### 1. 提交订单响应

```java
// 提交限价买单
Order order = new Order();
order.setOrderId("ORDER_001");
order.setUserId(1001L);
order.setSymbol("BTC/USDT");
order.setSide(OrderSide.BUY);
order.setType(OrderType.LIMIT);
order.setPrice(new BigDecimal("50000"));
order.setQuantity(new BigDecimal("1.0"));

MatchResponse response = matchEngineService.submitOrder(order);

// 检查响应
if (response.isSuccess()) {
    System.out.println("订单提交成功");
    System.out.println("成交数量: " + response.getMatchQuantity());
    System.out.println("剩余数量: " + response.getRemainingQuantity());
    System.out.println("成交价格: " + response.getMatchPrice());
    System.out.println("手续费: " + response.getFee());
    
    // 查看仓位变化
    if (response.getPositionChange() != null) {
        PositionChange posChange = response.getPositionChange();
        System.out.println("仓位变化: " + posChange.getQuantityChange());
        System.out.println("新仓位数量: " + posChange.getNewQuantity());
    }
    
    // 查看成交记录
    if (response.getTrades() != null) {
        for (Trade trade : response.getTrades()) {
            System.out.println("成交记录: " + trade.getTradeId() + 
                             ", 价格: " + trade.getPrice() + 
                             ", 数量: " + trade.getQuantity());
        }
    }
} else if (response.isRejected()) {
    System.out.println("订单被拒绝: " + response.getErrorMessage());
    if (response.getRejectInfo() != null) {
        System.out.println("拒绝类型: " + response.getRejectInfo().getRejectType());
        System.out.println("拒绝原因: " + response.getRejectInfo().getRejectReason());
    }
}
```

### 2. 撤单响应

```java
// 撤销订单
MatchResponse cancelResponse = matchEngineService.cancelOrder("ORDER_001", 1001L);

if (cancelResponse.isCancelled()) {
    System.out.println("撤单成功");
    System.out.println("撤单数量: " + cancelResponse.getCancelInfo().getCancelQuantity());
    System.out.println("撤单原因: " + cancelResponse.getCancelInfo().getCancelReason());
    System.out.println("撤单前状态: " + cancelResponse.getCancelInfo().getPreviousStatus());
} else {
    System.out.println("撤单失败: " + cancelResponse.getErrorMessage());
}
```

## 响应状态说明

### 1. 成功状态

- **SUCCESS**: 订单完全成交
- **PARTIALLY_FILLED**: 订单部分成交，剩余部分继续等待

### 2. 失败状态

- **REJECTED**: 订单被拒绝
- **CANCELLED**: 订单被取消

### 3. 等待状态

- **PENDING**: 订单待处理（未成交）

## 错误处理

### 1. 订单验证错误

```java
// 价格无效
if (!symbol.isValidPrice(order.getPrice())) {
    response.setStatus(MatchStatus.REJECTED);
    response.setRejectInfo(createRejectInfo(
        RejectType.INVALID_PRICE,
        "价格无效: " + order.getPrice()
    ));
}

// 数量无效
if (!symbol.isValidQuantity(order.getQuantity())) {
    response.setStatus(MatchStatus.REJECTED);
    response.setRejectInfo(createRejectInfo(
        RejectType.INVALID_QUANTITY,
        "数量无效: " + order.getQuantity()
    ));
}
```

### 2. 业务逻辑错误

```java
// POST_ONLY订单会立即成交
if (order.getType() == OrderType.POST_ONLY && willImmediatelyFill(order)) {
    response.setStatus(MatchStatus.REJECTED);
    response.setRejectInfo(createRejectInfo(
        RejectType.POST_ONLY_REJECTED,
        "POST_ONLY订单会立即成交"
    ));
}

// FOK订单无法全部成交
if (order.getType() == OrderType.FOK && !canFullyFill(order)) {
    response.setStatus(MatchStatus.REJECTED);
    response.setRejectInfo(createRejectInfo(
        RejectType.FOK_REJECTED,
        "FOK订单无法全部成交"
    ));
}
```

### 3. 系统错误

```java
try {
    // 撮合逻辑
} catch (Exception e) {
    response.setStatus(MatchStatus.REJECTED);
    response.setRejectInfo(createRejectInfo(
        RejectType.SYSTEM_ERROR,
        "系统错误: " + e.getMessage()
    ));
}
```

## 监控和日志

### 1. 成功操作日志

```java
log.info("订单提交成功: orderId={}, symbol={}, status={}, filled={}, remaining={}", 
        order.getOrderId(), order.getSymbol(), response.getStatus(), 
        response.getMatchQuantity(), response.getRemainingQuantity());
```

### 2. 错误操作日志

```java
log.warn("订单被拒绝: orderId={}, reason={}, type={}", 
        order.getOrderId(), response.getErrorMessage(), 
        response.getRejectInfo().getRejectType());
```

### 3. 撤单操作日志

```java
log.info("订单取消成功: orderId={}, userId={}, cancelQuantity={}", 
        orderId, userId, cancelInfo.getCancelQuantity());
```

## 最佳实践

### 1. 响应处理

```java
public void handleOrderResponse(MatchResponse response) {
    switch (response.getStatus()) {
        case SUCCESS:
            handleFullyFilledOrder(response);
            break;
        case PARTIALLY_FILLED:
            handlePartiallyFilledOrder(response);
            break;
        case PENDING:
            handlePendingOrder(response);
            break;
        case REJECTED:
            handleRejectedOrder(response);
            break;
        case CANCELLED:
            handleCancelledOrder(response);
            break;
    }
}
```

### 2. 错误处理

```java
public void handleRejectedOrder(MatchResponse response) {
    RejectInfo rejectInfo = response.getRejectInfo();
    switch (rejectInfo.getRejectType()) {
        case INVALID_PRICE:
            // 提示用户调整价格
            break;
        case INSUFFICIENT_BALANCE:
            // 提示用户充值
            break;
        case POST_ONLY_REJECTED:
            // 提示用户调整价格或使用其他订单类型
            break;
        default:
            // 通用错误处理
            break;
    }
}
```

### 3. 仓位更新

```java
public void updatePositionFromResponse(MatchResponse response) {
    if (response.getPositionChange() != null) {
        PositionChange posChange = response.getPositionChange();
        // 更新用户仓位
        updateUserPosition(posChange);
        // 发送仓位变化通知
        notifyPositionChange(posChange);
    }
}
```

## 总结

完整的响应信息实现提供了：

1. **详细的状态信息**：清楚显示订单的处理结果
2. **完整的成交信息**：包含价格、数量、金额、手续费等
3. **仓位变化追踪**：实时更新用户仓位信息
4. **撤单信息记录**：记录撤单原因和数量
5. **错误信息分类**：详细的错误类型和原因
6. **时间戳记录**：便于追踪和审计

这套响应机制为用户提供了完整的操作反馈，便于前端展示和后续处理。 