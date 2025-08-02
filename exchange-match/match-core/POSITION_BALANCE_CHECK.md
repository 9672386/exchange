# 持仓平衡检查机制

## 概述

在合约交易中，持仓平衡检查是确保系统稳定性的重要机制。本系统实现了两个层面的检查：

1. **开平仓平衡检查**：确保每笔开仓都有对应的平仓，防止出现净持仓
2. **意愿查询接口**：提供持仓意愿和订单深度的查询功能，不参与每笔委托的计算

## 核心概念

### 1. 开平仓平衡检查

**目标**：确保多空持仓数量总是平衡的
- 开仓：增加对应方向的持仓
- 平仓：减少对应方向的持仓
- 净持仓应该始终为零（允许小的误差）

### 2. 意愿查询接口

**目标**：提供市场情绪和深度的查询功能
- 持仓意愿：反映市场对多空方向的偏好
- 订单深度：反映当前价格水平的买卖压力
- 仅用于查询，不参与委托处理

## 核心组件

### 1. PositionBalance 模型

```java
public class PositionBalance {
    private String symbol;           // 交易对
    private BigDecimal longTotal;    // 多仓总量
    private BigDecimal shortTotal;   // 空仓总量
    private BigDecimal netPosition;  // 净持仓
    private BigDecimal longShortRatio; // 多空比例
    private BalanceStatus status;    // 平衡状态
    private LocalDateTime checkTime; // 检查时间
    private Map<Long, Position> userPositions; // 用户持仓映射
}
```

### 2. 平衡状态枚举

```java
public enum BalanceStatus {
    BALANCED("平衡"),           // 净持仓接近零
    UNBALANCED("不平衡"),       // 存在净持仓
    EXTREME_UNBALANCED("极端不平衡"); // 净持仓过大
}
```

### 3. 意愿查询服务

```java
@Service
public class PositionIntentionService {
    // 获取持仓意愿信息
    public PositionIntentionInfo getPositionIntention(String symbol)
    
    // 获取订单深度信息
    public OrderDepthInfo getOrderDepth(String symbol)
    
    // 获取综合市场信息
    public MarketInfo getMarketInfo(String symbol)
}
```

## 开平仓平衡检查逻辑

### 1. 平衡检查方法

```java
public boolean checkOpenCloseBalance(PositionAction action, OrderSide side, BigDecimal quantity) {
    if (action == null) {
        return true; // 现货交易不需要检查
    }
    
    BigDecimal newLongTotal = this.longTotal;
    BigDecimal newShortTotal = this.shortTotal;
    
    if (action.isOpen()) {
        // 开仓：增加对应方向的持仓
        if (side == OrderSide.BUY) {
            newLongTotal = newLongTotal.add(quantity);
        } else {
            newShortTotal = newShortTotal.add(quantity);
        }
    } else if (action.isClose()) {
        // 平仓：减少对应方向的持仓
        if (side == OrderSide.BUY) {
            // 买入平空仓
            if (newShortTotal.compareTo(quantity) < 0) {
                return false; // 空仓不足
            }
            newShortTotal = newShortTotal.subtract(quantity);
        } else {
            // 卖出平多仓
            if (newLongTotal.compareTo(quantity) < 0) {
                return false; // 多仓不足
            }
            newLongTotal = newLongTotal.subtract(quantity);
        }
    }
    
    // 检查新的净持仓是否平衡
    BigDecimal newNetPosition = newLongTotal.subtract(newShortTotal);
    BigDecimal tolerance = BigDecimal.valueOf(0.0001);
    
    return newNetPosition.abs().compareTo(tolerance) <= 0;
}
```

### 2. 平衡状态判断

```java
private BalanceStatus determineBalanceStatus() {
    // 检查净持仓是否为零（允许小的误差）
    BigDecimal tolerance = BigDecimal.valueOf(0.0001);
    if (this.netPosition.abs().compareTo(tolerance) <= 0) {
        return BalanceStatus.BALANCED;
    }
    
    // 计算不平衡比例
    BigDecimal totalPosition = this.longTotal.add(this.shortTotal);
    if (totalPosition.compareTo(BigDecimal.ZERO) == 0) {
        return BalanceStatus.BALANCED; // 无持仓
    }
    
    BigDecimal imbalanceRatio = this.netPosition.abs()
            .divide(totalPosition, 4, BigDecimal.ROUND_HALF_UP);
    
    if (imbalanceRatio.compareTo(BigDecimal.valueOf(0.1)) <= 0) {
        return BalanceStatus.BALANCED; // 不平衡比例 <= 10%
    } else if (imbalanceRatio.compareTo(BigDecimal.valueOf(0.3)) <= 0) {
        return BalanceStatus.UNBALANCED; // 10% < 不平衡比例 <= 30%
    } else {
        return BalanceStatus.EXTREME_UNBALANCED; // 不平衡比例 > 30%
    }
}
```

## 集成到撮合引擎

### 1. 在 NewOrderEventHandler 中集成

```java
// 开平仓订单需要检查持仓平衡
if (order.getPositionAction() != null) {
    PositionBalance balance = getPositionBalance(order.getSymbol());
    if (balance != null) {
        // 检查开平仓是否会导致持仓不平衡
        if (!balance.checkOpenCloseBalance(order.getPositionAction(), order.getSide(), order.getQuantity())) {
            response.setStatus(MatchStatus.REJECTED);
            response.setRejectInfo(createRejectInfo(
                MatchResponse.RejectInfo.RejectType.POSITION_IMBALANCE,
                "开平仓操作会导致持仓不平衡。当前状态: " + balance.getStatus().getDescription()
            ));
            return response;
        }
    }
}
```

### 2. 获取平衡信息的方法

```java
private PositionBalance getPositionBalance(String symbol) {
    // 获取所有持仓
    Map<Long, Position> allPositions = memoryManager.getAllPositions(symbol);
    if (allPositions == null || allPositions.isEmpty()) {
        return null;
    }
    
    PositionBalance balance = new PositionBalance(symbol);
    balance.updatePositions(allPositions);
    return balance;
}
```

## 意愿查询接口

### 1. 持仓意愿信息

```java
public static class PositionIntentionInfo {
    private String symbol;
    private BigDecimal longIntention;    // 多仓意愿
    private BigDecimal shortIntention;   // 空仓意愿
    private BigDecimal intentionRatio;   // 意愿比例
    private String intentionStatus;      // 意愿状态
}
```

### 2. 订单深度信息

```java
public static class OrderDepthInfo {
    private String symbol;
    private BigDecimal buyDepth;     // 买单深度
    private BigDecimal sellDepth;    // 卖单深度
    private BigDecimal depthRatio;   // 深度比例
    private String depthStatus;      // 深度状态
}
```

### 3. 综合市场信息

```java
public static class MarketInfo {
    private String symbol;
    private PositionIntentionInfo intentionInfo;  // 意愿信息
    private OrderDepthInfo depthInfo;            // 深度信息
    private String marketStatus;                 // 市场状态
    private String marketAdvice;                 // 市场建议
}
```

## 使用示例

### 1. 开平仓平衡检查

```java
// 检查开平仓是否平衡
PositionBalance balance = new PositionBalance("BTC-USDT");
balance.updatePositions(allPositions);

boolean isBalanced = balance.checkOpenCloseBalance(
    PositionAction.OPEN, 
    OrderSide.BUY, 
    BigDecimal.valueOf(1.0)
);

if (!isBalanced) {
    // 拒绝订单
}
```

### 2. 意愿查询

```java
@Autowired
private PositionIntentionService intentionService;

// 获取持仓意愿信息
PositionIntentionInfo intentionInfo = intentionService.getPositionIntention("BTC-USDT");
System.out.println("多仓意愿: " + intentionInfo.getLongIntention());
System.out.println("空仓意愿: " + intentionInfo.getShortIntention());
System.out.println("意愿状态: " + intentionInfo.getIntentionStatus());

// 获取订单深度信息
OrderDepthInfo depthInfo = intentionService.getOrderDepth("BTC-USDT");
System.out.println("买单深度: " + depthInfo.getBuyDepth());
System.out.println("卖单深度: " + depthInfo.getSellDepth());
System.out.println("深度状态: " + depthInfo.getDepthStatus());

// 获取综合市场信息
MarketInfo marketInfo = intentionService.getMarketInfo("BTC-USDT");
System.out.println("市场状态: " + marketInfo.getMarketStatus());
System.out.println("市场建议: " + marketInfo.getMarketAdvice());
```

### 3. 获取平衡信息

```java
System.out.println("平衡状态: " + balance.getStatus().getDescription());
System.out.println("风险等级: " + balance.getRiskLevel().getDescription());
System.out.println("平衡建议: " + balance.getBalanceAdvice());
System.out.println("不平衡详情: " + balance.getImbalanceDetails());
System.out.println("持仓统计: " + balance.getPositionStats());
```

## 配置参数

### 1. 平衡检查阈值

- **平衡容差**: 0.0001 (净持仓小于此值视为平衡)
- **不平衡阈值**: 10% (不平衡比例 <= 10% 视为平衡)
- **极端不平衡阈值**: 30% (不平衡比例 > 30% 视为极端不平衡)

### 2. 意愿查询阈值

- **意愿平衡阈值**: 0.8-1.2 (意愿比例在此范围内视为平衡)
- **深度平衡阈值**: 0.8-1.2 (深度比例在此范围内视为平衡)

## 优势

1. **精确的平衡检查**: 确保每笔开仓都有对应的平仓，防止净持仓
2. **高性能**: 意愿查询不参与委托处理，避免性能影响
3. **实时监控**: 提供实时的持仓平衡状态和意愿信息
4. **灵活查询**: 支持多种维度的市场信息查询
5. **风险控制**: 通过平衡检查防止系统性风险

## 注意事项

1. **性能考虑**: 平衡检查只在开平仓时进行，不影响普通委托性能
2. **数据一致性**: 确保持仓数据的实时性和准确性
3. **用户体验**: 提供清晰的拒绝原因和平衡建议
4. **监控告警**: 对不平衡状态进行实时监控和告警
5. **容差设置**: 根据实际情况调整平衡容差 