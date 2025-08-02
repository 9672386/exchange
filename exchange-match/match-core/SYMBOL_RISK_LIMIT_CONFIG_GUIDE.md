# Symbol风险限额配置系统

## 概述

Symbol风险限额配置系统是一个支持按交易对（Symbol）配置不同风险限额参数的系统。该系统解决了原有系统中风险限额配置不够灵活的问题，支持为不同的交易对设置差异化的风险控制参数。

## 问题分析

### 原有系统的问题

1. **缺少按Symbol的差异化配置**：所有交易对使用相同的风险限额配置
2. **配置不够灵活**：无法根据不同Symbol的特性设置不同的风险限额
3. **缺少动态配置能力**：无法在运行时调整风险限额
4. **硬编码配置**：风险等级和减仓比例硬编码在枚举中

### 解决方案

1. **创建SymbolRiskLimitConfig类**：支持按Symbol配置风险限额
2. **实现SymbolRiskLimitConfigManager**：管理不同Symbol的风险限额配置
3. **集成到RiskManagementService**：在风险管理服务中使用新的配置系统
4. **支持动态配置**：支持运行时更新配置

## 系统架构

### 核心组件

#### 1. SymbolRiskLimitConfig
```java
public class SymbolRiskLimitConfig {
    private String symbol;                    // 交易对
    private RiskLevelLimitConfig riskLevelConfig;        // 风险等级配置
    private IsolatedModeRiskLimitConfig isolatedModeConfig;  // 逐仓模式配置
    private CrossModeRiskLimitConfig crossModeConfig;        // 全仓模式配置
    private LiquidationRiskLimitConfig liquidationConfig;    // 强平配置
}
```

#### 2. SymbolRiskLimitConfigManager
```java
@Service
public class SymbolRiskLimitConfigManager {
    // 获取Symbol的风险限额配置
    public SymbolRiskLimitConfig getRiskLimitConfig(String symbol);
    
    // 设置Symbol的风险限额配置
    public void setRiskLimitConfig(String symbol, SymbolRiskLimitConfig config);
    
    // 获取风险等级阈值
    public BigDecimal getRiskThreshold(String symbol, RiskLevel riskLevel);
    
    // 获取减仓比例
    public BigDecimal getReductionRatio(String symbol, RiskLevel riskLevel);
}
```

### 配置结构

#### 风险等级配置 (RiskLevelLimitConfig)
```java
public static class RiskLevelLimitConfig {
    private BigDecimal normalThreshold = new BigDecimal("0.8");      // 正常等级阈值
    private BigDecimal warningThreshold = new BigDecimal("0.9");     // 预警等级阈值
    private BigDecimal dangerThreshold = new BigDecimal("0.95");     // 危险等级阈值
    private BigDecimal emergencyThreshold = new BigDecimal("0.98");  // 紧急等级阈值
    private BigDecimal liquidationThreshold = new BigDecimal("1.0"); // 强平等级阈值
    
    // 各等级减仓比例配置
    private Map<RiskLevel, BigDecimal> reductionRatios;
}
```

#### 逐仓模式配置 (IsolatedModeRiskLimitConfig)
```java
public static class IsolatedModeRiskLimitConfig {
    private BigDecimal maxLeverage = new BigDecimal("100");         // 最大杠杆倍数
    private BigDecimal riskThreshold = new BigDecimal("0.98");       // 风险阈值
    private BigDecimal liquidationRatio = new BigDecimal("0.5");     // 强平比例
    private Boolean enableTieredLiquidation = true;                 // 是否启用分档平仓
    private Integer maxTieredSteps = 5;                             // 最大分档步骤数
    
    // 各档位平仓比例配置
    private Map<RiskLevel, BigDecimal> tieredLiquidationRatios;
}
```

#### 全仓模式配置 (CrossModeRiskLimitConfig)
```java
public static class CrossModeRiskLimitConfig {
    private BigDecimal maxLeverage = new BigDecimal("125");         // 最大杠杆倍数
    private BigDecimal riskThreshold = new BigDecimal("0.95");       // 风险阈值
    private BigDecimal liquidationRatio = new BigDecimal("0.4");     // 强平比例
    private Boolean enableTieredLiquidation = true;                 // 是否启用分档平仓
    private Integer maxTieredSteps = 3;                             // 最大分档步骤数
    private String liquidationPriority = "UNREALIZED_PNL";          // 强平优先级
    
    // 各档位平仓比例配置
    private Map<RiskLevel, BigDecimal> tieredLiquidationRatios;
}
```

## 配置策略

### 按Symbol类型的差异化配置

#### 1. USDT交易对（相对保守）
- **风险阈值**：预警85%，危险92%，紧急96%
- **减仓比例**：预警5%，危险25%，紧急40%
- **杠杆限制**：逐仓50倍，全仓75倍
- **适用场景**：稳定币交易对，波动相对较小

#### 2. BTC交易对（相对激进）
- **风险阈值**：预警75%，危险85%，紧急92%
- **减仓比例**：预警15%，危险35%，紧急60%
- **杠杆限制**：逐仓25倍，全仓50倍
- **适用场景**：高波动交易对，需要更严格的风险控制

#### 3. ETH交易对（中等配置）
- **风险阈值**：预警80%，危险88%，紧急94%
- **减仓比例**：预警10%，危险30%，紧急50%
- **杠杆限制**：逐仓75倍，全仓100倍
- **适用场景**：中等波动交易对

## 使用方法

### 1. 获取Symbol配置
```java
@Autowired
private SymbolRiskLimitConfigManager configManager;

// 获取Symbol的风险限额配置
SymbolRiskLimitConfig config = configManager.getRiskLimitConfig("BTCUSDT");
```

### 2. 获取风险阈值
```java
// 获取特定风险等级的阈值
BigDecimal threshold = configManager.getRiskThreshold("BTCUSDT", RiskLevel.WARNING);
```

### 3. 获取减仓比例
```java
// 获取特定风险等级的减仓比例
BigDecimal ratio = configManager.getReductionRatio("BTCUSDT", RiskLevel.DANGER);
```

### 4. 获取档位平仓比例
```java
// 逐仓模式档位平仓比例
BigDecimal isolatedRatio = configManager.getIsolatedTieredLiquidationRatio("BTCUSDT", RiskLevel.LIQUIDATION);

// 全仓模式档位平仓比例
BigDecimal crossRatio = configManager.getCrossTieredLiquidationRatio("BTCUSDT", RiskLevel.LIQUIDATION);
```

### 5. 设置自定义配置
```java
// 创建自定义配置
SymbolRiskLimitConfig customConfig = new SymbolRiskLimitConfig();
customConfig.setSymbol("CUSTOM");

// 设置风险等级配置
SymbolRiskLimitConfig.RiskLevelLimitConfig riskConfig = customConfig.getRiskLevelConfig();
riskConfig.setWarningThreshold(new BigDecimal("0.85"));
riskConfig.setReductionRatio(RiskLevel.WARNING, new BigDecimal("0.1"));

// 应用配置
configManager.setRiskLimitConfig("CUSTOM", customConfig);
```

## 集成到现有系统

### 1. RiskManagementService集成
```java
@Autowired
private SymbolRiskLimitConfigManager symbolRiskLimitConfigManager;

// 在getLiquidationRatioForTier方法中使用
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

### 2. 配置检查
```java
// 检查是否启用分档平仓
private boolean isTieredLiquidationEnabled(Position position) {
    String symbol = position.getSymbol();
    if (position.isIsolatedMode()) {
        return symbolRiskLimitConfigManager.isIsolatedTieredLiquidationEnabled(symbol);
    } else {
        return symbolRiskLimitConfigManager.isCrossTieredLiquidationEnabled(symbol);
    }
}
```

## 测试

### 单元测试
```java
@Test
void testUSDTConfigCustomization() {
    SymbolRiskLimitConfig config = configManager.getRiskLimitConfig("BTCUSDT");
    
    // 验证USDT交易对的特殊配置
    SymbolRiskLimitConfig.RiskLevelLimitConfig riskConfig = config.getRiskLevelConfig();
    assertEquals(new BigDecimal("0.85"), riskConfig.getWarningThreshold());
    assertEquals(new BigDecimal("0.92"), riskConfig.getDangerThreshold());
    assertEquals(new BigDecimal("0.96"), riskConfig.getEmergencyThreshold());
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

## 优势

### 1. 灵活性
- 支持按Symbol配置不同的风险限额
- 支持动态更新配置
- 支持批量配置管理

### 2. 可扩展性
- 易于添加新的Symbol类型
- 支持自定义配置策略
- 支持配置验证和校验

### 3. 性能
- 使用缓存提高性能
- 支持并发访问
- 内存占用可控

### 4. 可维护性
- 清晰的配置结构
- 完善的测试覆盖
- 详细的文档说明

## 未来扩展

### 1. 数据库持久化
- 将配置持久化到数据库
- 支持配置版本管理
- 支持配置回滚

### 2. 动态配置更新
- 支持热更新配置
- 支持配置变更通知
- 支持配置变更审计

### 3. 配置验证
- 添加配置验证规则
- 支持配置冲突检测
- 支持配置合理性检查

### 4. 监控和告警
- 配置使用情况监控
- 配置变更告警
- 配置性能监控

## 总结

Symbol风险限额配置系统解决了原有系统中风险限额配置不够灵活的问题，通过支持按Symbol配置不同的风险限额参数，提高了系统的灵活性和可维护性。该系统为不同特性的交易对提供了差异化的风险控制策略，更好地满足了实际业务需求。 