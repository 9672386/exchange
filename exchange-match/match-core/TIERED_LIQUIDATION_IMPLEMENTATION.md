# 分档平仓功能实现

## 概述

分档平仓是一种风险控制机制，当用户风险限额处于第五档（爆仓等级）时，系统不会立即全部强平，而是采用分档逐步平仓的方式，先平掉一部分仓位降到第四档，然后重新计算风险率，如果还需要强平则继续降到第三档，以此类推。

## 风险等级定义

### 风险等级档位

| 档位 | 风险等级 | 风险率阈值 | 描述 | 平仓比例 |
|------|----------|------------|------|----------|
| 第一档 | NORMAL | < 80% | 正常交易，无限制 | - |
| 第二档 | WARNING | 80% - 90% | 限制开仓，允许平仓 | - |
| 第三档 | DANGER | 90% - 95% | 禁止开仓，强制减仓 | 30% |
| 第四档 | EMERGENCY | 95% - 98% | 禁止所有交易，立即强平 | 40% |
| 第五档 | LIQUIDATION | >= 98% | 立即全部强平 | 50% |

### 分档平仓逻辑

当用户风险等级达到第五档（LIQUIDATION）时：

1. **第五档 → 第四档**：平掉50%仓位
2. **第四档 → 第三档**：平掉40%仓位  
3. **第三档 → 第二档**：平掉30%仓位
4. **第二档 → 第一档**：平掉20%仓位

## 核心组件

### 1. 分档平仓服务 (RiskManagementService)

```java
@Service
public class RiskManagementService {
    
    /**
     * 执行分档平仓操作
     * 从当前风险档位逐步降到安全档位
     */
    public TieredLiquidationResult executeTieredLiquidation(Long userId, String symbol, LiquidationRequest liquidationRequest);
}
```

### 2. 分档平仓结果

```java
@Data
public static class TieredLiquidationResult {
    private Long userId;
    private String symbol;
    private long startTime;
    private long endTime;
    private RiskLevel finalRiskLevel;
    private String errorMessage;
    private List<TieredLiquidationStep> steps = new ArrayList<>();
    
    public BigDecimal getTotalLiquidationQuantity();
    public BigDecimal getTotalLiquidationAmount();
    public long getDuration();
}
```

### 3. 分档平仓步骤

```java
@Data
public static class TieredLiquidationStep {
    private RiskLevel riskLevel;
    private BigDecimal successQuantity;
    private BigDecimal totalAmount;
    private BigDecimal averagePrice;
    private int tradeCount;
    private String errorMessage;
    private long startTime;
    private long endTime;
}
```

## 实现流程

### 1. 触发条件判断

```java
private boolean shouldExecuteTieredLiquidation(RiskLevel riskLevel) {
    // 当风险等级为LIQUIDATION（第五档）时，执行分档平仓
    return riskLevel == RiskLevel.LIQUIDATION;
}
```

### 2. 分档平仓执行

```java
public TieredLiquidationResult executeTieredLiquidation(Long userId, String symbol, LiquidationRequest liquidationRequest) {
    // 1. 获取当前风险等级
    RiskLevel currentRiskLevel = liquidationRequest.getRiskLevel();
    
    // 2. 确定目标风险等级（安全档位）
    RiskLevel targetRiskLevel = getTargetRiskLevel(currentRiskLevel);
    
    // 3. 逐步降档平仓
    while (currentLevel.getPriority() > targetRiskLevel.getPriority()) {
        // 执行当前档位的平仓
        TieredLiquidationStep step = executeSingleTierLiquidation(userId, symbol, currentLevel, liquidationRequest);
        
        // 重新计算风险率
        RiskRecalculationService.RiskRecalculationResult riskResult = 
            riskRecalculationService.recalculateRisk(liquidationRequest);
        
        // 如果风险率已经降到安全水平，结束分档平仓
        if (!riskResult.isNeedLiquidation()) {
            break;
        }
        
        // 更新当前风险等级
        currentLevel = riskResult.getRiskLevel();
    }
}
```

### 3. 单档位平仓

```java
private TieredLiquidationStep executeSingleTierLiquidation(Long userId, String symbol, RiskLevel riskLevel, LiquidationRequest liquidationRequest) {
    // 1. 计算当前档位的平仓数量
    BigDecimal liquidationRatio = getLiquidationRatioForTier(riskLevel);
    BigDecimal liquidationQuantity = position.getQuantity().multiply(liquidationRatio);
    
    // 2. 确定平仓方向
    OrderSide liquidationSide = position.getSide() == PositionSide.LONG ? OrderSide.SELL : OrderSide.BUY;
    
    // 3. 创建平仓订单
    Order liquidationOrder = createTieredLiquidationOrder(userId, symbol, liquidationQuantity, liquidationSide, riskLevel);
    
    // 4. 执行平仓撮合
    List<Trade> trades = executeTieredLiquidationMatching(liquidationOrder);
    
    // 5. 更新持仓
    updatePositionAfterTieredLiquidation(position, trades);
}
```

### 4. 档位平仓比例

```java
private BigDecimal getLiquidationRatioForTier(RiskLevel riskLevel) {
    switch (riskLevel) {
        case LIQUIDATION: // 第五档：平掉50%仓位
            return BigDecimal.valueOf(0.5);
        case EMERGENCY: // 第四档：平掉40%仓位
            return BigDecimal.valueOf(0.4);
        case DANGER: // 第三档：平掉30%仓位
            return BigDecimal.valueOf(0.3);
        case WARNING: // 第二档：平掉20%仓位
            return BigDecimal.valueOf(0.2);
        default:
            return BigDecimal.valueOf(0.1); // 默认平掉10%
    }
}
```

## 风控数据传递

### 风控服务传递的数据

风控服务在触发强平时，会传递以下数据给撮合引擎：

```java
// 风控计算时的上下文信息
liquidationRequest.setIndexPrice(indexPrice);           // 触发强平时的指数价格
liquidationRequest.setBalance(balance);                 // 用户资金余额
liquidationRequest.setMargin(margin);                   // 用户保证金
liquidationRequest.setRiskRatio(riskRatio);             // 用户风险率
liquidationRequest.setUnrealizedPnl(unrealizedPnl);    // 未实现盈亏
liquidationRequest.setRealizedPnl(realizedPnl);        // 已实现盈亏
liquidationRequest.setMaintenanceMarginRatio(maintenanceMarginRatio); // 维持保证金率
liquidationRequest.setInitialMarginRatio(initialMarginRatio);         // 初始保证金率
```

### 重要说明

**风控传递的资金是用户当前余额，没有计算未实现盈亏**

这意味着：
1. 风控服务计算风险率时使用的是用户当前余额
2. 撮合引擎重新计算风险率时也使用相同的余额数据
3. 未实现盈亏在风险计算中作为减项，而不是加项

## 使用示例

### 1. 风控服务触发分档平仓

```java
// 风控服务检测到用户风险等级为LIQUIDATION
if (riskLevel == RiskLevel.LIQUIDATION) {
    // 发送强平指令到撮合引擎
    EventLiquidationReq liquidationReq = new EventLiquidationReq();
    liquidationReq.setLiquidationId("LIQ_" + System.currentTimeMillis());
    liquidationReq.setUserId(userId);
    liquidationReq.setSymbol(symbol);
    liquidationReq.setLiquidationType("TIERED_LIQUIDATION");
    liquidationReq.setRiskLevel("LIQUIDATION");
    
    // 设置风控计算的上下文信息
    liquidationReq.setIndexPrice(getCurrentPrice(symbol));
    liquidationReq.setBalance(getUserBalance(userId));
    liquidationReq.setMargin(getUserMargin(userId, symbol));
    liquidationReq.setRiskRatio(calculateRiskRatio(userId, symbol));
    liquidationReq.setUnrealizedPnl(calculateUnrealizedPnl(userId, symbol));
    
    // 发送到撮合引擎
    sendToMatchEngine(liquidationReq);
}
```

### 2. 撮合引擎执行分档平仓

```java
// 撮合引擎接收到强平指令
public void handleLiquidation(EventLiquidationReq liquidationReq) {
    // 重新计算风险率
    RiskRecalculationService.RiskRecalculationResult riskResult = 
        riskRecalculationService.recalculateRisk(liquidationRequest);
    
    // 检查是否需要执行分档平仓
    if (shouldExecuteTieredLiquidation(riskResult.getRiskLevel())) {
        // 执行分档平仓
        TieredLiquidationResult tieredResult = 
            riskManagementService.executeTieredLiquidation(
                liquidationRequest.getUserId(), 
                liquidationRequest.getSymbol(), 
                liquidationRequest
            );
        
        log.info("分档平仓完成，总平仓数量: {}, 最终风险等级: {}", 
                tieredResult.getTotalLiquidationQuantity(), 
                tieredResult.getFinalRiskLevel().getName());
    }
}
```

## 优势

1. **风险控制更精细**：避免一次性全部强平造成的市场冲击
2. **用户损失更小**：通过分档平仓，用户可能避免全部损失
3. **市场稳定性更好**：分档平仓减少了对市场的冲击
4. **风险计算更准确**：每次平仓后重新计算风险率，确保风险控制的有效性

## 注意事项

1. **执行时间**：分档平仓需要多次执行，总时间可能较长
2. **市场风险**：在极端市场条件下，分档平仓可能无法完全避免损失
3. **系统负载**：分档平仓会增加系统负载，需要合理控制执行频率
4. **监控告警**：需要监控分档平仓的执行情况，及时发现问题 