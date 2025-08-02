# 订单匹配器重构说明

## 重构目标

将不同订单类型的撮合逻辑进行重构，提取通用的撮合算法，减少代码重复，提高可维护性。

## 重构前的问题

1. **代码重复**：`LimitOrderMatcher`、`IocOrderMatcher`、`FokOrderMatcher` 三个类中有大量重复的撮合逻辑
2. **维护困难**：修改撮合算法需要在多个地方同时修改
3. **扩展性差**：添加新的订单类型需要重复实现相同的撮合逻辑

## 重构后的架构

### 1. 抽象基类 `AbstractOrderMatcher`

提取了所有订单类型通用的撮合逻辑：

- **核心撮合算法**：`matchBuyOrder()` 和 `matchSellOrder()`
- **成交记录创建**：`createTrade()`
- **可成交数量计算**：`calculateAvailableQuantity()`
- **模板方法模式**：`matchOrder()` 定义了撮合的通用流程

### 2. 订单类型特定逻辑

每种订单类型只需要实现特定的逻辑：

#### 限价单 (`LimitOrderMatcher`)
- 继承自 `AbstractOrderMatcher`
- 使用默认的 `postMatch()` 方法，将剩余订单添加到订单薄

#### IOC订单 (`IocOrderMatcher`)
- 重写 `postMatch()` 方法
- 不将订单添加到订单薄，无论是否完全成交

#### FOK订单 (`FokOrderMatcher`)
- 重写 `preMatch()` 方法：预先检查是否可以全部成交
- 重写 `postMatch()` 方法：不将订单添加到订单薄

## 重构优势

### 1. 代码复用
- 消除了约 80% 的重复代码
- 核心撮合逻辑只需要维护一份

### 2. 易于扩展
- 添加新的订单类型只需要继承 `AbstractOrderMatcher`
- 只需要实现特定的预处理和后处理逻辑

### 3. 易于维护
- 修改撮合算法只需要修改基类
- 各订单类型的特定逻辑清晰分离

### 4. 符合设计模式
- 使用模板方法模式定义撮合流程
- 使用策略模式处理不同订单类型

## 使用示例

### 添加新的订单类型

```java
@Component
public class MarketOrderMatcher extends AbstractOrderMatcher {
    
    @Override
    protected boolean preMatch(Order order, OrderBook orderBook, Symbol symbol) {
        // 市价单的预处理逻辑
        return true;
    }
    
    @Override
    protected void postMatch(Order order, OrderBook orderBook, Symbol symbol, List<Trade> trades) {
        // 市价单的后处理逻辑
        // 市价单不留在订单薄中
    }
    
    @Override
    public OrderType getSupportedOrderType() {
        return OrderType.MARKET;
    }
}
```

## 核心撮合算法

### 价格优先、时间优先原则

1. **价格优先**：买单按价格从高到低，卖单按价格从低到高
2. **时间优先**：同价格层内按订单提交时间先后顺序
3. **部分成交**：支持订单部分成交，剩余部分继续等待

### 撮合流程

1. **预处理**：检查订单是否满足撮合条件
2. **价格匹配**：遍历对手方订单队列，找到价格匹配的订单
3. **数量匹配**：计算可成交数量，创建成交记录
4. **更新状态**：更新订单和订单薄状态
5. **后处理**：根据订单类型决定是否留在订单薄

## 性能优化

- 使用 `TreeMap` 保证价格层的有序性
- 使用 `LinkedList` 保证同价格层内的时间顺序
- 避免不必要的对象创建和复制

## 测试建议

1. **单元测试**：为每个订单类型编写独立的测试用例
2. **集成测试**：测试不同订单类型之间的交互
3. **性能测试**：验证重构后的性能表现
4. **边界测试**：测试极端情况下的撮合行为 