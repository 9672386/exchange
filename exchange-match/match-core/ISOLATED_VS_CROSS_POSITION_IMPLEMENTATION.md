# 逐仓与全仓模式实现

## 概述

逐仓和全仓是两种不同的仓位管理模式，它们在风险计算、强平逻辑和保证金管理方面有很大的差异。本系统支持这两种模式，并根据不同的模式采用相应的风险控制策略。

## 仓位模式定义

### 1. 逐仓模式 (ISOLATED)

**特点**：
- 每个交易对独立计算保证金和风险
- 单个交易对的亏损不会影响其他交易对
- 风险隔离，单个交易对的风险不会传导到其他交易对
- 适合风险控制要求较高的用户

**风险计算**：
```
逐仓风险率 = (保证金 - 未实现盈亏) / 保证金
```

**强平逻辑**：
- 只强平当前交易对的仓位
- 不影响其他交易对的仓位
- 强平时只考虑当前交易对的保证金和盈亏

### 2. 全仓模式 (CROSS)

**特点**：
- 所有交易对共享保证金
- 单个交易对的盈利可以弥补其他交易对的亏损
- 风险共享，所有交易对的风险相互影响
- 适合资金利用率要求较高的用户

**风险计算**：
```
全仓风险率 = (总保证金 - 总未实现盈亏) / 总保证金
```

**强平逻辑**：
- 强平时考虑所有交易对的仓位
- 可能同时强平多个交易对的仓位
- 按优先级排序进行强平（亏损最多的优先）

## 核心组件

### 1. 仓位模式枚举 (PositionMode)

```java
public enum PositionMode {
    ISOLATED("逐仓", "每个交易对独立管理仓位和风险"),
    CROSS("全仓", "所有交易对共享保证金和风险");
}
```

### 2. 风险计算模式枚举 (RiskCalculationMode)

```java
public enum RiskCalculationMode {
    ISOLATED("逐仓风险计算", "基于单个交易对的保证金和盈亏计算风险"),
    CROSS("全仓风险计算", "基于所有交易对的共享保证金计算风险");
}
```

### 3. 强平模式枚举 (LiquidationMode)

```java
public enum LiquidationMode {
    ISOLATED("逐仓强平", "只强平当前交易对的仓位"),
    CROSS("全仓强平", "强平时考虑所有交易对的仓位");
}
```

## 实现架构

### 1. 仓位模型增强 (Position.java)

```java
@Data
public class Position {
    // 新增字段
    private PositionMode positionMode; // 仓位模式
    
    // 逐仓风险率计算
    public BigDecimal getIsolatedRiskRatio() {
        // 只考虑当前交易对的保证金和盈亏
    }
    
    // 全仓风险率计算
    public BigDecimal getCrossRiskRatio(BigDecimal totalMargin, BigDecimal totalUnrealizedPnl) {
        // 考虑所有交易对的共享保证金和总盈亏
    }
    
    // 检查仓位模式
    public boolean isIsolatedMode() {
        return this.positionMode.isIsolated();
    }
    
    public boolean isCrossMode() {
        return this.positionMode.isCross();
    }
}
```

### 2. 全仓仓位管理器 (CrossPositionManager.java)

```java
@Service
public class CrossPositionManager {
    
    // 计算用户全仓风险率
    public BigDecimal calculateCrossRiskRatio(Long userId, BigDecimal totalMargin);
    
    // 获取用户全仓持仓列表
    public List<Position> getCrossPositions(Long userId);
    
    // 计算全仓模式下的强平优先级
    public List<Position> getCrossPositionsByLiquidationPriority(Long userId);
    
    // 执行全仓强平
    public CrossLiquidationResult executeCrossLiquidation(Long userId, BigDecimal targetRiskRatio, BigDecimal totalMargin);
}
```

### 3. 风险重计算服务增强 (RiskRecalculationService.java)

```java
@Service
public class RiskRecalculationService {
    
    public RiskRecalculationResult recalculateRisk(LiquidationRequest liquidationRequest) {
        // 根据仓位模式采用不同的风险计算方式
        if (position.isIsolatedMode()) {
            riskRatio = calculateIsolatedRiskRatio(position, liquidationRequest);
        } else {
            riskRatio = calculateCrossRiskRatio(position, liquidationRequest);
        }
    }
}
```

### 4. 风险管理服务增强 (RiskManagementService.java)

```java
@Service
public class RiskManagementService {
    
    public TieredLiquidationResult executeTieredLiquidation(Long userId, String symbol, LiquidationRequest liquidationRequest) {
        // 根据仓位模式采用不同的强平策略
        if (position.isCrossMode()) {
            return executeCrossTieredLiquidation(userId, symbol, liquidationRequest);
        } else {
            return executeIsolatedTieredLiquidation(userId, symbol, liquidationRequest);
        }
    }
}
```

## 风险计算差异

### 逐仓风险计算

```java
// 逐仓风险率计算
public BigDecimal getIsolatedRiskRatio() {
    if (this.margin.compareTo(BigDecimal.ZERO) == 0) {
        return BigDecimal.valueOf(1.0); // 100%风险率
    }
    
    // 逐仓风险率 = (保证金 - 未实现盈亏) / 保证金
    BigDecimal availableMargin = this.margin.subtract(this.unrealizedPnl);
    BigDecimal riskRatio = availableMargin.divide(this.margin, 4, BigDecimal.ROUND_HALF_UP);
    return BigDecimal.ONE.subtract(riskRatio);
}
```

### 全仓风险计算

```java
// 全仓风险率计算
public BigDecimal getCrossRiskRatio(BigDecimal totalMargin, BigDecimal totalUnrealizedPnl) {
    if (totalMargin.compareTo(BigDecimal.ZERO) <= 0) {
        return BigDecimal.valueOf(1.0); // 100%风险率
    }
    
    // 全仓风险率 = (总保证金 - 总未实现盈亏) / 总保证金
    BigDecimal availableMargin = totalMargin.subtract(totalUnrealizedPnl);
    BigDecimal riskRatio = availableMargin.divide(totalMargin, 4, BigDecimal.ROUND_HALF_UP);
    return BigDecimal.ONE.subtract(riskRatio);
}
```

## 强平逻辑差异

### 逐仓强平

1. **触发条件**：当前交易对的风险率超过阈值
2. **强平范围**：只强平当前交易对的仓位
3. **保证金计算**：只考虑当前交易对的保证金
4. **风险隔离**：不影响其他交易对

### 全仓强平

1. **触发条件**：总风险率超过阈值
2. **强平范围**：可能同时强平多个交易对的仓位
3. **保证金计算**：考虑所有交易对的共享保证金
4. **优先级排序**：按亏损程度排序，亏损最多的优先强平

## 使用示例

### 1. 设置仓位模式

```java
// 设置逐仓模式
Position position = new Position();
position.setPositionMode(PositionMode.ISOLATED);

// 设置全仓模式
Position position = new Position();
position.setPositionMode(PositionMode.CROSS);
```

### 2. 风险计算

```java
// 逐仓风险计算
if (position.isIsolatedMode()) {
    BigDecimal riskRatio = position.getIsolatedRiskRatio();
    log.info("逐仓风险率: {}", riskRatio);
}

// 全仓风险计算
if (position.isCrossMode()) {
    BigDecimal totalMargin = getUserTotalMargin(userId);
    BigDecimal totalUnrealizedPnl = getTotalUnrealizedPnl(userId);
    BigDecimal riskRatio = position.getCrossRiskRatio(totalMargin, totalUnrealizedPnl);
    log.info("全仓风险率: {}", riskRatio);
}
```

### 3. 强平执行

```java
// 执行分档平仓（自动识别仓位模式）
TieredLiquidationResult result = riskManagementService.executeTieredLiquidation(
    userId, symbol, liquidationRequest
);

if (result.getSteps().size() > 0) {
    log.info("强平完成，最终风险等级: {}", result.getFinalRiskLevel().getName());
}
```

## 配置参数

### 逐仓模式配置

```java
// 逐仓风险阈值
BigDecimal isolatedRiskThreshold = BigDecimal.valueOf(0.98); // 98%

// 逐仓强平比例
BigDecimal isolatedLiquidationRatio = BigDecimal.valueOf(0.5); // 50%
```

### 全仓模式配置

```java
// 全仓风险阈值
BigDecimal crossRiskThreshold = BigDecimal.valueOf(0.95); // 95%

// 全仓强平优先级权重
Map<String, BigDecimal> crossLiquidationWeights = new HashMap<>();
crossLiquidationWeights.put("unrealizedPnl", BigDecimal.valueOf(0.6)); // 未实现盈亏权重
crossLiquidationWeights.put("positionValue", BigDecimal.valueOf(0.4)); // 仓位价值权重
```

## 优势对比

### 逐仓模式优势

1. **风险隔离**：单个交易对的风险不会影响其他交易对
2. **精确控制**：可以精确控制每个交易对的风险
3. **适合新手**：风险控制更直观，适合新手用户
4. **资金安全**：最大损失限制在单个交易对的保证金范围内

### 全仓模式优势

1. **资金利用率高**：所有交易对共享保证金，资金利用率更高
2. **风险对冲**：不同交易对的盈亏可以相互对冲
3. **适合专业用户**：需要较强的风险控制能力
4. **灵活性强**：可以根据市场情况灵活调整仓位

## 注意事项

1. **模式切换**：用户可以在逐仓和全仓模式之间切换，但需要注意风险控制
2. **保证金管理**：全仓模式需要更复杂的保证金管理系统
3. **风险监控**：全仓模式需要监控所有交易对的风险状况
4. **强平策略**：不同模式需要不同的强平策略和优先级算法
5. **性能考虑**：全仓模式的计算复杂度更高，需要考虑性能优化

## 扩展建议

1. **混合模式**：支持部分交易对逐仓，部分交易对全仓的混合模式
2. **动态调整**：根据市场情况动态调整风险阈值和强平策略
3. **用户偏好**：根据用户的风险偏好自动推荐合适的仓位模式
4. **风险预警**：在强平前提供风险预警，给用户主动平仓的机会 