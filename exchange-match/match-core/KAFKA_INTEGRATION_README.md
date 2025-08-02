# Kafka集成功能说明

## 概述

本撮合引擎已集成Kafka批量推送功能，支持将撮合结果和快照数据批量推送到Kafka，满足高性能、低延迟的数据传输需求。

## 功能特性

### 1. 批量推送机制
- **批量大小**: 1000条消息
- **超时时间**: 50毫秒
- **推送条件**: 满足1000条或50ms任一条件即推送
- **异步处理**: 不阻塞主撮合流程

### 2. 支持的数据类型
- **撮合结果**: 订单成交、撤单、强平等结果
- **快照数据**: 完整的撮合引擎状态快照
- **自动分类**: 根据消息内容自动选择Kafka主题

### 3. 监控功能
- 实时查看待推送消息数量
- 累计推送消息统计
- 强制推送接口

### 4. 完整快照功能
- **订单薄快照**: 包含所有交易对的订单薄数据
- **持仓快照**: 包含所有用户的持仓信息
- **订单快照**: 包含所有活跃订单数据
- **仓位锁定快照**: 包含所有仓位锁定状态
- **交易对配置快照**: 包含所有交易对配置信息
- **内存统计快照**: 包含完整的内存使用统计

### 5. 快照恢复功能
- **数据完整性验证**: 验证快照数据的完整性
- **完整状态恢复**: 从快照恢复撮合引擎的完整状态
- **服务可用性保证**: 确保服务重启后数据不丢失

### 6. Kafka Offset管理
- **Offset跟踪**: 实时跟踪各主题的offset状态
- **一致性检查**: 检查current offset和committed offset的一致性
- **故障恢复**: 支持从快照恢复offset状态
- **监控告警**: 监控offset不一致和消息积压情况

### 7. 从快照Offset开始消费
- **数据连续性**: 从快照的committed offset开始消费，确保数据连续性
- **自动恢复**: 快照恢复后自动启动消费者服务
- **消费控制**: 支持暂停、恢复消费操作
- **状态监控**: 实时监控消费状态和offset进度

### 8. 异步快照生成
- **非阻塞处理**: 快照生成在独立线程池中处理，不阻塞撮合流程
- **任务队列**: 使用队列管理快照任务，支持背压控制
- **异步推送**: 快照生成完成后异步推送到Kafka
- **性能优化**: 避免快照生成影响撮合性能

## 配置说明

### Kafka配置 (application-kafka.yml)
```yaml
kafka:
  bootstrap-servers: localhost:9092
  producer:
    batch-size: 16384
    linger-ms: 5
    buffer-memory: 33554432
  topic:
    match-results: match-results
    snapshots: snapshots
  batch:
    size: 1000
    timeout-ms: 50
```

### 配置参数说明
- `bootstrap-servers`: Kafka服务器地址
- `batch-size`: Kafka生产者批量大小
- `linger-ms`: 消息发送延迟时间
- `buffer-memory`: 生产者缓冲区大小
- `group-id`: 消费者组ID
- `auto-offset-reset`: 消费者offset重置策略
- `enable-auto-commit`: 是否启用自动提交offset
- `max-poll-records`: 单次拉取的最大消息数量
- `topic.match-results`: 撮合结果主题
- `topic.snapshots`: 快照数据主题
- `batch.size`: 批量推送大小
- `batch.timeout-ms`: 批量推送超时时间

## API接口

### 1. 快照生成
```http
POST /api/match/event/snapshot
Content-Type: application/json

{
  "symbol": "BTCUSDT"  // 可选，为null时生成所有交易对快照
}
```

### 2. 快照恢复
```http
POST /api/snapshot/recovery/recover
Content-Type: application/json

{
  "snapshotId": "uuid",
  "timestamp": 1640995200000,
  "orderBookSnapshots": {...},
  "positionSnapshots": {...},
  "orderSnapshots": {...},
  "positionLockSnapshots": {...},
  "symbolSnapshots": {...},
  "memoryStats": {...}
}
```

### 3. 快照验证
```http
POST /api/snapshot/recovery/validate
Content-Type: application/json

{
  "snapshotId": "uuid",
  // ... 快照数据
}
```

### 4. Kafka监控
```http
GET /api/kafka/monitor/status
```

响应示例:
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "pendingMessageCount": 150,
    "totalPushedCount": 5000,
    "timestamp": 1640995200000
  }
}
```

### 5. 强制推送
```http
POST /api/kafka/monitor/force-flush
```

### 6. Offset状态监控
```http
GET /api/kafka/monitor/offset/status
```

响应示例:
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "match-results": {
      "topic": "match-results",
      "currentOffset": 1500,
      "committedOffset": 1400,
      "pendingOffset": 100,
      "timestamp": 1640995200000
    },
    "snapshots": {
      "topic": "snapshots",
      "currentOffset": 50,
      "committedOffset": 50,
      "pendingOffset": 0,
      "timestamp": 1640995200000
    }
  }
}
```

### 7. Offset一致性检查
```http
GET /api/kafka/monitor/offset/consistency
```

### 8. 待确认消息数量
```http
GET /api/kafka/monitor/offset/pending
```

### 9. 重置Offset
```http
POST /api/kafka/monitor/offset/reset/{topic}
```

### 10. 消费者Offset监控
```http
GET /api/kafka/consumer/offsets
```

响应示例:
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "match-results": 1500,
    "snapshots": 50
  }
}
```

### 11. 消费者状态检查
```http
GET /api/kafka/consumer/status
```

### 12. 暂停消费
```http
POST /api/kafka/consumer/pause
```

### 13. 恢复消费
```http
POST /api/kafka/consumer/resume
```

## 快照数据结构

### 撮合引擎快照 (MatchEngineSnapshot)
```json
{
  "snapshotId": "uuid",
  "timestamp": 1640995200000,
  "snapshotTime": "2024-01-01T12:00:00",
  "engineStatus": "RUNNING",
  "orderBookSnapshots": {
    "BTCUSDT": {
      "symbol": "BTCUSDT",
      "lastPrice": 50000.00,
      "highPrice": 51000.00,
      "lowPrice": 49000.00,
      "volume24h": 1000.5,
      "buyDepth": [...],
      "sellDepth": [...]
    }
  },
  "positionSnapshots": {
    "123_BTCUSDT": {
      "userId": 123,
      "symbol": "BTCUSDT",
      "baseCurrency": "BTC",
      "quoteCurrency": "USDT",
      "side": "LONG",
      "positionMode": "ISOLATED",
      "quantity": 1.5,
      "averagePrice": 50000.00,
      "unrealizedPnl": 1000.00,
      "realizedPnl": 500.00,
      "margin": 10000.00,
      "riskRatio": 0.8,
      "leverage": 10.0,
      "liquidationPrice": 45000.00,
      "status": "ACTIVE",
      "lockedQuantity": 0.5,
      "availableQuantity": 1.0,
      "lockStatus": "PARTIALLY_LOCKED",
      "createTime": "2024-01-01T10:00:00",
      "updateTime": "2024-01-01T12:00:00"
    }
  },
  "orderSnapshots": {
    "order_123": {
      "orderId": "order_123",
      "userId": 123,
      "symbol": "BTCUSDT",
      "side": "BUY",
      "type": "LIMIT",
      "positionAction": "OPEN",
      "price": 50000.00,
      "quantity": 1.0,
      "remainingQuantity": 0.5,
      "filledQuantity": 0.5,
      "status": "PARTIALLY_FILLED",
      "clientOrderId": "client_123",
      "remark": "用户下单",
      "createTime": "2024-01-01T11:00:00",
      "updateTime": "2024-01-01T11:30:00"
    }
  },
  "positionLockSnapshots": {
    "123_BTCUSDT": {
      "userId": 123,
      "symbol": "BTCUSDT",
      "lockedQuantity": 0.5,
      "availableQuantity": 1.0,
      "lockStatus": "PARTIALLY_LOCKED"
    }
  },
  "symbolSnapshots": {
    "BTCUSDT": {
      "symbol": "BTCUSDT",
      "minQuantity": 0.001,
      "maxQuantity": 1000.0,
      "tickSize": 0.1,
      "riskLimitConfig": {...}
    }
  },
  "memoryStats": {
    "totalOrderBooks": 10,
    "totalPositions": 1000,
    "totalSymbols": 10,
    "totalOrders": 50000,
    "totalTrades": 100000,
    "totalVolume24h": 5000000.00,
    "totalLockedPositions": 150,
    "totalActiveOrders": 25000
  },
  "kafkaOffsetSnapshots": {
    "match-results": {
      "topic": "match-results",
      "currentOffset": 1500,
      "committedOffset": 1400,
      "pendingOffset": 100,
      "consistent": false,
      "timestamp": 1640995200000
    },
    "snapshots": {
      "topic": "snapshots",
      "currentOffset": 50,
      "committedOffset": 50,
      "pendingOffset": 0,
      "consistent": true,
      "timestamp": 1640995200000
    }
  }
}
```

## 快照数据完整性

### 包含的关键数据
1. **订单薄数据**: 所有交易对的订单薄状态
2. **持仓数据**: 所有用户的持仓信息，包括锁定状态
3. **订单数据**: 所有活跃订单的完整信息
4. **仓位锁定数据**: 所有仓位锁定状态，确保数据一致性
5. **交易对配置**: 所有交易对的配置信息
6. **内存统计**: 完整的内存使用统计信息
7. **Kafka Offset**: 各主题的offset状态，确保消息推送一致性

### 数据恢复保证
- **完整性验证**: 恢复前验证快照数据完整性
- **原子性恢复**: 要么全部恢复成功，要么全部失败
- **状态一致性**: 确保恢复后的状态与快照时一致
- **服务可用性**: 恢复完成后服务立即可用
- **消费连续性**: 从快照offset开始消费，确保数据连续性
- **消息不丢失**: 通过offset管理确保消息不丢失

## 性能优化

### 1. 批量处理
- 消息在内存中批量收集
- 减少网络往返次数
- 提高吞吐量

### 2. 异步推送
- 不阻塞主撮合流程
- 使用独立线程池处理
- 支持背压控制

### 3. 错误处理
- 推送失败自动重试
- 异常消息记录日志
- 服务降级保护

### 4. 快照优化
- 增量快照支持
- 压缩存储
- 快速恢复

## 监控指标

### 关键指标
- 待推送消息数量
- 累计推送消息数量
- 推送成功率
- 平均推送延迟
- 快照生成时间
- 快照恢复时间
- 数据完整性验证结果
- Kafka offset一致性
- 待确认消息数量
- Offset延迟时间

### 告警建议
- 待推送消息数量 > 5000
- 推送失败率 > 1%
- 平均推送延迟 > 100ms
- 快照生成时间 > 10s
- 快照恢复时间 > 30s
- Offset不一致持续时间 > 60s
- 待确认消息数量 > 1000
- Offset延迟时间 > 5s

## 部署建议

### 1. Kafka集群配置
- 建议3个以上broker
- 适当增加分区数量
- 配置合适的副本因子

### 2. 网络配置
- 确保网络延迟 < 10ms
- 配置足够的带宽
- 监控网络丢包率

### 3. 资源分配
- 为Kafka生产者分配足够内存
- 监控CPU使用率
- 定期清理日志文件

### 4. 快照存储
- 使用高性能存储
- 定期备份快照数据
- 监控存储空间使用

## 故障排查

### 1. 推送失败
- 检查Kafka连接状态
- 验证主题是否存在
- 查看网络连接情况

### 2. 消息积压
- 检查消费者处理速度
- 增加Kafka分区数量
- 优化消费者配置

### 3. 性能问题
- 调整批量大小参数
- 优化网络配置
- 增加硬件资源

### 4. 快照问题
- 检查快照数据完整性
- 验证恢复过程日志
- 监控内存使用情况

## 使用场景

### 1. 服务重启恢复
- 服务启动时从最新快照恢复
- 从快照offset开始消费，确保数据连续性
- 确保数据不丢失

### 2. 数据备份
- 定期生成完整快照
- 备份到外部存储
- 支持历史数据查询

### 3. 故障转移
- 主备服务切换
- 从快照快速恢复
- 保证服务连续性

### 4. 数据迁移
- 服务升级时数据迁移
- 环境切换时数据同步
- 支持跨环境部署

### 5. 数据恢复
- 从快照恢复完整状态
- 从快照offset开始消费
- 确保数据连续性和一致性 