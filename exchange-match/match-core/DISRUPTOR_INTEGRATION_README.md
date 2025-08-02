# Disruptor整合说明

## 概述

本项目在match-core模块中集成了Disruptor高性能事件处理框架，用于处理撮合引擎的各种事件。通过Disruptor的RingBuffer机制，实现了高并发、低延迟的事件处理。

## 架构设计

### 核心组件

1. **MatchEvent**: Disruptor事件对象，用于在RingBuffer中传递事件数据
2. **EventHandler**: 事件处理器接口，定义事件处理的标准方法
3. **MatchEventHandler**: Disruptor事件处理器，负责分发事件到具体的处理器
4. **EventPublishService**: 事件发布服务，用于向Disruptor发布事件
5. **MatchEventService**: 业务服务接口，提供业务层面的API
6. **MatchEventController**: REST控制器，提供HTTP接口

### 事件类型

支持以下事件类型：

- `NEW_ORDER`: 新订单事件
- `CANAL`: 撤单事件
- `CLEAR`: 清理事件
- `SNAPSHOT`: 快照事件
- `STOP`: 停止事件
- `QUERY_ORDER`: 查询订单事件
- `QUERY_POSITION`: 查询持仓事件

## 使用方法

### 1. 通过REST API调用

```bash
# 提交新订单
POST /api/match/event/new-order
{
    "orderId": "ORDER_001",
    "userId": 12345
}

# 撤销订单
POST /api/match/event/cancel-order
{
    "orderId": "ORDER_001",
    "userId": 12345
}

# 清理订单
POST /api/match/event/clear-orders
{
    "symbol": "BTC/USDT"
}

# 生成快照
POST /api/match/event/snapshot
{}

# 停止引擎
POST /api/match/event/stop
{}

# 查询订单
POST /api/match/event/query-order
{
    "symbol": "BTC/USDT",
    "userId": 12345,
    "orderType": 1
}

# 查询持仓
POST /api/match/event/query-position
{
    "symbol": "BTC/USDT",
    "userId": 12345
}
```

### 2. 通过服务接口调用

```java
@Autowired
private MatchEventService matchEventService;

// 提交新订单
EventNewOrderReq newOrderReq = new EventNewOrderReq();
newOrderReq.setOrderId("ORDER_001");
newOrderReq.setUserId(12345L);
String result = matchEventService.submitNewOrder(newOrderReq);

// 撤销订单
EventCanalReq canalReq = new EventCanalReq();
canalReq.setOrderId("ORDER_001");
canalReq.setUserId(12345L);
String result = matchEventService.cancelOrder(canalReq);
```

### 3. 直接发布事件

```java
@Autowired
private EventPublishService eventPublishService;

// 发布新订单事件
EventNewOrderReq newOrderReq = new EventNewOrderReq();
newOrderReq.setOrderId("ORDER_001");
newOrderReq.setUserId(12345L);
eventPublishService.publishNewOrderEvent(newOrderReq);
```

## 配置说明

### Disruptor配置

在`DisruptorConfig`类中配置了以下参数：

- **RingBuffer大小**: 1024（必须是2的幂）
- **线程工厂**: 使用默认线程工厂
- **事件处理器**: 使用`MatchEventHandler`

### 自定义配置

可以通过修改`DisruptorConfig`类来自定义配置：

```java
@Configuration
public class DisruptorConfig {
    
    private static final int BUFFER_SIZE = 2048; // 增加缓冲区大小
    
    @Bean
    public Disruptor<MatchEvent> disruptor() {
        Disruptor<MatchEvent> disruptor = new Disruptor<>(
                new MatchEventFactory(),
                BUFFER_SIZE,
                Executors.newFixedThreadPool(4) // 使用固定线程池
        );
        
        // 设置事件处理器
        disruptor.handleEventsWith(matchEventHandler);
        
        // 启动Disruptor
        disruptor.start();
        
        return disruptor;
    }
}
```

## 事件处理器扩展

### 添加新的事件处理器

1. 实现`EventHandler`接口：

```java
@Component
public class CustomEventHandler implements EventHandler {
    
    @Override
    public void handle(MatchEvent event) {
        // 处理事件逻辑
    }
    
    @Override
    public EventType getSupportedEventType() {
        return EventType.CUSTOM_EVENT;
    }
}
```

2. 在`EventPublishService`中添加发布方法：

```java
public void publishCustomEvent(CustomEventReq customReq) {
    publishEvent(EventType.CUSTOM_EVENT, event -> {
        event.setCustomReq(customReq);
    });
}
```

3. 在`MatchEventService`中添加业务方法：

```java
public String processCustomEvent(CustomEventReq customReq) {
    try {
        eventPublishService.publishCustomEvent(customReq);
        return "自定义事件处理成功";
    } catch (Exception e) {
        throw new RuntimeException("自定义事件处理失败", e);
    }
}
```

## 性能优化

### 1. 调整RingBuffer大小

根据业务需求调整RingBuffer大小：

```java
private static final int BUFFER_SIZE = 4096; // 增加缓冲区大小
```

### 2. 使用多线程处理

配置多个事件处理器并行处理：

```java
disruptor.handleEventsWith(handler1, handler2, handler3);
```

### 3. 批量处理

实现批量事件处理：

```java
@Override
public void onEvent(MatchEvent event, long sequence, boolean endOfBatch) {
    // 批量处理逻辑
    if (endOfBatch) {
        // 批量提交
    }
}
```

## 监控和日志

### 日志配置

在`application.yml`中配置日志级别：

```yaml
logging:
  level:
    com.exchange.match.core.event: DEBUG
    com.exchange.match.core.event.disruptor: DEBUG
```

### 性能监控

可以通过以下方式监控性能：

1. **事件处理时间**: 在事件处理器中记录处理时间
2. **RingBuffer使用率**: 监控RingBuffer的序列号
3. **事件处理成功率**: 统计成功和失败的事件数量

## 故障处理

### 1. 事件处理异常

当事件处理出现异常时，异常信息会被记录在`MatchEvent`对象中：

```java
try {
    // 处理事件
} catch (Exception e) {
    event.setException(e);
    log.error("事件处理失败", e);
}
```

### 2. RingBuffer满

当RingBuffer满时，发布事件会阻塞。可以通过以下方式处理：

```java
// 使用try-with-resources确保序列号被正确发布
long sequence = ringBuffer.next();
try {
    MatchEvent event = ringBuffer.get(sequence);
    // 设置事件数据
} finally {
    ringBuffer.publish(sequence);
}
```

### 3. 优雅关闭

在应用关闭时，需要优雅地关闭Disruptor：

```java
@PreDestroy
public void shutdown() {
    if (disruptor != null) {
        disruptor.shutdown();
    }
}
```

## 测试

### 单元测试

运行测试类验证功能：

```bash
mvn test -Dtest=DisruptorIntegrationTest
```

### 性能测试

可以使用JMeter或其他工具进行性能测试，验证在高并发场景下的表现。

## 注意事项

1. **线程安全**: 事件处理器必须是线程安全的
2. **内存管理**: 及时清理事件对象，避免内存泄漏
3. **异常处理**: 妥善处理事件处理过程中的异常
4. **监控告警**: 设置适当的监控和告警机制
5. **配置调优**: 根据实际业务需求调整配置参数 