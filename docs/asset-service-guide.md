# 资金服务（Asset Service）完整说明

> 模块前缀:`exchange-account`
> 覆盖:模块说明 / 接入说明 / 部署说明 / 测试说明

---

# 一、模块说明

## 1.1 定位

资金服务是交易所的**账本权威**,负责用户资产的冻结、结算、加减、划转与查询。

核心设计:**内存状态机 + Raft 共识 + CQRS 投影**。

- **权威状态**在内存(`BalanceLedger`),由 Aeron Cluster(Raft)复制,零数据库参与资金计算。
- **数据库是派生投影**(`t_user_asset` / `t_fund_flow`),由独立进程异步落库,只用于查询和审计。
- 资金操作全部走内存,毫秒级,单线程串行、无锁、确定性。

## 1.2 子模块与依赖分层

```
account-api      DTO / 枚举 / 事件 / 契约常量        （无内部依赖）
account-store    DB 投影层:实体 / Mapper / 查询服务   → account-api, common
account-core     内存状态机 / Gateway / 事件发布      → account-api, match-api, aeron   ★纯内存,不依赖 store
account-web      HTTP 入口 + 集群宿主 + 查询          → account-core, account-store
account-persist  Archive 消费落库(独立部署单元)      → account-store, account-api, aeron
```

依赖铁律:`account-core`(内存)**永不依赖** DB 层;`account-store`(DB)**永不反向依赖** core;服务间只经 `-api` 耦合。

## 1.3 核心组件

| 组件 | 模块 | 职责 |
|---|---|---|
| `BalanceLedger` | core | 内存账本状态机。三层结构 `userId → accountType → asset → Balance`。冻结/解冻/结算/加减/划转 + 幂等 + seq |
| `AssetClusteredService` | core | Aeron `ClusteredService`。解析 Ingress → 调账本 → 发事件(Leader)→ Egress 回包 |
| `AssetClusterNode` | core | 启动 `ClusteredMediaDriver`(Driver+Archive+ConsensusModule)+ ServiceContainer |
| `AssetGatewayService` | core | 同步请求/响应网关。correlationId + CompletableFuture,分片路由,Egress 轮询 |
| `AeronArchiveEventPublisher` | core | Leader 把 `AssetStateChangeEvent` 录制到 Archive(IPC stream 1000) |
| `AssetService` / `FundFlowService` | store | 读 `t_user_asset` 查询 + 消费事件 upsert 落库 |
| `AssetArchiveSubscriber` | persist | 回放 Archive → 单事务写流水+余额+位点 → seq 缺口检测 |
| `SystemEventReporter` | common | 观测事件计数 + 节流告警 |

## 1.4 数据模型

**内存 `Balance`**:`available`(可用)、`frozen`(冻结)。

**`AccountType`**(账户类型隔离):`SPOT` / `FUTURES` / `FUNDING` / `WEALTH` / `OPTIONS`。同一用户同一资产在不同账户类型下是**独立余额**。

**`t_user_asset`**:唯一键 `(user_id, account_type, asset)`,余额投影(最终一致)。

**`t_fund_flow`**:append-only 资金流水,`biz_type`(委托/成交/充提/调账/划转/手续费)+ `flow_type` 细分。

## 1.5 写入链路(端到端)

```
HTTP → AssetGatewayService → Aeron Cluster Ingress(20140)
   → AssetClusteredService.onSessionMessage(Raft 已提交)
       → BalanceLedger 变更(内存,所有副本)+ 分配 seq
       → 构造 AssetStateChangeEvent(带 seq)→ Leader 录制到 Archive
       → Egress 同步回包(correlationId)
   → account-persist 回放 Archive → 单事务落 t_user_asset / t_fund_flow
```

## 1.6 读取链路(CQRS 读侧)

```
HTTP 查询 → AssetService 读 t_user_asset 投影(不进 Raft 日志)
```
读侧是**最终一致**(相对内存账本有 persist 管道毫秒级延迟)。强一致场景以写操作的同步响应为准。

## 1.7 幂等与 seq

- **幂等**:每个操作携带业务单号。委托/成交/划转用 30 min TTL 内存表;充提/调账用永久表(随快照持久化)。
- **seq**:账本全局自增序号,"每产出一条流水消耗一个 seq,仅实际变更才消耗"。在所有副本确定性推进,入快照。事件携带 seq,persist 侧据此**检测事件丢失**(跳号 → `EVENT_SEQ_GAP` 告警)。

## 1.8 崩溃恢复与已知边界

| 场景 | 行为 |
|---|---|
| Cluster 节点崩溃 | Raft 快照 + 日志重放自动恢复,账本零丢失 |
| 快照 | 分块写(200 用户/块)+ 有界重试;`ImageFragmentAssembler` 聚合恢复 |
| persist 崩溃 | 从 `t_archive_position` 续读;流水+余额+位点单事务原子,无中间态 |
| Leader 提交后、发布事件前崩溃 | ⚠️ 该事件丢失 → DB 投影落后。**内存账本仍正确**(权威),可对账修复;seq 缺口检测秒级发现 |

---

# 二、接入说明

## 2.1 接入方式总览

| 调用方 | 方式 | 用途 |
|---|---|---|
| 订单服务 | REST(经 Gateway 转 Cluster) | 下单冻结 / 撤单解冻 |
| 撮合结算转发器 | Aeron Cluster Ingress(BATCH_SETTLE) | 成交结算 |
| 充提/运营/风控 | REST | 加钱 / 减钱 / 划转 |
| 行情/前端 | REST(查询) | 余额 / 流水查询 |
| 运维/监控 | REST | 集群状态 / 系统事件 |

## 2.2 REST 接口(account-web,默认端口 8085)

### 写操作(经 Gateway → Cluster)

```
POST /api/asset/freeze              冻结     Body: FreezeReq{userId,accountType,asset,amount,orderId}
POST /api/asset/unfreeze            解冻     Body: FreezeReq
POST /api/asset/batch-freeze        批量冻结  Body: BatchFreezeReq{userId,accountType,items[]}
POST /api/asset/credit              加钱     Body: CreditDebitReq{userId,accountType,asset,amount,bizNo,remark}
POST /api/asset/debit               减钱     Body: CreditDebitReq
POST /api/asset/internal-transfer   内部划转  Body: InternalTransferReq{userId,fromAccountType,toAccountType,asset,amount,bizNo}
```

### 读操作(DB 投影,最终一致)

```
GET  /api/asset/{userId}/{accountType}/{asset}   查单资产
GET  /api/asset/{userId}/{accountType}            查某账户类型全部资产
GET  /api/asset/{userId}                          查全部账户类型全部资产
POST /api/asset/flow/query                        资金流水分页查询  Body: FundFlowQueryReq
```

### 运维/监控

```
GET  /api/cluster/status        状态机运行时视图(role/seq/位点/规模)
GET  /api/cluster/health        健康检查(是否有期望恒 0 的异常事件)
GET  /api/system-event/stats    系统事件计数
GET  /api/system-event/health   异常事件健康检查
```

## 2.3 幂等契约(接入方必读)

- **所有写操作必须带业务单号**(orderId / bizNo),同一单号重复提交被安全忽略,不会重复扣加钱。
- **单号唯一性由调用方保证**。委托类用 Snowflake orderId(引擎解析时间戳做 30 min 有效期校验);充提类用外部单号(depositId/withdrawId)。
- 委托/成交单号超过 30 min 会被拒绝(`REQUEST_EXPIRED`),这是内存幂等表只保留 30 min 的前提。

## 2.4 账户类型语义

- 充值默认落 `FUNDING`,用户需 `internal-transfer` 划到 `SPOT`/`FUTURES` 才能交易。
- 写操作的 `accountType` **必填且严格校验**,非法值直接拒绝(不 fallback,避免钱记错账户)。

## 2.5 成交结算接入(撮合侧)

撮合不直接调 REST。`TradeSettlementForwarder`(运行在 account-core)从撮合的**结算 Archive 流**(IPC stream 2000)消费 `MatchResponse`,打包成 `BATCH_SETTLE` 发到 Asset Cluster Ingress。`archivePosition` 随 BATCH_SETTLE 原子写入 Raft 日志,保证位点与账本一致。

## 2.6 事件契约(下游消费)

`AssetStateChangeEvent`(account-api)字段:`eventId`(幂等键)、`eventType`、`userId`、`accountType`、`asset`、`available`、`frozen`、`amount`、`flowType`、`bizNo`、`clusterTimestamp`、**`seq`**(无洞递增,用于丢失检测)。

录制通道契约:`AssetArchiveStream`(account-api)—— `RECORDING_CHANNEL="aeron:ipc"`, `RECORDING_STREAM=1000`。发布侧与消费侧共用。

---

# 三、部署说明

## 3.1 部署单元

| 进程 | 模块 | 端口 | 说明 |
|---|---|---|---|
| Asset Web | account-web | HTTP 8085 | HTTP 入口 + **内嵌 Aeron Cluster 节点** + 查询 |
| Asset Persist | account-persist | HTTP 8086 | Archive 消费落库(独立进程) |
| MySQL | — | 3306 | `exchange_account` 库 |

> Asset Web 进程内同时跑 Cluster 状态机;Persist 是独立进程,可单独扩缩容。

## 3.2 端口约定(Aeron)

| 端口 | 用途 |
|---|---|
| 20140 | Cluster Ingress(Gateway/Forwarder 入口) |
| 20250 | Member-to-member |
| 20360 | Raft log 复制 |
| 8020 | Asset Archive 控制端口 |
| 40201 | Asset Archive replay(persist 消费) |

## 3.3 环境变量

| 变量 | 默认 | 说明 |
|---|---|---|
| `DB_USER` / `DB_PASS` | root / root | MySQL 账号 |
| `ASSET_CLUSTER_NODE_ID` | 0 | 本节点 ID(三节点分别 0/1/2) |
| `ASSET_CLUSTER_MEMBERS` | `0,localhost:20140,localhost:20250,localhost:20360,localhost:0,localhost:8020` | 集群成员表 |
| `ASSET_AERON_DIR` / `ASSET_ARCHIVE_DIR` / `ASSET_CLUSTER_DIR` | aeron-asset-* | Aeron 目录 |
| `ASSET_ARCHIVE_CONTROL` / `ASSET_ARCHIVE_REPLAY` | 见上 | persist 连接 Archive 的通道 |

关键配置(application.yml):
```yaml
asset:
  cluster:
    enabled: true
    ingress: { endpoints: "aeron:udp?endpoint=localhost:20140" }   # 分片数=endpoints 数,当前只能为 1
  event: { publisher: archive }        # 默认 archive;kafka 为降级(默认关)
system-event: { enabled: true, ... }   # 观测事件节流策略
```

> ⚠️ **分片限制**:按 userId 分片与成交结算互斥(买卖双方跨 shard),当前 `ingress.endpoints` 只能配 1 个。资产账本单节点容量约 20–50 万笔/秒,足够。

## 3.4 构建

```bash
cd exchange
# 完整构建(首次或改了 parent/framework)
mvn -DskipTests install
# 只构建资产相关(含依赖链)
mvn -DskipTests -pl :account-web,:account-persist -am install
```
> `exchange-parent/pom.xml` 承载全局依赖版本(含 aeron 1.44.1 / agrona 1.20.0),**务必纳入版本控制**,丢失会导致全局构建失败。

## 3.5 启动顺序

1. MySQL(建库 `exchange_account`,建表 `t_user_asset` / `t_fund_flow` / `t_archive_position`)
2. **Asset Web**(启动内嵌 Cluster + Archive)
3. **Asset Persist**(连接 Archive 消费)

> Persist 若先于 Web 启动会重试等待 Archive 录制出现,不致命。

## 3.6 建表要点

- `t_user_asset`:唯一索引 `(user_id, account_type, asset)`
- `t_fund_flow`:`event_id` 建 **UNIQUE**(NULL 不参与),跨重启幂等兜底;主键顺序自增
- `t_archive_position`:主键 `recording_id`,存消费位点

## 3.7 三节点扩展

只改环境变量(`ASSET_CLUSTER_NODE_ID` + `ASSET_CLUSTER_MEMBERS` 列全部成员),代码不变。三节点 Raft 容忍单节点故障。

---

# 四、测试说明

## 4.1 分层测试策略

| 层级 | 对象 | 重点 |
|---|---|---|
| 单元 | `BalanceLedger` | 冻结/结算/加减/划转的余额正确性、幂等、seq 推进、异常回滚 |
| 单元 | `AssetServiceImpl` | 投影查询、upsert 按 (userId,accountType,asset) 定位 |
| 集成 | Cluster 单节点 | Ingress→账本→事件→Egress 全链路 |
| 集成 | persist | 事件回放→单事务落库→幂等→seq 缺口检测 |
| E2E | Web+Cluster+Persist+MySQL | REST 写→查询最终一致 |
| 混沌 | 崩溃/背压 | 快照恢复、位点续读、事件丢失检测 |

## 4.2 单元测试要点(BalanceLedger,纯内存无需 Aeron)

- 冻结:可用足额→available↓ frozen↑;余额不足→抛异常,状态不变
- 结算:四步原子(买扣/买入/卖扣/卖入);冻结不足→整体抛异常
- 幂等:同一 orderId/tradeId 重复调用→第二次跳过,seq 不推进
- 加减钱:永久幂等,同一 depositId 重发不重复加钱;DEBIT 余额不足抛异常且不占用幂等键
- 划转:fromType==toType 拒绝;跨类型原子(出账+入账)
- **seq**:每次实际变更 `currentSeq()` 递增对应流水数;幂等跳过/异常不推进
- **快照**:export→restore 后账本、幂等表、seq、位点完全一致

## 4.3 集成测试要点(需起 Aeron Cluster)

- 单节点:发 FREEZE Ingress → 收 FREEZE_OK Egress + 事件被 Archive 录制
- 批量:BATCH_FREEZE 原子性(任一资产不足整批失败)
- 幂等:重复 correlationId / 重复 orderId
- 时效:超 30 min 的 Snowflake orderId 被拒(`REQUEST_EXPIRED`)
- 严格校验:CREDIT/DEBIT 缺 accountType 或非法值被拒(`REQUEST_INVALID`)

## 4.4 持久化/恢复测试(重点)

| 场景 | 预期 |
|---|---|
| Cluster 重启 | 快照+重放恢复,账本与崩溃前一致 |
| 大账本快照 | 分块写入不超单消息上限,恢复完整(校验块数) |
| persist 中途 kill | 重启从旧位点续读,`event_id` 幂等去重,无重复流水 |
| 手动删一条事件(模拟丢失) | persist 检测到 seq 跳号 → `EVENT_SEQ_GAP` 告警 |
| 事件发布失败 | `EVENT_PUBLISH_DROPPED` 计数,`/api/cluster/health` 报 unhealthy |

## 4.5 健康检查断言

- `GET /api/cluster/health` → `healthy:true` 且 `abnormalEvents` 为空
- `GET /api/system-event/stats` → 期望恒 0 的事件(REQUEST_EXPIRED/INVALID、EVENT_PUBLISH_DROPPED、EVENT_SEQ_GAP、SNAPSHOT_FAILED、EGRESS_DROPPED)计数应为 0

## 4.6 冒烟脚本(手工)

```bash
# 1. 充值到 FUNDING
curl -XPOST :8085/api/asset/credit -H 'Content-Type: application/json' \
  -d '{"userId":1,"accountType":"FUNDING","asset":"USDT","amount":"1000","bizNo":"dep-1"}'
# 2. 划转到 SPOT
curl -XPOST :8085/api/asset/internal-transfer -H 'Content-Type: application/json' \
  -d '{"userId":1,"fromAccountType":"FUNDING","toAccountType":"SPOT","asset":"USDT","amount":"500","bizNo":"tf-1"}'
# 3. 查询(最终一致,稍等 persist 落库)
curl :8085/api/asset/1/SPOT/USDT
# 4. 幂等验证:重发步骤 1,余额不变
```
