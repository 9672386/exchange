# 合约强平功能实现

## 架构设计

### 职责分工

#### 风控服务职责
- **风险率计算**：实时计算用户风险率
- **强平触发判断**：根据风险率判断是否需要强平
- **发送强平指令**：向撮合引擎发送强平指令，包含完整的上下文信息
- **风险监控**：持续监控用户风险状态

#### 撮合引擎职责
- **接收强平指令**：接收来自风控服务的强平指令
- **重新计算风险率**：根据风控服务传递的数据和标的风险限额重新计算
- **执行风险降档**：根据重新计算的结果执行降档操作
- **执行ADL策略**：根据重新计算的结果执行ADL策略
- **执行最终强平**：执行最终的强平操作

### 数据流向

```
风控服务 → 计算风险率 → 发送完整上下文 → 撮合引擎 → 重新计算风险率 → 判断后续操作
```

## 核心组件

### 1. 风控服务接口 (RiskControlService)

```java
public interface RiskControlService {
    
    /**
     * 计算用户风险率
     */
    BigDecimal calculateRiskRatio(Long userId, String symbol);
    
    /**
     * 获取用户当前风险等级
     */
    RiskLevel getCurrentRiskLevel(Long userId, String symbol);
    
    /**
     * 触发强平（包含完整上下文信息）
     */
    void triggerLiquidation(Long userId, String symbol, String liquidationType, String reason);
}
```

### 2. 风险重新计算服务 (RiskRecalculationService)

```java
@Service
public class RiskRecalculationService {
    
    /**
     * 重新计算用户风险率
     * 根据风控服务传递的数据和标的风险限额重新计算
     */
    public RiskRecalculationResult recalculateRisk(LiquidationRequest liquidationRequest);
}
```

### 3. 撮合引擎风险管理服务 (RiskManagementService)

```java
@Service
public class RiskManagementService {
    
    /**
     * 执行风险降档操作
     */
    public void executeRiskReduction(Long userId, String symbol, RiskLevel riskLevel);
    
    /**
     * 执行ADL策略
     */
    public void executeADLStrategy(String symbol, ADLStrategy strategy, BigDecimal targetQuantity);
}
```

## 强平流程

### 1. 风控服务监控和计算

```java
// 风控服务持续监控用户风险
public void monitorUserRisk(Long userId, String symbol) {
    // 1. 计算用户风险率
    BigDecimal riskRatio = calculateRiskRatio(userId, symbol);
    
    // 2. 获取风险等级
    RiskLevel riskLevel = RiskLevel.getRiskLevel(riskRatio);
    
    // 3. 检查是否需要强平
    if (needLiquidation(userId, symbol)) {
        // 发送强平指令到撮合引擎，包含完整上下文信息
        triggerLiquidationWithContext(userId, symbol, "MARGIN_INSUFFICIENT", "保证金不足");
    }
}

// 发送强平指令（包含完整上下文）
public void triggerLiquidationWithContext(Long userId, String symbol, String liquidationType, String reason) {
    // 1. 获取当前市场价格
    BigDecimal indexPrice = getCurrentPrice(symbol);
    
    // 2. 获取用户资金信息
    BigDecimal balance = getUserBalance(userId);
    BigDecimal margin = getUserMargin(userId, symbol);
    
    // 3. 计算盈亏信息
    BigDecimal unrealizedPnl = calculateUnrealizedPnl(userId, symbol);
    BigDecimal realizedPnl = calculateRealizedPnl(userId, symbol);
    
    // 4. 获取保证金率配置
    BigDecimal maintenanceMarginRatio = getMaintenanceMarginRatio(symbol);
    BigDecimal initialMarginRatio = getInitialMarginRatio(symbol);
    
    // 5. 构建强平请求（包含完整上下文）
    EventLiquidationReq liquidationReq = new EventLiquidationReq();
    liquidationReq.setLiquidationId("LIQ_" + System.currentTimeMillis());
    liquidationReq.setUserId(userId);
    liquidationReq.setSymbol(symbol);
    liquidationReq.setLiquidationType(liquidationType);
    liquidationReq.setReason(reason);
    liquidationReq.setIsFullLiquidation(true);
    liquidationReq.setIsEmergency(true);
    
    // 设置风控计算的上下文信息
    liquidationReq.setIndexPrice(indexPrice);
    liquidationReq.setBalance(balance);
    liquidationReq.setMargin(margin);
    liquidationReq.setRiskRatio(calculateRiskRatio(userId, symbol));
    liquidationReq.setUnrealizedPnl(unrealizedPnl);
    liquidationReq.setRealizedPnl(realizedPnl);
    liquidationReq.setMaintenanceMarginRatio(maintenanceMarginRatio);
    liquidationReq.setInitialMarginRatio(initialMarginRatio);
    liquidationReq.setPositionInfo(getPositionInfo(userId, symbol));
    liquidationReq.setMarketInfo(getMarketInfo(symbol));
    liquidationReq.setRiskCalculationTime(System.currentTimeMillis());
    
    // 6. 发送到撮合引擎
    sendToMatchEngine(liquidationReq);
}
```

### 2. 撮合引擎重新计算风险率

```java
// 撮合引擎接收强平指令并重新计算风险率
public void handleLiquidation(EventLiquidationReq liquidationReq) {
    // 1. 根据风控服务传递的数据重新计算风险率
    RiskRecalculationService.RiskRecalculationResult riskResult = 
        riskRecalculationService.recalculateRisk(liquidationRequest);
    
    log.info("风险重新计算完成，用户: {}, 交易对: {}, 风险率: {}, 风险等级: {}, 需要降档: {}, 需要强平: {}", 
            liquidationRequest.getUserId(), liquidationRequest.getSymbol(), 
            riskResult.getRiskRatio(), riskResult.getRiskLevel().getName(), 
            riskResult.isNeedRiskReduction(), riskResult.isNeedLiquidation());
    
    // 2. 如果重新计算后不需要强平，直接返回
    if (!riskResult.isNeedLiquidation()) {
        log.info("重新计算后不需要强平，用户: {}, 交易对: {}, 风险率: {}", 
                liquidationRequest.getUserId(), liquidationRequest.getSymbol(), riskResult.getRiskRatio());
        return;
    }
    
    // 3. 如果需要风险降档，先执行降档
    if (riskResult.isNeedRiskReduction()) {
        log.info("执行风险降档，用户: {}, 交易对: {}, 风险等级: {}", 
                liquidationRequest.getUserId(), liquidationRequest.getSymbol(), 
                riskResult.getRiskLevel().getName());
        riskManagementService.executeRiskReduction(
            liquidationRequest.getUserId(), 
            liquidationRequest.getSymbol(), 
            riskResult.getRiskLevel()
        );
        
        // 4. 降档后重新计算风险率
        log.info("降档后重新计算风险率，用户: {}, 交易对: {}", 
                liquidationRequest.getUserId(), liquidationRequest.getSymbol());
        riskResult = riskRecalculationService.recalculateRisk(liquidationRequest);
        
        // 5. 如果降档后不需要强平，直接返回
        if (!riskResult.isNeedLiquidation()) {
            log.info("降档后不需要强平，用户: {}, 交易对: {}, 风险率: {}", 
                    liquidationRequest.getUserId(), liquidationRequest.getSymbol(), riskResult.getRiskRatio());
            return;
        }
    }
    
    // 6. 确定ADL策略
    ADLStrategy adlStrategy = determineADLStrategy(
        liquidationRequest.getLiquidationType(), 
        riskResult.getRiskLevel()
    );
    
    // 7. 执行ADL策略
    if (adlStrategy.isADLStrategy()) {
        riskManagementService.executeADLStrategy(
            liquidationRequest.getSymbol(), 
            adlStrategy, 
            liquidationQuantity
        );
    }
    
    // 8. 执行最终强平
    if (liquidationQuantity.compareTo(BigDecimal.ZERO) > 0) {
        executeFinalLiquidation(liquidationRequest, liquidationQuantity);
    }
}
```

## 风险重新计算逻辑

### 1. 使用风控服务传递的数据

```java
public RiskRecalculationResult recalculateRisk(LiquidationRequest liquidationRequest) {
    // 使用风控服务传递的指数价格
    BigDecimal currentPrice = liquidationRequest.getIndexPrice();
    if (currentPrice == null || currentPrice.compareTo(BigDecimal.ZERO) <= 0) {
        currentPrice = getCurrentPrice(symbol);
    }
    
    // 使用风控服务传递的保证金
    BigDecimal margin = liquidationRequest.getMargin();
    if (margin == null || margin.compareTo(BigDecimal.ZERO) <= 0) {
        margin = liquidationRequest.getBalance(); // 使用余额作为保证金
    }
    
    // 重新计算未实现盈亏
    BigDecimal unrealizedPnl = calculateUnrealizedPnl(position, currentPrice);
    
    // 重新计算风险率
    BigDecimal riskRatio = calculateRiskRatio(margin, unrealizedPnl);
    
    // 获取风险等级
    RiskLevel riskLevel = RiskLevel.getRiskLevel(riskRatio);
    
    return RiskRecalculationResult.builder()
            .riskRatio(riskRatio)
            .riskLevel(riskLevel)
            .needRiskReduction(riskLevel.needForceReduction())
            .needLiquidation(riskLevel.needImmediateLiquidation())
            .build();
}
```

### 2. 考虑标的风险限额

```java
private RiskLimitInfo calculateRiskLimits(Symbol symbolConfig, Position position, BigDecimal currentPrice) {
    BigDecimal positionValue = position.getQuantity().multiply(currentPrice);
    
    // 根据标的配置计算风险限额
    BigDecimal maxPositionValue = symbolConfig.getMaxPositionValue();
    BigDecimal maxLeverage = symbolConfig.getMaxLeverage();
    
    return RiskLimitInfo.builder()
            .maxPositionValue(maxPositionValue)
            .maxLeverage(maxLeverage)
            .currentPositionValue(positionValue)
            .currentLeverage(calculateLeverage(positionValue, position.getMargin()))
            .build();
}
```

## 优势特点

1. **数据完整性**：风控服务传递完整的上下文信息，确保撮合引擎有足够的数据进行判断
2. **双重验证**：风控服务和撮合引擎都进行风险计算，确保准确性
3. **动态调整**：降档后重新计算风险率，可能避免不必要的强平
4. **标的风险限额**：考虑不同标的的风险限额配置
5. **实时性**：使用风控服务传递的实时价格和资金信息
6. **可追溯性**：记录风控计算时间戳，便于问题排查

## 注意事项

1. **数据一致性**：确保风控服务和撮合引擎使用相同的数据源
2. **时间同步**：风控计算时间戳与撮合引擎处理时间的差异
3. **价格准确性**：使用风控服务传递的指数价格，避免价格差异
4. **资金准确性**：使用风控服务传递的资金信息，确保计算准确
5. **配置同步**：确保标的风险限额配置在风控和撮合引擎中一致
6. **性能考虑**：重新计算风险率可能增加处理时间，需要优化 