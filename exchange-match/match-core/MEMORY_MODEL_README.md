# 撮合引擎内存模型说明

## 概述

本项目实现了完整的撮合引擎内存模型，包括订单薄、仓位管理和标的管理。所有数据都存储在内存中，提供高性能的交易处理能力。

## 核心组件

### 1. 订单薄 (OrderBook)

订单薄是撮合引擎的核心数据结构，管理买卖订单队列。

#### 主要特性：
- **价格优先**: 买单按价格从高到低排序，卖单按价格从低到高排序
- **时间优先**: 同价格订单按时间顺序排列（先到先得）
- **快速撮合**: 使用ConcurrentSkipListMap实现高效的价格匹配
- **深度查询**: 支持获取指定深度的买卖盘数据
- **FIFO撮合**: 同价格订单按先进先出原则撮合

#### 核心方法：
```java
// 添加订单
orderBook.addOrder(order);

// 移除订单
orderBook.removeOrder(orderId);

// 更新订单
orderBook.updateOrder(order);

// 获取深度
List<PriceLevel> buyDepth = orderBook.getBuyDepth(20);
List<PriceLevel> sellDepth = orderBook.getSellDepth(20);

// 获取最佳价格
BigDecimal bestBid = orderBook.getBestBid();
BigDecimal bestAsk = orderBook.getBestAsk();
```

### 2. 仓位管理 (Position)

仓位管理跟踪用户的持仓情况，支持多空仓位。

#### 主要特性：
- **多空支持**: 支持多头和空头仓位
- **平均价格**: 自动计算平均开仓价格
- **盈亏计算**: 实时计算未实现和已实现盈亏
- **保证金管理**: 支持杠杆交易和保证金计算

#### 核心方法：
```java
// 开仓
position.openPosition(quantity, price);

// 平仓
position.closePosition(quantity, price);

// 更新未实现盈亏
position.updateUnrealizedPnl(currentPrice);

// 检查强平风险
boolean isLiquidatable = position.isLiquidatable();
```

### 3. 标的管理 (Symbol)

标的管理定义交易对的基本信息和交易规则。

#### 主要特性：
- **参数验证**: 验证价格和数量的有效性
- **精度控制**: 支持价格和数量的精度控制
- **手续费计算**: 自动计算交易手续费
- **杠杆支持**: 支持杠杆交易配置

#### 核心方法：
```java
// 验证价格
boolean isValidPrice = symbol.isValidPrice(price);

// 验证数量
boolean isValidQuantity = symbol.isValidQuantity(quantity);

// 格式化价格
BigDecimal formattedPrice = symbol.formatPrice(price);

// 计算手续费
BigDecimal fee = symbol.calculateFee(quantity, price);
```

### 4. 内存管理器 (MemoryManager)

内存管理器统一管理所有内存数据，提供数据访问接口。

#### 主要特性：
- **统一管理**: 管理订单薄、仓位和标的
- **线程安全**: 使用ConcurrentHashMap保证线程安全
- **统计信息**: 提供内存使用统计
- **数据清理**: 支持数据清理和重置

#### 核心方法：
```java
// 获取订单薄
OrderBook orderBook = memoryManager.getOrCreateOrderBook(symbol);

// 获取仓位
Position position = memoryManager.getPosition(userId, symbol);

// 添加标的
memoryManager.addSymbol(symbol);

// 获取统计信息
MemoryStats stats = memoryManager.getMemoryStats();
```

## 撮合引擎服务

### MatchEngineService

撮合引擎服务提供完整的交易功能。

#### 主要功能：
- **订单提交**: 验证并提交订单到撮合引擎
- **订单撤销**: 撤销用户订单
- **订单查询**: 查询订单和用户订单
- **深度查询**: 获取订单薄深度数据
- **仓位管理**: 查询和管理用户仓位
- **标的管理**: 管理交易标的

#### 使用示例：
```java
@Autowired
private MatchEngineService matchEngineService;

// 提交订单
Order order = createOrder("ORDER_001", 12345L, "BTC/USDT", OrderSide.BUY, 
        new BigDecimal("50000"), new BigDecimal("1.0"));
MatchResult result = matchEngineService.submitOrder(order);

// 撤销订单
boolean cancelled = matchEngineService.cancelOrder("ORDER_001", 12345L);

// 查询订单薄
OrderBookSnapshot snapshot = matchEngineService.getOrderBookSnapshot("BTC/USDT");

// 查询仓位
Position position = matchEngineService.getPosition(12345L, "BTC/USDT");
```

## 数据模型

### 订单模型 (Order)

```java
@Data
public class Order {
    private String orderId;           // 订单ID
    private Long userId;              // 用户ID
    private String symbol;            // 交易对
    private OrderSide side;           // 订单方向
    private OrderType type;           // 订单类型
    private BigDecimal price;         // 价格
    private BigDecimal quantity;      // 数量
    private BigDecimal filledQuantity;    // 已成交数量
    private BigDecimal remainingQuantity; // 剩余数量
    private OrderStatus status;       // 订单状态
    private LocalDateTime createTime; // 创建时间
    private LocalDateTime updateTime; // 更新时间
}
```

### 仓位模型 (Position)

```java
@Data
public class Position {
    private Long userId;              // 用户ID
    private String symbol;            // 交易对
    private PositionSide side;        // 仓位方向
    private BigDecimal quantity;      // 持仓数量
    private BigDecimal averagePrice;  // 平均价格
    private BigDecimal unrealizedPnl; // 未实现盈亏
    private BigDecimal realizedPnl;   // 已实现盈亏
    private BigDecimal margin;        // 保证金
    private BigDecimal leverage;      // 杠杆倍数
    private PositionStatus status;    // 仓位状态
}
```

### 标的模型 (Symbol)

```java
@Data
public class Symbol {
    private String symbol;            // 交易对
    private String baseCurrency;      // 基础货币
    private String quoteCurrency;     // 计价货币
    private BigDecimal minQuantity;   // 最小交易数量
    private BigDecimal maxQuantity;   // 最大交易数量
    private Integer quantityPrecision; // 数量精度
    private Integer pricePrecision;   // 价格精度
    private BigDecimal tickSize;      // 最小价格变动单位
    private BigDecimal feeRate;       // 手续费率
    private Boolean supportsLeverage; // 是否支持杠杆
    private BigDecimal maxLeverage;   // 最大杠杆倍数
    private Boolean supportsShort;    // 是否支持做空
    private SymbolStatus status;      // 交易状态
}
```

## 撮合算法

### 价格优先时间优先 (Price-Time Priority)

1. **价格优先**: 买单按价格从高到低排序，卖单按价格从低到高排序
2. **时间优先**: 同价格订单按提交时间排序（先到先得）
3. **FIFO撮合**: 同价格订单按先进先出原则进行撮合
4. **连续撮合**: 新订单进入时，立即与对手方订单进行撮合

### 撮合流程

```java
// 买单撮合流程
private List<Trade> matchBuyOrder(Order buyOrder, OrderBook orderBook, Symbol symbol) {
    List<Trade> trades = new ArrayList<>();
    
    // 遍历卖单队列（价格从低到高）
    for (Map.Entry<BigDecimal, List<Order>> entry : orderBook.getSellOrders().entrySet()) {
        BigDecimal sellPrice = entry.getKey();
        List<Order> sellOrders = entry.getValue();
        
        // 检查价格是否匹配
        if (buyOrder.getPrice().compareTo(sellPrice) < 0) {
            break; // 价格不匹配，停止撮合
        }
        
        // 撮合该价格层的订单（按时间顺序，先到先得）
        for (Order sellOrder : sellOrders) {
            // 计算成交数量
            BigDecimal matchQuantity = buyOrder.getRemainingQuantity()
                    .min(sellOrder.getRemainingQuantity());
            
            // 创建成交记录
            Trade trade = createTrade(buyOrder, sellOrder, sellPrice, matchQuantity);
            trades.add(trade);
            
            // 更新订单状态
            buyOrder.updateFilledQuantity(matchQuantity);
            sellOrder.updateFilledQuantity(matchQuantity);
            
            // 更新仓位
            updatePositions(buyOrder, sellOrder, matchQuantity, sellPrice);
        }
    }
    
    return trades;
}
```

## 性能优化

### 1. 数据结构优化

- **ConcurrentSkipListMap**: 用于价格队列，提供O(log n)的查找和插入性能
- **ConcurrentHashMap**: 用于订单和仓位映射，提供O(1)的查找性能
- **LinkedList**: 用于同价格订单列表，提供O(1)的插入和删除性能，保证时间优先顺序

### 2. 内存管理

- **对象复用**: 减少对象创建和垃圾回收
- **内存池**: 对于频繁创建的对象使用对象池
- **数据压缩**: 对于大量数据使用压缩存储

### 3. 并发控制

- **无锁设计**: 尽可能使用无锁数据结构
- **分段锁**: 对不同标的使用不同的锁
- **读写分离**: 读操作和写操作分离

## 监控和统计

### 内存统计

```java
MemoryStats stats = matchEngineService.getMemoryStats();
System.out.println("订单薄数量: " + stats.getOrderBookCount());
System.out.println("仓位数量: " + stats.getPositionCount());
System.out.println("标的数量: " + stats.getSymbolCount());
System.out.println("总订单数量: " + stats.getTotalOrderCount());
```

### 性能监控

- **订单处理延迟**: 监控订单从提交到处理的延迟
- **撮合成功率**: 监控订单撮合的成功率
- **内存使用**: 监控内存使用情况
- **GC情况**: 监控垃圾回收情况

## 扩展功能

### 1. 支持更多订单类型

- **市价单**: 以当前市场价格成交
- **止损单**: 达到止损价格时自动成交
- **限价单**: 以指定价格或更好价格成交

### 2. 支持更多撮合算法

- **FIFO**: 先进先出撮合
- **Pro-Rata**: 按比例撮合
- **混合算法**: 结合多种撮合算法

### 3. 支持更多功能

- **冰山订单**: 隐藏真实订单数量
- **条件订单**: 满足条件时自动触发
- **算法交易**: 支持算法交易接口

## 测试

### 单元测试

运行内存模型测试：

```bash
mvn test -Dtest=MemoryModelTest
```

运行时间优先测试：

```bash
mvn test -Dtest=OrderBookTimePriorityTest
```

### 性能测试

```java
@Test
public void testPerformance() {
    long startTime = System.currentTimeMillis();
    
    // 提交大量订单
    for (int i = 0; i < 10000; i++) {
        Order order = createOrder("PERF_" + i, 12345L, "BTC/USDT", 
                OrderSide.BUY, new BigDecimal("50000"), new BigDecimal("1.0"));
        matchEngineService.submitOrder(order);
    }
    
    long endTime = System.currentTimeMillis();
    System.out.println("处理10000个订单耗时: " + (endTime - startTime) + "ms");
}
```

## 注意事项

1. **内存管理**: 注意内存使用，避免内存泄漏
2. **并发安全**: 确保所有操作都是线程安全的
3. **数据一致性**: 确保订单、仓位和成交记录的一致性
4. **性能监控**: 持续监控系统性能，及时优化
5. **错误处理**: 妥善处理各种异常情况 