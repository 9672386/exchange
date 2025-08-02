# 重新设计的Symbol风险限额配置系统

## 概述

基于用户反馈，我们重新设计了Symbol风险限额配置系统。新的设计解决了原有模型中的问题：

1. **全仓和逐仓使用不同的风险限额**：每个仓位模式都有独立的配置
2. **每个档位有独立的配置**：不同风险等级（NORMAL、WARNING、DANGER、EMERGENCY、LIQUIDATION）都有独立的配置参数
3. **更灵活的配置结构**：支持按Symbol和仓位模式进行精细化配置

## 问题分析

### 原有模型的问题

1. **全仓和逐仓使用同一个风险限额**：实际上它们有不同的风险特征和强平策略
2. **档位配置不够独立**：不同风险等级使用相同的配置参数
3. **配置结构过于复杂**：将全仓和逐仓分开，但实际上它们应该共享基础的风险等级配置

### 新模型的设计原则

1. **全仓和逐仓独立配置**：每个仓位模式都有完整的风险等级配置
2. **每个档位独立配置**：每个风险等级都有独立的阈值、减仓比例、平仓比例、最大杠杆等参数
3. **配置结构清晰**：使用Map结构存储各档位的配置，便于查找和修改

## 新的配置模型

### 核心结构

```java
public class SymbolRiskLimitConfig {
    // 逐仓模式风险限额配置
    private IsolatedModeRiskLimitConfig isolatedModeConfig;
    
    // 全仓模式风险限额配置
    private CrossModeRiskLimitConfig crossModeConfig;
    
    // 强平配置
    private LiquidationRiskLimitConfig liquidationConfig;
}
```

### 逐仓模式配置

```java
public static class IsolatedModeRiskLimitConfig {
    // 最大杠杆倍数
    private BigDecimal maxLeverage = new BigDecimal("100");
    
    // 各风险等级的独立配置
    private Map<RiskLevel, RiskLevelConfig> riskLevelConfigs = new HashMap<>();
    
    // 是否启用分档平仓
    private Boolean enableTieredLiquidation = true;
    
    // 最大分档步骤数
    private Integer maxTieredSteps = 5;
}
```

### 全仓模式配置

```java
public static class CrossModeRiskLimitConfig {
    // 最大杠杆倍数
    private BigDecimal maxLeverage = new BigDecimal("125");
    
    // 各风险等级的独立配置
    private Map<RiskLevel, RiskLevelConfig> riskLevelConfigs = new HashMap<>();
    
    // 是否启用分档平仓
    private Boolean enableTieredLiquidation = true;
    
    // 最大分档步骤数
    private Integer maxTieredSteps = 3;
    
    // 强平优先级
    private String liquidationPriority = "UNREALIZED_PNL";
}
```

### 单个风险等级配置

```java
public static class RiskLevelConfig {
    // 风险阈值
    private BigDecimal threshold;
    
    // 减仓比例
    private BigDecimal reductionRatio;
    
    // 平仓比例
    private BigDecimal liquidationRatio;
    
    // 最大杠杆倍数
    private BigDecimal maxLeverage;
    
    // 是否允许开仓
    private Boolean allowOpenPosition = true;
    
    // 是否允许平仓
    private Boolean allowClosePosition = true;
    
    // 是否强制减仓
    private Boolean forceReduction = false;
    
    // 是否立即强平
    private Boolean immediateLiquidation = false;
}
```

## 配置策略对比

### 逐仓模式默认配置

| 风险等级 | 阈值 | 减仓比例 | 平仓比例 | 最大杠杆 |
|---------|------|----------|----------|----------|
| NORMAL | 0.8 | 0% | 10% | 100 |
| WARNING | 0.9 | 10% | 20% | 50 |
| DANGER | 0.95 | 30% | 30% | 25 |
| EMERGENCY | 0.98 | 50% | 40% | 10 |
| LIQUIDATION | 1.0 | 100% | 50% | 1 |

### 全仓模式默认配置

| 风险等级 | 阈值 | 减仓比例 | 平仓比例 | 最大杠杆 |
|---------|------|----------|----------|----------|
| NORMAL | 0.8 | 0% | 5% | 125 |
| WARNING | 0.85 | 15% | 10% | 75 |
| DANGER | 0.9 | 35% | 20% | 50 |
| EMERGENCY | 0.95 | 60% | 30% | 25 |
| LIQUIDATION | 1.0 | 100% | 40% | 1 |

## 差异化配置策略

### USDT交易对（相对保守）

#### 逐仓模式
- **WARNING**：阈值85%，减仓5%，平仓15%，杠杆50
- **DANGER**：阈值92%，减仓25%，平仓25%，杠杆25
- **EMERGENCY**：阈值96%，减仓40%，平仓35%，杠杆10

#### 全仓模式
- **WARNING**：阈值82%，减仓10%，平仓8%，杠杆75
- **DANGER**：阈值88%，减仓25%，平仓15%，杠杆50
- **EMERGENCY**：阈值92%，减仓50%，平仓25%，杠杆25

### BTC交易对（相对激进）

#### 逐仓模式
- **WARNING**：阈值75%，减仓15%，平仓25%，杠杆25
- **DANGER**：阈值85%，减仓35%，平仓40%，杠杆15
- **EMERGENCY**：阈值92%，减仓60%，平仓50%，杠杆5

#### 全仓模式
- **WARNING**：阈值72%，减仓20%，平仓12%，杠杆50
- **DANGER**：阈值82%，减仓40%，平仓25%，杠杆30
- **EMERGENCY**：阈值88%，减仓70%，平仓35%，杠杆15

### ETH交易对（中等配置）

#### 逐仓模式
- **WARNING**：阈值80%，减仓10%，平仓18%，杠杆75
- **DANGER**：阈值88%，减仓30%，平仓30%，杠杆40
- **EMERGENCY**：阈值94%，减仓50%，平仓40%，杠杆15

#### 全仓模式
- **WARNING**：阈值78%，减仓12%，平仓9%，杠杆100
- **DANGER**：阈值85%，减仓30%，平仓18%，杠杆60
- **EMERGENCY**：阈值90%，减仓55%，平仓28%，杠杆30

## 使用方法

### 1. 获取配置管理器

```java
@Autowired
private SymbolRiskLimitConfigManager configManager;
```

### 2. 获取风险阈值

```java
// 逐仓模式风险阈值
BigDecimal isolatedThreshold = configManager.getIsolatedRiskThreshold("BTCUSDT", RiskLevel.WARNING);

// 全仓模式风险阈值
BigDecimal crossThreshold = configManager.getCrossRiskThreshold("BTCUSDT", RiskLevel.WARNING);
```

### 3. 获取减仓比例

```java
// 逐仓模式减仓比例
BigDecimal isolatedReduction = configManager.getIsolatedReductionRatio("BTCUSDT", RiskLevel.DANGER);

// 全仓模式减仓比例
BigDecimal crossReduction = configManager.getCrossReductionRatio("BTCUSDT", RiskLevel.DANGER);
```

### 4. 获取平仓比例

```java
// 逐仓模式平仓比例
BigDecimal isolatedLiquidation = configManager.getIsolatedTieredLiquidationRatio("BTCUSDT", RiskLevel.LIQUIDATION);

// 全仓模式平仓比例
BigDecimal crossLiquidation = configManager.getCrossTieredLiquidationRatio("BTCUSDT", RiskLevel.LIQUIDATION);
```

### 5. 检查分档平仓设置

```java
// 检查逐仓模式是否启用分档平仓
boolean isolatedEnabled = configManager.isIsolatedTieredLiquidationEnabled("BTCUSDT");

// 检查全仓模式是否启用分档平仓
boolean crossEnabled = configManager.isCrossTieredLiquidationEnabled("BTCUSDT");
```

### 6. 获取最大步骤数

```java
// 逐仓模式最大分档步骤数
int isolatedSteps = configManager.getIsolatedMaxTieredSteps("BTCUSDT");

// 全仓模式最大分档步骤数
int crossSteps = configManager.getCrossMaxTieredSteps("BTCUSDT");
```

## 集成到RiskManagementService

### 更新getLiquidationRatioForTier方法

```java
private BigDecimal getLiquidationRatioForTier(RiskLevel riskLevel, LiquidationRequest liquidationRequest) {
    String symbol = liquidationRequest.getSymbol();
    Position position = memoryManager.getPosition(liquidationRequest.getUserId(), symbol);
    
    if (position.isIsolatedMode()) {
        return symbolRiskLimitConfigManager.getIsolatedTieredLiquidationRatio(symbol, riskLevel);
    } else {
        return symbolRiskLimitConfigManager.getCrossTieredLiquidationRatio(symbol, riskLevel);
    }
}
```

### 更新isTieredLiquidationEnabled方法

```java
private boolean isTieredLiquidationEnabled(Position position) {
    String symbol = position.getSymbol();
    if (position.isIsolatedMode()) {
        return symbolRiskLimitConfigManager.isIsolatedTieredLiquidationEnabled(symbol);
    } else {
        return symbolRiskLimitConfigManager.isCrossTieredLiquidationEnabled(symbol);
    }
}
```

## 优势

### 1. 更精确的风险控制
- 全仓和逐仓使用不同的风险限额配置
- 每个风险等级都有独立的配置参数
- 支持按Symbol特性进行差异化配置

### 2. 更灵活的配置管理
- 使用Map结构存储配置，便于查找和修改
- 支持运行时动态更新配置
- 支持批量配置管理

### 3. 更好的可维护性
- 配置结构清晰，易于理解
- 支持配置验证和校验
- 完善的测试覆盖

### 4. 更高的性能
- 使用缓存机制提高性能
- 支持并发访问
- 内存占用可控

## 测试

### 单元测试

```java
@Test
void testUSDTConfigCustomization() {
    SymbolRiskLimitConfig config = configManager.getRiskLimitConfig("BTCUSDT");
    
    // 验证逐仓模式配置
    SymbolRiskLimitConfig.IsolatedModeRiskLimitConfig isolatedConfig = config.getIsolatedModeConfig();
    assertEquals(new BigDecimal("50"), isolatedConfig.getMaxLeverage());
    
    // 验证逐仓模式各风险等级配置
    SymbolRiskLimitConfig.RiskLevelConfig isolatedWarning = isolatedConfig.getRiskLevelConfig(RiskLevel.WARNING);
    assertEquals(new BigDecimal("0.85"), isolatedWarning.getThreshold());
    assertEquals(new BigDecimal("0.05"), isolatedWarning.getReductionRatio());
    assertEquals(new BigDecimal("0.15"), isolatedWarning.getLiquidationRatio());
    assertEquals(new BigDecimal("50"), isolatedWarning.getMaxLeverage());
}
```

### 集成测试

```java
@Test
void testRiskManagementIntegration() {
    // 测试风险管理服务与配置管理器的集成
    BigDecimal ratio = riskManagementService.getLiquidationRatioForTier(
        RiskLevel.LIQUIDATION, liquidationRequest);
    
    // 验证配置是否正确应用
    assertNotNull(ratio);
    assertTrue(ratio.compareTo(BigDecimal.ZERO) > 0);
}
```

## 总结

重新设计的Symbol风险限额配置系统解决了原有模型中的问题，提供了更精确、更灵活的风险控制能力。新系统支持：

1. **全仓和逐仓独立配置**：每个仓位模式都有完整的风险等级配置
2. **每个档位独立配置**：每个风险等级都有独立的参数设置
3. **差异化配置策略**：根据Symbol特性设置不同的配置
4. **动态配置管理**：支持运行时更新和批量管理

这个新系统更好地满足了实际业务需求，提供了更精确的风险控制能力。 