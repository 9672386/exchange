# 杠杆验证功能实现文档

## 概述

本文档描述了在订单中增加杠杆验证功能的实现，包括不同杠杆规则的支持、风险限额配置的集成，以及全局杠杆验证功能。

## 功能特性

### 1. 杠杆验证规则

系统支持四种杠杆验证规则：

#### 1.1 统一杠杆 (UNIFORM)
- **规则**: 同一用户在同一标的上只能使用相同的杠杆倍数
- **应用场景**: 风险控制较严格的场景，确保用户持仓的一致性
- **验证逻辑**: 检查新订单的杠杆是否与现有持仓的杠杆一致

#### 1.2 灵活杠杆 (FLEXIBLE)
- **规则**: 同一用户在同一标的上可以使用不同的杠杆倍数
- **应用场景**: 用户可以根据市场情况灵活调整杠杆
- **验证逻辑**: 只验证杠杆是否在允许范围内，不限制杠杆变化

#### 1.3 递增杠杆 (INCREASING)
- **规则**: 同一用户在同一标的上只能使用递增的杠杆倍数
- **应用场景**: 鼓励用户逐步增加风险敞口
- **验证逻辑**: 新订单的杠杆必须大于等于现有持仓的最大杠杆

#### 1.4 递减杠杆 (DECREASING)
- **规则**: 同一用户在同一标的上只能使用递减的杠杆倍数
- **应用场景**: 鼓励用户逐步降低风险敞口
- **验证逻辑**: 新订单的杠杆必须小于等于现有持仓的最小杠杆

### 2. 全局杠杆验证（新增）

#### 2.1 全局统一杠杆规则
- **规则**: 同一个用户在整个合约交易中只允许使用一个杠杆
- **应用场景**: 确保用户所有交易的一致性，简化风险管理
- **验证逻辑**: 检查用户在所有标的上的持仓和委托是否使用相同的杠杆

#### 2.2 杠杆调整限制
- **规则**: 在有仓位或委托的情况下，杠杆只允许增加不允许降低
- **应用场景**: 防止用户在已有风险敞口时降低杠杆，避免风险集中
- **验证逻辑**: 检查新订单杠杆是否大于等于现有最大杠杆

#### 2.3 杠杆同步调整
- **功能**: 当用户调整杠杆时，同步调整所有仓位和活跃委托
- **应用场景**: 确保用户所有持仓和委托使用相同的杠杆
- **实现**: 通过LeverageAdjustmentService实现同步调整

### 3. 风险限额配置

#### 3.1 逐仓模式配置
- 各风险等级独立配置最大杠杆
- 支持分档平仓
- 动态杠杆限制

#### 3.2 全仓模式配置
- 各风险等级独立配置最大杠杆
- 支持分档平仓
- 动态杠杆限制

#### 3.3 风险等级杠杆配置
撮合系统使用固定的NORMAL风险等级，确保系统稳定可控：
- **NORMAL**: 100倍（逐仓）/ 125倍（全仓）
- **其他风险等级**: 仅用于配置管理，撮合系统不进行动态调整

### 4. 杠杆验证流程

```
订单提交
    ↓
基础杠杆验证
    ↓
全局杠杆验证（新增）
    ↓
获取现有持仓
    ↓
根据杠杆规则验证
    ↓
风险限额验证
    ↓
验证通过/失败
```

## 实现组件

### 1. LeverageValidationService
**位置**: `com.exchange.match.core.service.LeverageValidationService`

**主要功能**:
- 验证订单杠杆的有效性
- 根据不同的杠杆规则进行验证
- 检查风险限额配置
- 提供详细的验证结果
- **新增**: 全局杠杆验证功能

**核心方法**:
```java
public LeverageValidationResult validateOrderLeverage(Order order)
private LeverageValidationResult validateGlobalLeverage(Order order)
private BigDecimal getMaxLeverage(Order order) // 动态计算最大杠杆
```

### 2. LeverageAdjustmentService（新增）
**位置**: `com.exchange.match.core.service.LeverageAdjustmentService`

**主要功能**:
- 处理杠杆调整时的仓位和委托同步
- 验证杠杆调整的有效性
- 重新计算保证金和强平价格
- 更新用户全局杠杆设置

**核心方法**:
```java
public LeverageAdjustmentResult adjustUserLeverage(Long userId, BigDecimal newLeverage)
```

### 3. SymbolRiskLimitConfigManager
**位置**: `com.exchange.match.core.service.SymbolRiskLimitConfigManager`

**主要功能**:
- 管理Symbol风险限额配置
- 提供杠杆验证规则获取
- 支持动态配置更新

**核心方法**:
```java
public LeverageValidationResult validateLeverage(String symbol, Long userId, 
                                               BigDecimal newLeverage, 
                                               BigDecimal existingLeverage, 
                                               RiskLevel riskLevel)
public BigDecimal getMaxLeverage(String symbol, RiskLevel riskLevel, boolean isCrossMode) // 动态计算最大杠杆
```

### 4. OrderValidationService
**位置**: `com.exchange.match.core.service.OrderValidationService`

**主要功能**:
- 集成杠杆验证到订单验证流程
- 提供完整的订单验证服务

**集成方式**:
```java
// 6. 验证杠杆
LeverageValidationService.LeverageValidationResult leverageResult = 
    leverageValidationService.validateOrderLeverage(order);
if (!leverageResult.isValid()) {
    result.setValid(false);
    result.setErrorCode(leverageResult.getErrorCode());
    result.setErrorMessage(leverageResult.getErrorMessage());
    return result;
}
```

## 配置说明

### 1. 风险限额配置结构

```java
public class SymbolRiskLimitConfig {
    private IsolatedModeRiskLimitConfig isolatedModeConfig;
    private CrossModeRiskLimitConfig crossModeConfig;
    private LiquidationRiskLimitConfig liquidationConfig;
}

public static class RiskLevelConfig {
    private BigDecimal maxLeverage;
    private Boolean allowLeverage;
    private LeverageValidationRule leverageValidationRule;
}
```

### 2. 杠杆验证规则枚举

```java
public enum LeverageValidationRule {
    UNIFORM,      // 统一杠杆
    FLEXIBLE,     // 灵活杠杆
    INCREASING,   // 递增杠杆
    DECREASING    // 递减杠杆
}
```

### 3. 全局杠杆信息结构（新增）

```java
public static class GlobalLeverageInfo {
    private Long userId;
    private List<Position> positions;
    private List<Order> activeOrders;
    private BigDecimal existingLeverage;
    private boolean hasExistingLeverage;
    private boolean hasMultipleLeverages;
    private boolean hasPositionsOrOrders;
}
```

## 使用示例

### 1. 创建订单时验证杠杆

```java
Order order = new Order();
order.setOrderId("ORDER_001");
order.setUserId(1001L);
order.setSymbol("BTCUSDT");
order.setLeverage(new BigDecimal("10"));
order.setQuantity(new BigDecimal("1.0"));
order.setPrice(new BigDecimal("50000"));

// 验证订单
OrderValidationResult result = orderValidationService.validateOrder(order);
if (result.isValid()) {
    // 订单验证通过，可以提交
    System.out.println("订单验证通过");
} else {
    // 订单验证失败
    System.out.println("订单验证失败: " + result.getErrorMessage());
}
```

### 2. 调整用户杠杆

```java
Long userId = 1001L;
BigDecimal newLeverage = new BigDecimal("20");

// 调整用户杠杆
LeverageAdjustmentService.LeverageAdjustmentResult result = 
    leverageAdjustmentService.adjustUserLeverage(userId, newLeverage);

if (result.isSuccess()) {
    System.out.println("杠杆调整成功: " + result.getPositionsCount() + "个持仓, " + 
                      result.getOrdersCount() + "个委托已同步调整");
} else {
    System.out.println("杠杆调整失败: " + result.getErrorMessage());
}
```

### 3. 配置风险限额

```java
SymbolRiskLimitConfig config = new SymbolRiskLimitConfig();
config.setSymbol("BTCUSDT");

// 设置各风险等级的最大杠杆
config.getIsolatedModeConfig().getRiskLevelConfig(RiskLevel.NORMAL).setMaxLeverage(new BigDecimal("100"));
config.getIsolatedModeConfig().getRiskLevelConfig(RiskLevel.WARNING).setMaxLeverage(new BigDecimal("50"));
config.getIsolatedModeConfig().getRiskLevelConfig(RiskLevel.DANGER).setMaxLeverage(new BigDecimal("25"));
config.getIsolatedModeConfig().getRiskLevelConfig(RiskLevel.EMERGENCY).setMaxLeverage(new BigDecimal("10"));
config.getIsolatedModeConfig().getRiskLevelConfig(RiskLevel.LIQUIDATION).setMaxLeverage(BigDecimal.ONE);

// 设置杠杆验证规则
config.getIsolatedModeConfig().getRiskLevelConfig(RiskLevel.NORMAL)
    .setLeverageValidationRule(LeverageValidationRule.UNIFORM);

// 应用配置
symbolRiskLimitConfigManager.setRiskLimitConfig("BTCUSDT", config);
```

### 4. 获取动态最大杠杆

```java
// 根据风险等级获取最大杠杆
RiskLevel currentRiskLevel = RiskLevel.NORMAL;
boolean isCrossMode = false; // 逐仓模式

BigDecimal maxLeverage = symbolRiskLimitConfigManager.getMaxLeverage("BTCUSDT", currentRiskLevel, isCrossMode);
System.out.println("当前风险等级最大杠杆: " + maxLeverage);
```

## 错误码说明

| 错误码 | 说明 | 解决方案 |
|--------|------|----------|
| LEVERAGE_TOO_LOW | 杠杆倍数小于最小值(1) | 将杠杆设置为1或更大 |
| LEVERAGE_EXCEEDED | 杠杆倍数超过最大允许值 | 降低杠杆倍数 |
| LEVERAGE_MISMATCH | 统一杠杆规则：杠杆不一致 | 使用与现有持仓相同的杠杆 |
| LEVERAGE_NOT_INCREASING | 递增杠杆规则：新杠杆小于现有杠杆 | 使用大于等于现有最大杠杆的值 |
| LEVERAGE_NOT_DECREASING | 递减杠杆规则：新杠杆大于现有杠杆 | 使用小于等于现有最小杠杆的值 |
| **GLOBAL_LEVERAGE_MISMATCH** | **全局杠杆规则：用户已有其他杠杆** | **使用与现有持仓/委托相同的杠杆** |
| **LEVERAGE_DECREASE_NOT_ALLOWED** | **有仓位或委托时不允许降低杠杆** | **只允许增加杠杆，或先平仓/撤单** |

## 测试

### 1. 单元测试
运行杠杆验证服务的单元测试：
```bash
mvn test -Dtest=LeverageValidationServiceTest
mvn test -Dtest=LeverageAdjustmentServiceTest
```

### 2. 测试覆盖场景
- 基础杠杆验证（最小值、最大值）
- 统一杠杆规则验证
- 灵活杠杆规则验证
- 递增杠杆规则验证
- 递减杠杆规则验证
- **全局杠杆验证（新增）**
- **杠杆调整限制验证（新增）**
- **杠杆同步调整测试（新增）**
- **固定风险等级配置验证（新增）**
- 风险限额验证

## 注意事项

### 1. 性能考虑
- 杠杆验证在订单验证流程中执行，需要保证性能
- 现有持仓查询需要优化，避免频繁数据库访问
- 考虑使用缓存机制提高验证效率
- **全局杠杆验证需要查询用户所有持仓和委托，需要特别注意性能**
- **使用固定的风险等级配置，避免动态计算带来的性能开销**

### 2. 数据一致性
- 确保用户持仓数据的准确性
- 杠杆验证需要与实际的持仓数据同步
- 考虑并发情况下的数据一致性
- **杠杆调整时需要确保所有相关数据的原子性更新**

### 3. 配置管理
- 风险限额配置支持动态更新
- 杠杆验证规则可以根据业务需求调整
- 提供配置验证机制，确保配置的有效性
- **全局杠杆设置需要持久化存储**
- **使用固定的风险等级配置，确保系统稳定可控**

### 4. 扩展性
- 支持新增杠杆验证规则
- 支持不同标的的差异化配置
- 支持用户级别的个性化配置
- **支持杠杆调整的历史记录和回滚功能**
- **使用固定的风险等级配置，确保系统稳定可控**
- **支持配置管理，但不进行动态风险调整**

## 后续优化

### 1. 功能增强
- 支持用户级别的杠杆限制
- 支持基于市场风险的动态杠杆调整
- 支持杠杆使用历史记录和分析
- **支持杠杆调整的审批流程**
- **支持杠杆调整的批量操作**
- **保持系统稳定，使用固定的风险等级配置**
- **支持配置管理，但不进行动态调整**

### 2. 性能优化
- 实现持仓数据缓存机制
- 优化验证算法，减少不必要的计算
- 支持批量订单验证
- **实现全局杠杆信息的缓存机制**
- **优化杠杆调整的并发处理**

### 3. 监控告警
- 添加杠杆验证的监控指标
- 实现异常情况的告警机制
- 提供验证失败的分析报告
- **添加杠杆调整的监控和告警**
- **实现杠杆异常的自动检测**

### 4. 用户体验
- **提供杠杆调整的实时反馈**
- **支持杠杆调整的预览功能**
- **实现杠杆调整的进度显示**
- **提供杠杆调整的详细日志** 