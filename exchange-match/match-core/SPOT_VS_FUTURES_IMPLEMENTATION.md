# 现货与合约交易实现

## 概述

交易所支持两种主要的交易类型：现货交易和合约交易。它们在交易逻辑、仓位管理、风险控制等方面有本质区别。

## 交易类型定义

### 1. TradingType 枚举

```java
public enum TradingType {
    SPOT("现货"),           // 现货交易
    FUTURES("合约"),        // 定期合约
    PERPETUAL("永续合约");  // 永续合约
}
```

### 2. 主要区别

| 特性 | 现货交易 | 合约交易 |
|------|----------|----------|
| 仓位管理 | 无仓位概念 | 支持多空仓位 |
| 杠杆交易 | 不支持 | 支持杠杆 |
| 做空 | 不支持 | 支持做空 |
| 开平仓 | 无开平仓概念 | 支持开平仓 |
| 资金费率 | 无 | 永续合约有资金费率 |
| 强平机制 | 无 | 有强平机制 |

## Symbol 模型扩展

### 1. 添加交易类型字段

```java
@Data
public class Symbol {
    /**
     * 交易类型
     */
    private TradingType tradingType;
    
    // ... 其他字段
}
```

### 2. 交易类型检查方法

```java
public class Symbol {
    /**
     * 检查是否为现货交易
     */
    public boolean isSpot() {
        return tradingType.isSpot();
    }
    
    /**
     * 检查是否为合约交易
     */
    public boolean isFutures() {
        return tradingType.isFutures();
    }
    
    /**
     * 检查是否支持仓位管理
     */
    public boolean supportsPosition() {
        return tradingType.supportsPosition();
    }
    
    /**
     * 检查是否支持杠杆交易
     */
    public boolean isLeverageSupported() {
        return tradingType.supportsLeverage() && Boolean.TRUE.equals(supportsLeverage);
    }
    
    /**
     * 检查是否支持做空
     */
    public boolean isShortSupported() {
        return tradingType.supportsShort() && Boolean.TRUE.equals(supportsShort);
    }
}
```

## 撮合逻辑差异

### 1. 现货交易逻辑

```java
// 现货交易：只处理资产转移，无仓位概念
if (symbol.isSpot()) {
    // 不设置开平仓动作
    order.setPositionAction(null);
    
    // 不更新仓位
    // 只更新资产余额
    updateAssetBalance(trade);
}
```

### 2. 合约交易逻辑

```java
// 合约交易：处理开平仓和仓位管理
if (symbol.supportsPosition()) {
    // 自动判断开平仓动作
    if (order.getPositionAction() == null) {
        Position currentPosition = memoryManager.getPosition(order.getUserId(), order.getSymbol());
        PositionSide currentPositionSide = currentPosition != null ? currentPosition.getSide() : null;
        order.setPositionAction(PositionAction.determineAction(order.getSide(), currentPositionSide));
    }
    
    // 更新仓位
    updatePositionsFromTrade(trade);
}
```

## 订单处理差异

### 1. 现货订单

```java
// 现货订单示例
Order spotOrder = new Order();
spotOrder.setSymbol("BTC/USDT");
spotOrder.setSide(OrderSide.BUY);
spotOrder.setType(OrderType.LIMIT);
spotOrder.setPrice(new BigDecimal("50000"));
spotOrder.setQuantity(new BigDecimal("1.0"));
// 不设置positionAction，系统会自动设为null

MatchResponse response = matchEngineService.submitOrder(spotOrder);
// 响应中不包含仓位变化信息
```

### 2. 合约订单

```java
// 合约订单示例
Order futuresOrder = new Order();
futuresOrder.setSymbol("BTC/USDT-PERP");
futuresOrder.setSide(OrderSide.BUY);
futuresOrder.setType(OrderType.LIMIT);
futuresOrder.setPositionAction(PositionAction.OPEN); // 明确指定开仓
futuresOrder.setPrice(new BigDecimal("50000"));
futuresOrder.setQuantity(new BigDecimal("1.0"));

MatchResponse response = matchEngineService.submitOrder(futuresOrder);
// 响应中包含仓位变化信息
```

## 资产管理差异

### 1. 现货资产管理

```java
/**
 * 现货交易资产更新
 */
private void updateSpotAssetBalance(Trade trade) {
    // 买方：减少计价货币，增加基础货币
    updateUserBalance(trade.getBuyUserId(), trade.getQuoteCurrency(), 
                     trade.getAmount().negate()); // 减少USDT
    updateUserBalance(trade.getBuyUserId(), trade.getBaseCurrency(), 
                     trade.getQuantity()); // 增加BTC
    
    // 卖方：增加计价货币，减少基础货币
    updateUserBalance(trade.getSellUserId(), trade.getQuoteCurrency(), 
                     trade.getAmount()); // 增加USDT
    updateUserBalance(trade.getSellUserId(), trade.getBaseCurrency(), 
                     trade.getQuantity().negate()); // 减少BTC
}
```

### 2. 合约仓位管理

```java
/**
 * 合约交易仓位更新
 */
private void updateFuturesPosition(Trade trade) {
    // 更新买方仓位
    Position buyPosition = memoryManager.getOrCreatePosition(trade.getBuyUserId(), trade.getSymbol());
    if (trade.getBuyPositionAction().isOpen()) {
        buyPosition.openPosition(trade.getQuantity(), trade.getPrice());
    } else {
        buyPosition.closePosition(trade.getQuantity(), trade.getPrice());
    }
    memoryManager.updatePosition(buyPosition);
    
    // 更新卖方仓位
    Position sellPosition = memoryManager.getOrCreatePosition(trade.getSellUserId(), trade.getSymbol());
    if (trade.getSellPositionAction().isOpen()) {
        sellPosition.openPosition(trade.getQuantity(), trade.getPrice());
    } else {
        sellPosition.closePosition(trade.getQuantity(), trade.getPrice());
    }
    memoryManager.updatePosition(sellPosition);
}
```

## 响应信息差异

### 1. 现货交易响应

```java
MatchResponse spotResponse = new MatchResponse();
spotResponse.setOrderId("SPOT_001");
spotResponse.setStatus(MatchStatus.SUCCESS);
spotResponse.setTrades(trades);
spotResponse.setMatchQuantity(filledQuantity);
spotResponse.setMatchAmount(totalAmount);
// 不包含positionChange信息
```

### 2. 合约交易响应

```java
MatchResponse futuresResponse = new MatchResponse();
futuresResponse.setOrderId("FUTURES_001");
futuresResponse.setStatus(MatchStatus.SUCCESS);
futuresResponse.setTrades(trades);
futuresResponse.setMatchQuantity(filledQuantity);
futuresResponse.setMatchAmount(totalAmount);
futuresResponse.setPositionChange(positionChange); // 包含仓位变化信息
```

## 风险控制差异

### 1. 现货风险控制

```java
/**
 * 现货交易风险检查
 */
private boolean validateSpotOrder(Order order, Symbol symbol) {
    // 检查余额是否充足
    BigDecimal requiredBalance = order.getPrice().multiply(order.getQuantity());
    BigDecimal userBalance = getUserBalance(order.getUserId(), symbol.getQuoteCurrency());
    
    if (order.getSide() == OrderSide.BUY) {
        return userBalance.compareTo(requiredBalance) >= 0;
    } else {
        BigDecimal userAsset = getUserBalance(order.getUserId(), symbol.getBaseCurrency());
        return userAsset.compareTo(order.getQuantity()) >= 0;
    }
}
```

### 2. 合约风险控制

```java
/**
 * 合约交易风险检查
 */
private boolean validateFuturesOrder(Order order, Symbol symbol) {
    // 检查保证金是否充足
    BigDecimal requiredMargin = calculateRequiredMargin(order, symbol);
    BigDecimal userMargin = getUserMargin(order.getUserId(), symbol.getSymbol());
    
    if (order.getPositionAction().isClose()) {
        // 平仓检查：仓位是否充足
        Position position = memoryManager.getPosition(order.getUserId(), order.getSymbol());
        return position != null && position.getQuantity().compareTo(order.getQuantity()) >= 0;
    } else {
        // 开仓检查：保证金是否充足
        return userMargin.compareTo(requiredMargin) >= 0;
    }
}
```

## 使用示例

### 1. 创建现货标的

```java
Symbol spotSymbol = new Symbol();
spotSymbol.setSymbol("BTC/USDT");
spotSymbol.setTradingType(TradingType.SPOT);
spotSymbol.setBaseCurrency("BTC");
spotSymbol.setQuoteCurrency("USDT");
spotSymbol.setSupportsLeverage(false);
spotSymbol.setSupportsShort(false);
```

### 2. 创建合约标的

```java
Symbol futuresSymbol = new Symbol();
futuresSymbol.setSymbol("BTC/USDT-PERP");
futuresSymbol.setTradingType(TradingType.PERPETUAL);
futuresSymbol.setBaseCurrency("BTC");
futuresSymbol.setQuoteCurrency("USDT");
futuresSymbol.setSupportsLeverage(true);
futuresSymbol.setSupportsShort(true);
futuresSymbol.setMaxLeverage(new BigDecimal("100"));
```

### 3. 现货交易流程

```java
// 1. 提交现货订单
Order spotOrder = new Order();
spotOrder.setSymbol("BTC/USDT");
spotOrder.setSide(OrderSide.BUY);
spotOrder.setType(OrderType.LIMIT);
spotOrder.setPrice(new BigDecimal("50000"));
spotOrder.setQuantity(new BigDecimal("1.0"));

MatchResponse response = matchEngineService.submitOrder(spotOrder);

// 2. 检查响应
if (response.isSuccess()) {
    System.out.println("现货交易成功");
    System.out.println("成交数量: " + response.getMatchQuantity());
    System.out.println("成交金额: " + response.getMatchAmount());
    // 注意：现货交易响应中不包含positionChange
}
```

### 4. 合约交易流程

```java
// 1. 提交合约订单
Order futuresOrder = new Order();
futuresOrder.setSymbol("BTC/USDT-PERP");
futuresOrder.setSide(OrderSide.BUY);
futuresOrder.setType(OrderType.LIMIT);
futuresOrder.setPositionAction(PositionAction.OPEN);
futuresOrder.setPrice(new BigDecimal("50000"));
futuresOrder.setQuantity(new BigDecimal("1.0"));

MatchResponse response = matchEngineService.submitOrder(futuresOrder);

// 2. 检查响应
if (response.isSuccess()) {
    System.out.println("合约交易成功");
    System.out.println("成交数量: " + response.getMatchQuantity());
    System.out.println("成交金额: " + response.getMatchAmount());
    
    // 合约交易包含仓位变化信息
    PositionChange posChange = response.getPositionChange();
    if (posChange != null) {
        System.out.println("仓位变化: " + posChange.getQuantityChange());
        System.out.println("新仓位数量: " + posChange.getNewQuantity());
        System.out.println("开平仓动作: " + posChange.getPositionAction());
    }
}
```

## 配置管理

### 1. 现货配置

```java
// 现货交易配置
SymbolConfig spotConfig = new SymbolConfig();
spotConfig.setTradingType(TradingType.SPOT);
spotConfig.setSupportsLeverage(false);
spotConfig.setSupportsShort(false);
spotConfig.setMaxLeverage(BigDecimal.ONE);
spotConfig.setPositionManagement(false);
```

### 2. 合约配置

```java
// 合约交易配置
SymbolConfig futuresConfig = new SymbolConfig();
futuresConfig.setTradingType(TradingType.PERPETUAL);
futuresConfig.setSupportsLeverage(true);
futuresConfig.setSupportsShort(true);
futuresConfig.setMaxLeverage(new BigDecimal("100"));
futuresConfig.setPositionManagement(true);
futuresConfig.setFundingRateEnabled(true);
futuresConfig.setLiquidationEnabled(true);
```

## 总结

现货和合约交易的主要区别：

1. **交易逻辑**：
   - 现货：资产转移，无仓位概念
   - 合约：开平仓，支持多空仓位

2. **风险控制**：
   - 现货：余额检查
   - 合约：保证金检查、强平机制

3. **功能支持**：
   - 现货：不支持杠杆、做空
   - 合约：支持杠杆、做空、资金费率

4. **响应信息**：
   - 现货：不包含仓位变化
   - 合约：包含详细的仓位变化信息

这种设计确保了系统能够正确处理不同类型的交易，同时保持代码的清晰性和可维护性。 