# 撮合服务（Match Engine）完整说明

> 模块前缀:`exchange-match`
> 覆盖:模块说明 / 接入说明 / 部署说明 / 测试说明

---

# 一、模块说明

## 1.1 定位

撮合引擎是交易所的**订单簿权威**,负责接收委托、撮合成交、维护订单簿与仓位,并把成交结果可靠地送给资产、行情、风控等下游。

核心设计:**内存订单簿 + Raft 共识 + 双流输出**。

- 订单簿状态全内存,单线程撮合,确定性(所有副本相同输入产出相同结果)。
- Aeron Cluster(Raft)复制 Ingress 委托,崩溃可恢复。
- 成交输出**双流分离**:结算走可靠 IPC 流,行情/风控走尽力而为 UDP 流。

## 1.2 子模块与依赖分层

```
match-api    请求 DTO / 枚举 / 传输模型(Trade,MatchResponse)/ 契约常量   （无内部依赖）
match-core   撮合引擎 / 状态机 / Archive 录制 / 快照恢复                 → match-api, common, framework-transport
match-web    HTTP 入口 + 集群宿主                                      → match-api, match-core, framework-web
```

跨服务契约(`Trade` / `MatchResponse` / 撮合枚举 / 结算流常量)全部在 `match-api`,资产等下游只依赖 `match-api`,不碰 `match-core`。

## 1.3 核心组件

| 组件 | 职责 |
|---|---|
| `MatchClusteredService` | Aeron `ClusteredService`。解析 Ingress → 撮合 → Egress 回包 → 双流广播成交 |
| `MatchClusterNode` | 启动 `ClusteredMediaDriver` + ServiceContainer |
| `MatchEngineService` / matcher 族 | 撮合算法:LIMIT / MARKET / IOC / FOK / POST_ONLY |
| `OrderBook` / `MemoryManager` | 内存订单簿(价格层 + 时间优先)与仓位管理 |
| `AeronMatchResultPublisher` | **双流发布**:结算流(IPC,可靠)+ 实时流(UDP MDC,尽力而为) |
| `SnapshotEventHandler` / `EventReplayService` | 快照生成 / 非集群模式的 Archive 回放恢复 |
| `MatchRuntimeStatus` / `MatchSystemEvent` | 运行时观测视图 / 撮合专属系统事件 |

## 1.4 双流输出(关键设计)

成交需要发给两类下游,可靠性要求不同:

```
撮合成交 MatchResponse:
  ① 结算流(IPC, stream 2000)  → Archive 本地录制 → TradeSettlementForwarder   【可靠,自旋直到成功,绝不丢】
  ② 实时流(UDP MDC, 1001)     → Risk / Quote 动态订阅                       【尽力而为,慢消费者丢弃】
```

- **为什么分离**:若共用一条流,一个慢的行情/风控消费者背压,会把结算录制一起拖到丢弃 → 漏结算 → 买卖双方冻结资金永不释放。分离后结算持久性与实时消费者速度**彻底解耦**。
- 结算流常量:`MatchSettlementStream`(match-api)—— `SETTLEMENT_CHANNEL="aeron:ipc"`, `SETTLEMENT_STREAM=2000`。发布侧与消费侧共用。

## 1.5 撮合链路(端到端)

```
下单 → Cluster Ingress(20110)
  → MatchClusteredService.onSessionMessage(Raft 已提交)
      → 委托时效校验(Snowflake orderId,超 10s 拒绝)
      → MatchEngineService.submitOrder → 撮合 → MatchResponse
      → Egress 同步回包(ACK/REJECT)给订单服务
      → 结算流可靠录制 + 实时流广播(仅 Leader)
  → TradeSettlementForwarder 从结算 Archive 消费 → 转发资产结算
```

## 1.6 确定性约束

- 状态机内**禁止读 wall-clock**,统一用 `onSessionMessage` 的集群协调时间戳。
- 委托时效校验用集群时间(`SnowflakeId.tryExtractTimestampMs(orderId, clusterTimestamp)`),保证重放判定一致。
- 订单 `createTime` 用集群时间覆盖,保证所有副本时间优先顺序一致。

## 1.7 崩溃恢复与已知边界

| 场景 | 行为 |
|---|---|
| Cluster 节点崩溃(集群模式) | Raft 快照 + 日志重放自动恢复订单簿 |
| 快照 | 分块写(50 交易对/块 + 500 仓位/块)+ 有界重试;`ImageFragmentAssembler` 聚合 |
| 非集群模式重启 | 文件快照 + `EventReplayService` 回放结算 Archive 补齐快照后的成交 |
| 结算发布(Archive 未启用) | ⚠️ 降级模式无持久化,仅实时广播 —— **仅测试用,生产必须启用 Archive** |

## 1.8 系统事件(观测)

`MatchSystemEvent`(撮合专属)+ `CoreSystemEvent`(通用):
- `MATCH_ORDER_REJECTED` / `MATCH_CANCEL_MISS` / `MATCH_TRADE_PRODUCED`
- `REQUEST_EXPIRED`(委托超时,期望恒 0)、`SNAPSHOT_TAKEN/FAILED/RESTORED`、`CLUSTER_ROLE_CHANGED`、`EGRESS_DROPPED`

---

# 二、接入说明

## 2.1 接入方式总览

| 调用方 | 方式 | 用途 |
|---|---|---|
| 订单服务 | Cluster Ingress(下单/撤单) | 提交委托,收 ACK/REJECT |
| 资产服务(Forwarder) | 消费结算 Archive(stream 2000) | 获取成交做结算 |
| 行情/风控 | 订阅实时 UDP MDC(stream 1001) | 实时成交流 |
| 运维/监控 | REST | 状态 / 快照 / 恢复 |

## 2.2 下单接入(Ingress 消息格式)

```
[1 byte msgType][JSON body]
  0x01 MSG_NEW_ORDER   → EventNewOrderReq{orderId,userId,symbol,price,quantity,side,orderType,...}
  0x02 MSG_CANCEL      → EventCanalReq{orderId,userId}

Egress 回包:
  0x10 MSG_ACK         接受(挂单成功或全部成交)
  0x11 MSG_REJECT      拒绝(时效超时/业务原因)
  0x12 MSG_CANCEL_ACK  撤单确认
```

- `orderId` **必须是 Snowflake 格式**,引擎解析其时间戳做 10s 时效校验(超时拒绝)。
- 客户端通过 Aeron Cluster 客户端连接 Ingress,egress 回包与 ingress 同一 session。

## 2.3 消费成交(下游)

- **结算(资产)**:连接撮合 Archive,按 `MatchSettlementStream.SETTLEMENT_STREAM=2000` 找录制、按位点回放。这是**可靠、无丢**的流。
- **实时(行情/风控)**:订阅 UDP MDC 通道(`MATCH_CONTROL_ADDR` + `STREAM_ID=1001`),尽力而为,可动态加入,慢消费不影响撮合。

## 2.4 传输模型契约(match-api)

- `Trade`:tradeId、symbol、买卖双方 userId/orderId、price、quantity、amount、buyFee/sellFee、tradeTime、side、仓位动作
- `MatchResponse`:orderId、status、成交列表、仓位变动、撤单/拒绝信息
- 撮合枚举:`OrderSide` / `OrderType` / `MatchStatus` / `PositionSide` / `PositionAction` / `TradeSide`

## 2.5 REST 接口(match-web)

```
POST /api/match/event/new-order       下单(HTTP 入口,内部转 Ingress)
POST /api/match/event/cancel-order     撤单
POST /api/match/event/query-order      查询委托
POST /api/match/event/query-position   查询仓位
POST /api/match/event/liquidation      强平
POST /api/match/event/snapshot         触发快照
GET  /api/match/cluster/status         状态机运行时视图(role/logPosition/规模)
GET  /api/match/cluster/health         健康检查
GET  /api/match/monitoring/status      监控:内存/快照/重放状态
POST /api/snapshot/async/generate      异步生成快照
POST /api/snapshot/recovery/recover    从快照恢复
```

---

# 三、部署说明

## 3.1 部署单元

| 进程 | 模块 | 说明 |
|---|---|---|
| Match Web | match-web | HTTP 入口 + **内嵌 Aeron Cluster 节点**(Driver+Archive+ConsensusModule+ServiceContainer) |

## 3.2 端口约定(Aeron)

| 端口 | 用途 |
|---|---|
| 20110 | Cluster Ingress(下单入口) |
| 20220 | Member-to-member |
| 20330 | Raft log 复制 |
| 8010 | Match Archive 控制端口 |
| 40300 | 结算 Archive replay(Forwarder 消费) |
| (IPC) | 结算录制流 stream 2000;实时广播 stream 1001 |

## 3.3 环境变量

| 变量 | 默认 | 说明 |
|---|---|---|
| `CLUSTER_NODE_ID` | 0 | 本节点 ID(三节点分别 0/1/2) |
| `CLUSTER_MEMBERS` | `0,localhost:20110,localhost:20220,localhost:20330,localhost:0,localhost:8010` | 集群成员表 |
| `AERON_DIR` / `ARCHIVE_DIR` / `CLUSTER_DIR` | aeron-match-* | Aeron 目录 |
| `MATCH_CONTROL_ADDR` | 见 AeronConfigFactory | 实时 MDC 控制地址(行情/风控订阅) |
| `STREAM_ID` | 1001 | 实时 MDC stream(注意:结算流固定 2000,不受此影响) |
| `AERON_INTERFACE` | — | AWS ENI 绑定 IP(云上必填) |

关键开关:
```
aeron.enabled=true            # 默认 true
aeron.result-publisher=true   # 默认 true,启用双流发布
match.cluster.enabled=true    # 集群模式(Raft 恢复);false 走文件快照+回放
```

> ⚠️ **生产必须启用 Archive**,否则结算流无持久化(降级模式仅测试用)。

## 3.4 构建

```bash
cd exchange
mvn -DskipTests -pl :match-web -am install
```

## 3.5 启动顺序

1. **Match Web**(启动内嵌 Cluster + Archive,开始录制结算流)
2. 资产服务的 `TradeSettlementForwarder` 自动连接撮合 Archive 消费(启动时从 Asset Cluster 取上次位点)
3. 行情/风控按需订阅实时 MDC

## 3.6 三节点扩展

改 `CLUSTER_NODE_ID` + `CLUSTER_MEMBERS`(列全部成员,`|` 分隔),代码不变。撮合按 **symbol 水平分片**天然可扩(不同 symbol 无状态耦合)——这是撮合与资产(单账本)的关键区别。

---

# 四、测试说明

## 4.1 分层测试策略

| 层级 | 对象 | 重点 |
|---|---|---|
| 单元 | matcher 族 | 各订单类型撮合正确性、价格时间优先、部分成交 |
| 单元 | `OrderBook` | 挂单/撤单/成交后订单簿结构、并发安全 |
| 集成 | Cluster 单节点 | 下单 Ingress→撮合→Egress→双流广播 |
| 集成 | 双流 | 结算流可靠录制、实时流尽力而为、慢消费者不阻塞撮合 |
| E2E | 撮合+资产 | 成交→结算 Archive→Forwarder→资产账本更新 |
| 混沌 | 崩溃/背压 | 快照恢复、结算不丢、时效拒绝 |

## 4.2 单元测试要点

- LIMIT:限价撮合、挂单、部分成交、价格优先时间优先
- MARKET:吃对手盘直到成交或无对手
- IOC:立即成交剩余撤销;FOK:无法全额成交则整单撤销
- POST_ONLY:会立即成交则拒绝(只做 maker)
- 订单簿:成交后数量归零移除、撤单移除、时间优先顺序
- 确定性:相同委托序列在不同实例产出相同成交(无 wall-clock)

## 4.3 集成测试要点

- 下单 Ingress → Egress ACK;非法/超时委托 → REJECT(`REQUEST_EXPIRED`)
- 撤单命中/未命中(`MATCH_CANCEL_MISS`)
- 成交 → 结算流录制到 Archive + 实时流广播

## 4.4 双流可靠性测试(重点,对应审计 B 方案)

| 场景 | 预期 |
|---|---|
| 实时消费者(Risk/Quote)故意变慢 | 实时流丢弃,但**结算流一笔不丢**,撮合不阻塞 |
| 结算 Archive 背压 | 结算发布自旋直到成功(可被延迟监控发现),不丢结算 |
| Archive 未启用(降级) | 日志告警,结算无持久化(确认降级行为) |
| 成交后 kill 撮合 | 重启恢复订单簿;结算流已录制的成交不丢 |

## 4.5 持久化/恢复测试

| 场景 | 预期 |
|---|---|
| 大订单簿快照 | 分块写不超单消息上限,恢复完整(校验交易对/仓位块数) |
| Cluster 重启 | Raft 快照+重放恢复订单簿一致 |
| 非集群模式重启 | 文件快照 + 结算 Archive 回放补齐快照后成交 |

## 4.6 健康检查断言

- `GET /api/match/cluster/health` → `healthy:true`,`abnormalEvents` 为空
- 期望恒 0 的事件(`REQUEST_EXPIRED`、`SNAPSHOT_FAILED`、`EGRESS_DROPPED`)计数为 0

## 4.7 端到端结算验证(与资产联调)

```
1. 撮合下两笔可成交的对手单(买/卖)
2. 确认撮合返回成交(Egress ACK 带 trades)
3. 确认结算 Archive(stream 2000)录到该成交
4. 确认资产服务 TradeSettlementForwarder 消费并结算
5. 查资产:买卖双方余额正确变动、冻结释放(最终一致,稍候 persist 落库)
```
