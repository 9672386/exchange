# 资金服务:分层、幂等与 seq 设计规范

> 适用范围:`exchange-account`(账户网关 / Asset Cluster / account-persist)
> 状态:设计稿,待评审后实施
> 修订:v2 —— 按"领域网关"分层重写,移除 RocksDB 侧车方案

---

## 0. 分层职责

| 层 | 模块 | 职责 | 明确不做 |
|---|---|---|---|
| **边缘网关** | `exchange-gateway` | PC/APP/H5 鉴权、全局限流、防重放攻击、按域路由 | 不理解资金语义 |
| **账户网关** | `AssetGatewayService` | 语义化接口 → `bizType`/`accountType`/操作 组装<br>参数强校验<br>时效快速失败<br>长窗口去重查询<br>分片路由 + Egress 等待 | 不碰账本 |
| **资金状态机** | `AssetClusteredService` | 30 min 内存幂等<br>账本变更<br>`seq` 分配<br>事件构造(全副本)+ Leader 发布<br>时效终校验 | 无 I/O、无参数校验、无业务推导 |
| **持久层** | `account-persist` | 单事务写 `t_fund_flow` + `t_asset_dedup` + 消费位点<br>`seq` 连续性检测 | 不回写账本 |

设计取向:**校验与业务翻译全部前移到账户网关**,状态机只保留"必须确定性执行"的最小集合。
状态机代码越短,确定性越容易逐条验证。

> 撮合域同构:撮合网关负责 symbol 路由、委托组装、时效校验。本文不展开。

---

## 1. 三个 ID 的职责边界

| 标识 | 生成方 | 作用域 | 用途 | 入库字段 |
|---|---|---|---|---|
| `requestId` | 上游业务系统 | 该业务内唯一 | 幂等判定的输入 | `biz_no` |
| `bizType` | **账户网关(推导)** | 全局枚举 | 幂等判定的作用域 | `biz_type` |
| `seq` | 资金状态机 | 单 shard 内自增 | 流水主键、丢失检测 | 主键 |

核心原则:

- **`requestId` 对资金服务只是"请求编号"**。订单号、充值单号在各自系统内唯一,跨系统会重复,单用它做幂等键不安全。
- **`bizType` 是幂等作用域,由网关按接口结构性推导,不由外部传入**(见第 2 节)。
- **`seq` 是资金服务自己的产出编号**,仅在实际发生数据变动时消耗。

---

## 2. BizType 划分与接口映射

### 2.1 为什么由网关推导,而不是上游传字段

若 `bizType` 作为请求字段由上游传入:

- 上游需要理解资金域枚举,填错即污染幂等空间
- 无法防止伪造(恶意或误配置传成别的 bizType)
- 通用接口 `POST /credit` 被多个业务共用,单号命名空间靠调用方自觉

改为**网关按语义化接口推导**后,`bizType` 由 URL 路径结构性决定,上述风险全部消失。
`accountType` 同理:由 symbol 或接口推导,不需要上游传,也就不存在"非法值 fallback"的问题。

> 注意:`bizType` 仍然出现在 Ingress 消息里传给 Cluster,
> 区别在于它的**来源是网关而非外部调用方**——这是信任边界的问题,不是字段有无的问题。

### 2.2 接口 → bizType 映射表

| 接口 | bizType | accountType | 账本操作 | requestId | 长窗口去重 |
|---|---|---|---|---|---|
| `POST /asset/order/freeze` | `ORDER_FREEZE` | 按 symbol 推导 | FREEZE | orderId | 否 |
| `POST /asset/order/unfreeze` | `ORDER_UNFREEZE` | 按 symbol 推导 | UNFREEZE | orderId | 否 |
| *(内部 Ingress)* | `TRADE_SETTLE` | 按 symbol 推导 | SETTLE | tradeId | 否 |
| *(内部 Ingress)* | `LIQUIDATION` | 按 symbol 推导 | SETTLE | liquidationId | 否 |
| `POST /asset/transfer/internal` | `INTERNAL_TRANSFER` | 显式 from/to | TRANSFER | transferId | 否 |
| `POST /asset/transfer/user` | `USER_TRANSFER` | 显式 | TRANSFER | transferId | 否 |
| `POST /asset/deposit` | `DEPOSIT` | FUNDING | CREDIT | depositId | **是** |
| `POST /asset/withdraw` | `WITHDRAW` | FUNDING | DEBIT | withdrawId | **是** |
| `POST /asset/withdraw/refund` | `WITHDRAW_REFUND` | FUNDING | CREDIT | withdrawId | **是** |
| `POST /asset/activity-reward` | `ACTIVITY_REWARD` | FUNDING | CREDIT | rewardRecordId | **是** |
| `POST /asset/fee-rebate` | `FEE_REBATE` | SPOT | CREDIT | rebateId | **是** |
| `POST /asset/admin/adjust-in` | `MANUAL_ADJUST_IN` | 显式 | CREDIT | ticketId | **是** |
| `POST /asset/admin/adjust-out` | `MANUAL_ADJUST_OUT` | 显式 | DEBIT | ticketId | **是** |
| `POST /asset/risk/penalty` | `PENALTY` | 显式 | DEBIT | caseId | **是** |

关键观察:

- `ORDER_FREEZE` / `ORDER_UNFREEZE` 共用 orderId,`WITHDRAW` / `WITHDRAW_REFUND` 共用 withdrawId
  —— 靠 bizType 区分,这是幂等键必须包含 bizType 的直接证据。
- **需要长窗口去重的 bizType 全部是低频业务**(充提、运营、风控、人工),
  高频路径(委托、成交)只需 30 min 窗口。这个分布是整套方案成立的前提。

### 2.3 契约

> 同一个 `bizType` 下,`requestId` 由唯一的上游系统发号,该系统保证其永不重复。

新增业务接入资金服务时:**新增语义化接口 + 新增 bizType 枚举**,不允许复用已有枚举。

### 2.4 ⚠️ 命名冲突

代码中**已存在** `FundFlowBizType` 枚举(`ORDER` / `TRADE` / `DEPOSIT_WITHDRAW` / `ADJUSTMENT` /
`TRANSFER` / `FEE`),用于报表大类分组,与本文的 `bizType` 概念**粒度不同但名字相同**,
直接沿用会造成严重混淆。

建议新枚举命名为 **`BizScene`(业务场景)**,三者关系为逐级推导:

```
BizScene.DEPOSIT        → FundFlowType.CREDIT   → FundFlowBizType.DEPOSIT_WITHDRAW
BizScene.ACTIVITY_REWARD → FundFlowType.CREDIT   → FundFlowBizType.ADJUSTMENT
BizScene.ORDER_FREEZE   → FundFlowType.FREEZE   → FundFlowBizType.ORDER
```

即 `BizScene` 是最细粒度的入口标识,现有两级枚举由它推导得出,
持久层三个字段全部落库(`biz_scene` / `flow_type` / `biz_type`),
分别服务于**幂等**、**流水语义**、**报表分组**三种用途。

本文其余部分为行文连贯仍写作 `bizType`,实现时请统一替换为 `bizScene`。

---

## 3. 幂等设计:两层窗口

### 3.1 分工

| 层 | 存储 | 窗口 | 覆盖范围 | 适用 bizType |
|---|---|---|---|---|
| **账户网关** | `t_asset_dedup`(共享 MySQL) | 长期 | 分钟级以上的重发 | 仅长窗口业务 |
| **资金状态机** | 内存 Map | 30 min TTL | 并发、实时、持久化延迟 | **全部** |

高频路径不查库,直接转发,由状态机内存表兜底;低频路径多一次约 1 ms 的索引查询,业务上无感
(一笔充值本来就要等链上确认数秒)。

### 3.2 为什么网关侧必须用共享存储

账户网关要多实例部署(HA + 吞吐),嵌入式本地存储(如 RocksDB)各实例互不可见:

```
请求 A(depositId=100001) → gateway-1 → 写本地存储 → 转发
31 分钟后重发            → gateway-2 → 本地查不到 → 放行
                                     → 状态机内存 TTL 已过期
                                     → 重复加钱
```

粘性路由(按 requestId 哈希到固定实例)理论可行,但实例故障/扩缩容时 key 段易主,
新 owner 无历史数据,切换期出现去重空洞。资金场景不接受。

### 3.3 去重表由持久层维护,网关只读

若由网关自己写去重表,需处理 TOCTOU 与崩溃:先写再提交会阻塞合法重试,
先提交再写会在崩溃时丢记录,于是要引入 PENDING/SUCCESS 状态机和清扫任务。

**改为持久层维护的投影,网关只查不写:**

```
状态机执行 → 发布事件 → account-persist
                          ├─ 写 t_fund_flow
                          └─ 若该 bizType 需长窗口,同事务写 t_asset_dedup

网关收到请求 → 参数校验 → 时效快速失败
             → 若需长窗口:查 t_asset_dedup
                  命中   → 直接返回 {duplicate:true, seq:...}
                  未命中 → 转发状态机
             → 高频 bizType:跳过查询直接转发
```

`t_asset_dedup` 结构:

| 字段 | 说明 |
|---|---|
| `biz_type` + `request_id` | **UNIQUE**,幂等键 |
| `shard_id` + `seq` | 指向 `t_fund_flow` 的起始主键,命中时返回给上游 |
| `user_id` | 便于按用户排查 |
| `created_at` | 归档依据 |

好处:网关无写路径 → 无 TOCTOU、无状态机、无清扫任务;表只含低频业务,体积小,
且不需要给高频写入的 `t_fund_flow` 加二级索引。

### 3.4 两层窗口的覆盖分析

去重表异步写入,相对状态机有毫秒级延迟,由内存 TTL 兜底:

| 重复请求到达时刻 | 网关去重表 | 状态机内存 | 结果 |
|---|---|---|---|
| 并发(同毫秒) | 查不到 | **命中** | 拦截 |
| T + 数秒 | 可能查不到 | **命中** | 拦截 |
| T + 5 分钟 | 命中 | 命中 | 拦截 |
| T + 31 分钟 | **命中** | 已过期 | 拦截 |
| T + 3 个月 | **命中** | 已过期 | 拦截 |

两窗口重叠覆盖(毫秒级延迟 ≪ 30 min),无缝隙。
因此 30 min 只需**远大于持久化延迟**即可,不需精确调优。

### 3.5 状态机内的幂等结构

统一一张表,**不再区分永久表与 TTL 表**:

```java
Map<String, IdempotentRecord> processedRequests;   // key = bizType + ":" + requestId

record IdempotentRecord(
    long seq,        // 首次执行分配的起始 seq(反查 t_fund_flow)
    long clusterTs   // 首次执行的 cluster 时间戳(计算过期)
) {}
```

存 `seq` 而非布尔值,换来三点:

1. 重复请求可返回 `{"status":"OK","seq":10086,"duplicate":true}`,上游能区分"刚执行"与"早已执行"
2. 拿外部单号可直接定位流水行,无需扫 `biz_no`
3. 过期时间按当前策略实时计算,调整窗口不需重写历史记录

### 3.6 时效校验:两层都要保留

| | 网关侧(wall-clock) | 状态机侧(cluster timestamp) |
|---|---|---|
| 目的 | 快速失败,不浪费 Raft 轮次 | **保证 30 min TTL 表是充分的** |
| 能否省 | 可以(纯优化) | **不能** |

状态机侧的 `checkBizNoExpiry` 是内存 TTL 正确性的前提:因为拒绝了超过 30 min 的请求,
所以只记 30 min 的幂等就够。若仅在网关拦截,存在窄窗:

```
T+29:59  网关校验通过(本地时钟)
T+30:01  消息经 ingress 排队后到达状态机 → 若不检查则放行
         → 该 requestId 的幂等记录可能刚被 evict → 重复执行
```

---

## 4. seq 规范

### 4.1 分配规则

> **每产出一条资金流水记录,消耗一个 seq。**

| 操作 | 产出流水数 | 消耗 seq |
|---|---|---|
| FREEZE / UNFREEZE | 1 | 1 |
| CREDIT / DEBIT | 1 | 1 |
| 内部划转 | 2(OUT + IN) | 2 |
| 单笔成交结算 | 4(买扣/买入/卖扣/卖入) | 4 |
| N 笔批量结算 | 4N | 4N |

### 4.2 消耗条件

> **仅当实际发生状态变更时消耗。**

| 场景 | 状态变更 | 消耗 seq |
|---|---|---|
| 正常执行 | 是 | 是 |
| 幂等命中跳过 | 否 | **否** |
| 余额不足抛异常 | 否 | **否** |
| 批量中部分重复 | 部分 | 按实际执行条数 |

重放走同样的判定分支,消耗同样数量的 seq,完全可复现。

### 4.3 确定性硬约束(违反任一即副本分歧)

**① seq 递增必须在所有副本无条件执行**

```java
// ✗ 错误:整个构造在 leader-only 分支内,Follower 计数器不前进
if (cluster.role() == Cluster.Role.LEADER && eventPublisher != null) {
    publishSettleEvents(...);        // 内部分配 seq
}

// ✓ 正确:分配与构造在所有副本执行,只有发布被 gate
List<AssetStateChangeEvent> events = buildSettleEvents(...);   // 分配 seq
if (cluster.role() == Cluster.Role.LEADER && eventPublisher != null) {
    events.forEach(eventPublisher::publish);
}
```

> **当前 `handleSettle`(第 245 行)与 `handleBatchSettle`(第 380 行)是错误写法**,
> `publishSettleEvents` 整体位于 leader 分支内。
> 平时无感;Leader 切换后新 Leader 的 seq 落后 → 重发已用过的主键 → 流水覆盖或插入失败。

**② 分配 seq 的遍历顺序必须确定**

只允许遍历有序集合(`List`、固定顺序的字段序列)。
禁止遍历 `HashMap` / `HashSet` 并在循环内分配 seq —— 迭代顺序跨 JVM 实例不保证一致。

**③ 状态机内不得读取 wall-clock**

所有时间判断使用 cluster timestamp。
`SnowflakeId.tryExtractTimestampMs` 须使用带 `referenceMs` 的重载。

### 4.4 主键格式

```
t_fund_flow  PRIMARY KEY (shard_id, seq)
```

当前 `shard_id` 恒为 0(见第 5 节),但**字段必须现在就留**。
将来若真需拆分,主键格式不用改;现在不留,以后加就是全表迁移。

---

## 5. 分片结论:资金单账本,撮合按 symbol

### 5.1 按 userId 分片与成交结算架构互斥

```java
// BalanceLedger.settleTrade —— 买卖双方在同一个 ledger 实例上操作
Balance buyerQuote = getOrCreate(buyerId,  accountType, quoteAsset);
Balance sellerBase = getOrCreate(sellerId, accountType, baseAsset);
```

```java
// TradeSettlementForwarder —— 整批只按第一笔的 buyerId 路由到一个 shard
if (routingUserId == null) routingUserId = trade.getBuyUserId();
assetGatewayService.batchSettle(routingUserId, tradeItems, archivePosition);
```

每个 shard 是独立 Raft 组、独立 `BalanceLedger`。当 `totalShards > 1`:

1. 批次按"第一笔买家"选定 shard A
2. 批内其他成交的买卖方可能属于 shard B、C
3. shard A 账本中这些用户无余额 → `getOrCreate` 造出零余额
4. 预校验 `frozen < required` → 抛异常
5. Forwarder 捕获 → 位点不推进 → 5s 后重试 → **无限循环,结算停摆**

撮合买卖双方随机配对,不可能同 shard。因此 **`totalShards` 实际只能是 1**,
`ShardRouter` 中"扩大 totalShards 即可扩容"的注释是错误的。

### 5.2 容量论证

单线程账本每笔结算约 4 次 map 查找 + 若干 BigDecimal 运算,约 2–5 µs,
即 **20–50 万笔/秒**;BATCH_SETTLE 进一步摊薄 Raft 共识成本。
该数字远超头部交易所峰值,单账本足够。

真正需要水平扩展的是撮合:按 symbol 分片天然成立,不同 symbol 之间无状态耦合。

### 5.3 处置

- 资金侧明确采用**单一账本**
- `ShardRouter` 保留但在 `totalShards > 1` 时**启动即报错**,防止误配置后在生产撞上无限重试
- 主键预留 `shard_id` 字段,恒为 0

---

## 6. 附带收益:事件丢失可检测

`seq` 无洞递增,`account-persist` 记录"已处理到 seq = N"。
下一条事件若为 N+2,立即判定 N+1 丢失。

这把"Leader 崩溃导致事件永久丢失、DB 与账本静默分歧"的问题,
从"需定期跑全量对账才能发现"降级为"消费时顺带检测,秒级告警"。

同时 `t_fund_flow` 的幂等直接由主键实现,不再需要 `event_id` 字符串唯一索引:

| | `event_id` VARCHAR UNIQUE | `(shard_id, seq)` PK |
|---|---|---|
| 索引体积 | 大 | 小 |
| InnoDB 插入 | 随机位置,页分裂 | 顺序追加 |
| 幂等 | 唯一索引冲突 | 主键冲突 |
| 丢失检测 | 做不到 | **连续性即可检测** |

> 残留缺口:事件丢失时 `t_asset_dedup` 也拿不到记录,31 分钟后重发会二次执行。
> 该缺口由上述 seq 连续性告警覆盖,非本方案新增。

---

## 7. 与当前实现的差异

| 项 | 当前 | 目标 |
|---|---|---|
| 接口形态 | 通用 `/credit`、`/debit` | 语义化 `/deposit`、`/activity-reward` … |
| bizType(bizScene) | 无,靠操作类型前缀 | 网关按接口推导,**必须落库** |
| 枚举命名 | `FundFlowBizType` 已占用 | 新增 `BizScene`,避免概念混淆 |
| 幂等键 | `"CREDIT:" + bizNo` | `bizType + ":" + requestId` |
| 幂等值 | `Set<String>` / 过期时间 | `IdempotentRecord(seq, clusterTs)` |
| 幂等表 | 两张(TTL + 永久) | **一张,统一 30 min TTL** |
| 长窗口去重 | 状态机内存永久表 | 网关查 `t_asset_dedup` |
| accountType | 写操作可 fallback | 网关推导,状态机不接受缺省 |
| seq | 无 | 状态机自增 |
| 流水主键 | 雪花 `ASSIGN_ID` + `event_id` 唯一索引 | `(shard_id, seq)` |
| settle 事件构造 | leader-only 分支内 | 全副本执行,仅发布 gate |
| 分片 | `userId % totalShards`,注释称可扩容 | 单账本 + 启动保护 |

---

## 8. 实施顺序

| # | 事项 | 危险度 | 依赖 | 说明 |
|---|---|---|---|---|
| 1 | seq 递增移出 leader-only 分支 | 高 | — | 现存缺陷,Leader 切换时爆发 |
| 2 | 语义化接口 + bizType 网关推导 | 高 | — | 现存缺陷,跨业务单号碰撞静默吞钱 |
| 3 | `ShardRouter` 启动保护 | 中 | — | 一行判断,防误配置 |
| 4 | 引入 seq + 主键改 `(shard_id, seq)` | 中 | 1 | 同时移除 `event_id` 索引 |
| 5 | 幂等值改 `IdempotentRecord`,响应带 `seq`/`duplicate` | 中 | 4 | |
| 6 | `t_asset_dedup` 投影表 + 持久层写入 | 中 | 4 | |
| 7 | 网关接入去重查询 | 中 | 6 | |
| 8 | **移除 `permanentBizNos`,统一 30 min TTL** | 中 | **7** | ⚠️ 必须在 7 上线并回填后 |
| 9 | persist 侧 seq 连续性检测告警 | 低 | 4 | |

### 关键依赖

第 8 项**必须等第 7 项上线之后**。
若先移除永久内存表而网关查询尚未生效,会出现去重空窗:
超过 30 分钟的充提重发将无人拦截,直接重复入账。

**关于历史数据回填:**

当前处于开发阶段、无生产数据,不涉及回填。

但需注意:**历史 `t_fund_flow` 无法回填出新的 bizType 粒度**。
现有记录只有 `flow_type = CREDIT` 和 `biz_no`,无从判断这笔 CREDIT 究竟来自充值还是活动奖励
—— 该信息从未被记录。若将来在有数据的环境做同类改造,只能:

- 按 `biz_no` 的格式特征人工推断(不可靠),或
- 接受历史记录不参与长窗口去重(此时旧单号重发无人拦截),或
- 要求上游提供单号清单反查

结论:**bizType 必须从第一天就落库**,这是本次改造要连带解决的事(见第 7 节差异表)。

---

## 9. 待决策项

1. **30 min TTL 是否足够** —— 取决于 Ingress 排队的最坏延迟。建议压测确认后固化。
2. **`t_asset_dedup` 保留期** —— 永久,还是 N 年后离线归档?归档后老单号重放如何处置?
3. **账户网关是否独立部署** —— 当前 `AssetGatewayService` 内嵌于 `account-web`。
   独立部署便于单独扩缩容与限流,但增加一跳延迟。
4. **`USER_TRANSFER` 是否实现** —— 单账本下无跨分片问题,实现成本低。
5. **撮合按 symbol 分片的推进时机** —— 资金侧确定单账本后,撮合是唯一的扩展维度。
