# 分档平仓使用示例

## 场景描述

假设用户张三在BTCUSDT上持有1 BTC的多仓，开仓价格为58000 USDT，当前市场价格为50000 USDT，用户账户余额为10000 USDT。

由于市场下跌，用户的风险率达到了99%，触发了第五档（LIQUIDATION）风险等级。

## 分档平仓执行流程

### 1. 风控服务检测到风险

```java
// 风控服务计算用户风险率
BigDecimal riskRatio = calculateRiskRatio(userId, symbol);
// 结果：99% (0.99)

RiskLevel riskLevel = RiskLevel.getRiskLevel(riskRatio);
// 结果：LIQUIDATION (第五档)

// 触发强平指令
if (riskLevel == RiskLevel.LIQUIDATION) {
    EventLiquidationReq liquidationReq = new EventLiquidationReq();
    liquidationReq.setLiquidationId("LIQ_" + System.currentTimeMillis());
    liquidationReq.setUserId(1001L);
    liquidationReq.setSymbol("BTCUSDT");
    liquidationReq.setLiquidationType("TIERED_LIQUIDATION");
    liquidationReq.setRiskLevel("LIQUIDATION");
    
    // 设置风控计算的上下文信息
    liquidationReq.setIndexPrice(BigDecimal.valueOf(50000));        // 当前价格
    liquidationReq.setBalance(BigDecimal.valueOf(10000));           // 用户余额
    liquidationReq.setMargin(BigDecimal.valueOf(10000));            // 保证金
    liquidationReq.setRiskRatio(BigDecimal.valueOf(0.99));         // 风险率99%
    liquidationReq.setUnrealizedPnl(BigDecimal.valueOf(-8000));    // 未实现亏损8000
    liquidationReq.setRealizedPnl(BigDecimal.valueOf(1000));       // 已实现盈利1000
    
    // 发送到撮合引擎
    sendToMatchEngine(liquidationReq);
}
```

### 2. 撮合引擎接收强平指令

```java
// 撮合引擎接收到强平指令
public void handleLiquidation(EventLiquidationReq liquidationReq) {
    // 重新计算风险率
    RiskRecalculationService.RiskRecalculationResult riskResult = 
        riskRecalculationService.recalculateRisk(liquidationRequest);
    
    // 检查是否需要执行分档平仓
    if (shouldExecuteTieredLiquidation(riskResult.getRiskLevel())) {
        log.info("执行分档平仓，用户: {}, 交易对: {}, 风险等级: {}", 
                liquidationRequest.getUserId(), liquidationRequest.getSymbol(), 
                riskResult.getRiskLevel().getName());
        
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

### 3. 分档平仓执行过程

#### 第一步：第五档 → 第四档

```java
// 当前持仓：1 BTC
// 第五档平仓比例：50%
// 平仓数量：1 BTC × 50% = 0.5 BTC

// 执行平仓
Order liquidationOrder = createTieredLiquidationOrder(
    userId, symbol, BigDecimal.valueOf(0.5), OrderSide.SELL, RiskLevel.LIQUIDATION
);

// 市价平仓，假设成交价格为50000 USDT
// 成交金额：0.5 BTC × 50000 USDT = 25000 USDT

// 更新持仓
position.setQuantity(position.getQuantity().subtract(BigDecimal.valueOf(0.5)));
// 剩余持仓：0.5 BTC

// 重新计算风险率
RiskRecalculationService.RiskRecalculationResult riskResult = 
    riskRecalculationService.recalculateRisk(liquidationRequest);
// 新的风险率：95% (EMERGENCY档)
```

#### 第二步：第四档 → 第三档

```java
// 当前持仓：0.5 BTC
// 第四档平仓比例：40%
// 平仓数量：0.5 BTC × 40% = 0.2 BTC

// 执行平仓
Order liquidationOrder = createTieredLiquidationOrder(
    userId, symbol, BigDecimal.valueOf(0.2), OrderSide.SELL, RiskLevel.EMERGENCY
);

// 市价平仓，假设成交价格为50000 USDT
// 成交金额：0.2 BTC × 50000 USDT = 10000 USDT

// 更新持仓
position.setQuantity(position.getQuantity().subtract(BigDecimal.valueOf(0.2)));
// 剩余持仓：0.3 BTC

// 重新计算风险率
RiskRecalculationService.RiskRecalculationResult riskResult = 
    riskRecalculationService.recalculateRisk(liquidationRequest);
// 新的风险率：92% (DANGER档)
```

#### 第三步：第三档 → 第二档

```java
// 当前持仓：0.3 BTC
// 第三档平仓比例：30%
// 平仓数量：0.3 BTC × 30% = 0.09 BTC

// 执行平仓
Order liquidationOrder = createTieredLiquidationOrder(
    userId, symbol, BigDecimal.valueOf(0.09), OrderSide.SELL, RiskLevel.DANGER
);

// 市价平仓，假设成交价格为50000 USDT
// 成交金额：0.09 BTC × 50000 USDT = 4500 USDT

// 更新持仓
position.setQuantity(position.getQuantity().subtract(BigDecimal.valueOf(0.09)));
// 剩余持仓：0.21 BTC

// 重新计算风险率
RiskRecalculationService.RiskRecalculationResult riskResult = 
    riskRecalculationService.recalculateRisk(liquidationRequest);
// 新的风险率：85% (WARNING档，安全水平)
```

### 4. 分档平仓结果

```java
// 分档平仓完成后的结果
TieredLiquidationResult result = {
    userId: 1001L,
    symbol: "BTCUSDT",
    startTime: 1640995200000,
    endTime: 1640995205000,
    finalRiskLevel: WARNING,
    steps: [
        {
            riskLevel: LIQUIDATION,
            successQuantity: 0.5,
            totalAmount: 25000,
            averagePrice: 50000,
            tradeCount: 1,
            duration: 1000
        },
        {
            riskLevel: EMERGENCY,
            successQuantity: 0.2,
            totalAmount: 10000,
            averagePrice: 50000,
            tradeCount: 1,
            duration: 800
        },
        {
            riskLevel: DANGER,
            successQuantity: 0.09,
            totalAmount: 4500,
            averagePrice: 50000,
            tradeCount: 1,
            duration: 600
        }
    ]
};

// 统计信息
BigDecimal totalLiquidationQuantity = result.getTotalLiquidationQuantity(); // 0.79 BTC
BigDecimal totalLiquidationAmount = result.getTotalLiquidationAmount();     // 39500 USDT
long duration = result.getDuration();                                       // 5000ms
```

### 5. 最终状态

- **原始持仓**：1 BTC
- **分档平仓后持仓**：0.21 BTC
- **总平仓数量**：0.79 BTC
- **总平仓金额**：39,500 USDT
- **最终风险等级**：WARNING (第二档，安全水平)
- **执行时间**：5秒

## 优势分析

### 1. 风险控制更精细

通过分档平仓，避免了一次性全部强平对市场造成的冲击，同时给用户更多机会来管理风险。

### 2. 用户损失更小

在这个例子中，用户保留了0.21 BTC的持仓，而不是全部被强平。如果市场后续反弹，用户可能有机会减少损失。

### 3. 市场稳定性更好

分档平仓减少了单次大量抛售对市场价格的冲击，有助于维护市场稳定。

### 4. 风险计算更准确

每次平仓后都重新计算风险率，确保风险控制的有效性和准确性。

## 注意事项

1. **执行时间**：分档平仓需要多次执行，总时间可能较长（本例中为5秒）
2. **市场风险**：在极端市场条件下，分档平仓可能无法完全避免损失
3. **系统负载**：分档平仓会增加系统负载，需要合理控制执行频率
4. **监控告警**：需要监控分档平仓的执行情况，及时发现问题

## 配置参数

可以根据实际需求调整以下参数：

```java
// 档位平仓比例配置
private BigDecimal getLiquidationRatioForTier(RiskLevel riskLevel) {
    switch (riskLevel) {
        case LIQUIDATION: return BigDecimal.valueOf(0.5);  // 可调整
        case EMERGENCY:   return BigDecimal.valueOf(0.4);  // 可调整
        case DANGER:      return BigDecimal.valueOf(0.3);  // 可调整
        case WARNING:     return BigDecimal.valueOf(0.2);  // 可调整
        default:          return BigDecimal.valueOf(0.1);  // 可调整
    }
}
```

这些比例可以根据市场条件、用户类型、标的特性等因素进行调整。 