# 标的创建与风险限额配置流程

## 概述

本系统要求所有标的在创建时必须配置风险限额，没有风险限额配置的合约不允许进行下单。这确保了交易的安全性和风险控制的有效性。

## 核心原则

### 1. 风险限额配置是标的创建的必要条件
- 标的创建后必须配置风险限额才能交易
- 未配置风险限额的标的无法下单
- 风险限额配置后标的自动激活

### 2. 标的状态管理
- **INACTIVE**：标的已创建但未配置风险限额
- **ACTIVE**：标的已配置风险限额，可以交易

### 3. 交易权限控制
- 只有配置了风险限额的标的才能进行交易
- 订单验证服务会检查标的风险限额配置状态

## 标的创建流程

### 1. 创建标的

```java
@Autowired
private SymbolManagementService symbolManagementService;

// 创建标的（初始状态为INACTIVE）
Symbol symbol = symbolManagementService.createSymbol(
    "BTCUSDT",                    // 交易对
    TradingType.PERPETUAL,        // 交易类型
    "BTC",                        // 基础货币
    "USDT",                       // 计价货币
    new BigDecimal("0.001"),      // 最小交易数量
    new BigDecimal("1000"),       // 最大交易数量
    8,                            // 数量精度
    2,                            // 价格精度
    new BigDecimal("0.1"),        // 最小价格变动单位
    new BigDecimal("0.001"),      // 手续费率
    true,                         // 支持杠杆
    true                          // 支持做空
);
```

### 2. 配置风险限额

#### 方式一：使用默认配置
```java
// 使用默认风险限额配置
symbolManagementService.configureRiskLimitWithDefault("BTCUSDT");
```

#### 方式二：使用自定义配置
```java
// 创建自定义风险限额配置
SymbolRiskLimitConfig customConfig = new SymbolRiskLimitConfig();
customConfig.setSymbol("BTCUSDT");

// 配置逐仓模式风险限额
SymbolRiskLimitConfig.IsolatedModeRiskLimitConfig isolatedConfig = customConfig.getIsolatedModeConfig();
SymbolRiskLimitConfig.RiskLevelConfig isolatedWarning = isolatedConfig.getRiskLevelConfig(RiskLevel.WARNING);
isolatedWarning.setThreshold(new BigDecimal("0.85"));
isolatedWarning.setReductionRatio(new BigDecimal("0.1"));
isolatedWarning.setLiquidationRatio(new BigDecimal("0.2"));
isolatedWarning.setMaxLeverage(new BigDecimal("50"));

// 配置全仓模式风险限额
SymbolRiskLimitConfig.CrossModeRiskLimitConfig crossConfig = customConfig.getCrossModeConfig();
SymbolRiskLimitConfig.RiskLevelConfig crossWarning = crossConfig.getRiskLevelConfig(RiskLevel.WARNING);
crossWarning.setThreshold(new BigDecimal("0.82"));
crossWarning.setReductionRatio(new BigDecimal("0.15"));
crossWarning.setLiquidationRatio(new BigDecimal("0.1"));
crossWarning.setMaxLeverage(new BigDecimal("75"));

// 应用配置
symbolManagementService.configureRiskLimit("BTCUSDT", customConfig);
```

### 3. 批量创建标的

```java
List<SymbolManagementService.SymbolCreationRequest> requests = new ArrayList<>();

// 创建BTCUSDT标的请求
SymbolManagementService.SymbolCreationRequest btcRequest = new SymbolManagementService.SymbolCreationRequest();
btcRequest.setSymbol("BTCUSDT");
btcRequest.setTradingType(TradingType.PERPETUAL);
btcRequest.setBaseCurrency("BTC");
btcRequest.setQuoteCurrency("USDT");
btcRequest.setMinQuantity(new BigDecimal("0.001"));
btcRequest.setMaxQuantity(new BigDecimal("1000"));
btcRequest.setQuantityPrecision(8);
btcRequest.setPricePrecision(2);
btcRequest.setTickSize(new BigDecimal("0.1"));
btcRequest.setFeeRate(new BigDecimal("0.001"));
btcRequest.setSupportsLeverage(true);
btcRequest.setSupportsShort(true);
btcRequest.setConfigureRiskLimit(true); // 自动配置风险限额

requests.add(btcRequest);

// 批量创建标的
List<Symbol> createdSymbols = symbolManagementService.batchCreateSymbols(requests);
```

## 订单验证流程

### 1. 订单验证服务

```java
@Autowired
private OrderValidationService orderValidationService;

// 验证订单
OrderValidationService.OrderValidationResult result = orderValidationService.validateOrder(order);

if (!result.isValid()) {
    log.error("订单验证失败: {}", result.getErrorMessage());
    // 处理验证失败的情况
}
```

### 2. 验证检查项

订单验证服务会检查以下项目：

1. **标的是否存在**
2. **标的是否配置了风险限额**
3. **标的是否可以交易**
4. **价格是否有效**
5. **数量是否有效**
6. **杠杆支持检查**
7. **做空支持检查**

### 3. 错误码说明

| 错误码 | 说明 | 解决方案 |
|--------|------|----------|
| SYMBOL_NOT_FOUND | 标的不存在 | 检查交易对名称是否正确 |
| SYMBOL_NOT_TRADEABLE | 标的不可交易 | 配置风险限额或激活标的 |
| SYMBOL_INACTIVE | 标的未激活 | 激活标的 |
| INVALID_PRICE | 价格无效 | 检查价格格式和精度 |
| INVALID_QUANTITY | 数量无效 | 检查数量范围和精度 |
| LEVERAGE_NOT_SUPPORTED | 不支持杠杆 | 使用非杠杆订单 |
| SHORT_NOT_SUPPORTED | 不支持做空 | 使用买入订单 |

## 标的管理操作

### 1. 检查标的状态

```java
// 检查标的是否可以交易
boolean canTrade = symbolManagementService.canTrade("BTCUSDT");

// 获取标的交易状态信息
OrderValidationService.SymbolTradeStatus status = orderValidationService.getSymbolTradeStatus("BTCUSDT");
System.out.println("标的状态: " + status.getMessage());
```

### 2. 获取标的列表

```java
// 获取所有可交易的标的
List<Symbol> tradeableSymbols = symbolManagementService.getTradeableSymbols();

// 获取所有已配置风险限额的标的
List<Symbol> configuredSymbols = symbolManagementService.getConfiguredSymbols();

// 获取所有未配置风险限额的标的
List<Symbol> unconfiguredSymbols = symbolManagementService.getUnconfiguredSymbols();
```

### 3. 标的状态管理

```java
// 激活标的
symbolManagementService.activateSymbol("BTCUSDT");

// 停用标的
symbolManagementService.deactivateSymbol("BTCUSDT");

// 删除标的（需要确保没有活跃订单和持仓）
symbolManagementService.deleteSymbol("BTCUSDT");
```

### 4. 更新风险限额配置

```java
// 更新风险限额配置
SymbolRiskLimitConfig updatedConfig = new SymbolRiskLimitConfig();
// ... 配置新的风险限额参数

symbolManagementService.updateRiskLimit("BTCUSDT", updatedConfig);
```

## 风险限额配置示例

### USDT交易对配置（相对保守）

```java
SymbolRiskLimitConfig usdtConfig = new SymbolRiskLimitConfig();

// 逐仓模式配置
SymbolRiskLimitConfig.IsolatedModeRiskLimitConfig isolatedConfig = usdtConfig.getIsolatedModeConfig();
isolatedConfig.setMaxLeverage(new BigDecimal("50"));

// 设置各风险等级配置
SymbolRiskLimitConfig.RiskLevelConfig isolatedWarning = isolatedConfig.getRiskLevelConfig(RiskLevel.WARNING);
isolatedWarning.setThreshold(new BigDecimal("0.85"));
isolatedWarning.setReductionRatio(new BigDecimal("0.05"));
isolatedWarning.setLiquidationRatio(new BigDecimal("0.15"));
isolatedWarning.setMaxLeverage(new BigDecimal("50"));

// 全仓模式配置
SymbolRiskLimitConfig.CrossModeRiskLimitConfig crossConfig = usdtConfig.getCrossModeConfig();
crossConfig.setMaxLeverage(new BigDecimal("75"));

SymbolRiskLimitConfig.RiskLevelConfig crossWarning = crossConfig.getRiskLevelConfig(RiskLevel.WARNING);
crossWarning.setThreshold(new BigDecimal("0.82"));
crossWarning.setReductionRatio(new BigDecimal("0.1"));
crossWarning.setLiquidationRatio(new BigDecimal("0.08"));
crossWarning.setMaxLeverage(new BigDecimal("75"));
```

### BTC交易对配置（相对激进）

```java
SymbolRiskLimitConfig btcConfig = new SymbolRiskLimitConfig();

// 逐仓模式配置
SymbolRiskLimitConfig.IsolatedModeRiskLimitConfig isolatedConfig = btcConfig.getIsolatedModeConfig();
isolatedConfig.setMaxLeverage(new BigDecimal("25"));

// 设置各风险等级配置
SymbolRiskLimitConfig.RiskLevelConfig isolatedWarning = isolatedConfig.getRiskLevelConfig(RiskLevel.WARNING);
isolatedWarning.setThreshold(new BigDecimal("0.75"));
isolatedWarning.setReductionRatio(new BigDecimal("0.15"));
isolatedWarning.setLiquidationRatio(new BigDecimal("0.25"));
isolatedWarning.setMaxLeverage(new BigDecimal("25"));

// 全仓模式配置
SymbolRiskLimitConfig.CrossModeRiskLimitConfig crossConfig = btcConfig.getCrossModeConfig();
crossConfig.setMaxLeverage(new BigDecimal("50"));

SymbolRiskLimitConfig.RiskLevelConfig crossWarning = crossConfig.getRiskLevelConfig(RiskLevel.WARNING);
crossWarning.setThreshold(new BigDecimal("0.72"));
crossWarning.setReductionRatio(new BigDecimal("0.2"));
crossWarning.setLiquidationRatio(new BigDecimal("0.12"));
crossWarning.setMaxLeverage(new BigDecimal("50"));
```

## 监控和统计

### 1. 获取统计信息

```java
// 获取标的统计信息
String stats = symbolManagementService.getSymbolStats();
System.out.println(stats);
// 输出示例：标的统计: 总数=10, 已配置=8, 可交易=6

// 获取配置统计信息
String configStats = symbolRiskLimitConfigManager.getConfigStats();
System.out.println(configStats);
// 输出示例：Symbol风险限额配置统计: 总配置数=8, 自定义配置数=3
```

### 2. 日志监控

系统会记录以下关键操作：

- 标的创建：`开始创建标的: BTCUSDT, 交易类型: PERPETUAL`
- 风险限额配置：`开始配置标的风险限额: BTCUSDT`
- 标的激活：`标的激活成功: BTCUSDT`
- 订单验证：`订单验证通过: orderId=xxx, symbol=BTCUSDT`

## 最佳实践

### 1. 标的创建流程

1. **创建标的**：使用`createSymbol`方法创建标的
2. **配置风险限额**：使用`configureRiskLimit`或`configureRiskLimitWithDefault`配置风险限额
3. **验证配置**：使用`canTrade`方法验证标的是否可以交易
4. **激活标的**：配置风险限额后标的自动激活

### 2. 风险限额配置建议

- **USDT交易对**：使用相对保守的配置，适合稳定币交易
- **BTC交易对**：使用相对激进的配置，适合高波动交易
- **ETH交易对**：使用中等配置，适合中等波动交易

### 3. 错误处理

- 始终检查订单验证结果
- 处理标的不存在的情况
- 处理风险限额未配置的情况
- 记录详细的错误信息

### 4. 性能考虑

- 使用缓存机制提高配置查询性能
- 批量操作减少数据库访问
- 异步处理大量标的创建

## 总结

通过这个系统，我们确保了：

1. **安全性**：所有标的都必须配置风险限额才能交易
2. **灵活性**：支持不同标的的差异化风险限额配置
3. **可维护性**：清晰的配置结构和完整的验证流程
4. **可扩展性**：支持批量操作和动态配置更新

这个设计确保了交易系统的安全性和风险控制的有效性。 