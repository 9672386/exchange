# 撮合运维接口设计:批量撤单 + 标的上下架

> 范围:指定订单撤单(已有,补解冻)、用户全撤、标的全撤、标的上架、标的下架。
> 已定:全部走 Raft 日志;撤单资金解冻由**撮合驱动**(与成交一致,走可靠 IPC 结算流)。

## 0. 三条铁律

1. **全部是状态机写操作 → 必须进 Raft 日志**(新增 MSG 类型),不能走现在的 `SymbolManagementService` 直接改内存旁路——否则副本分裂、重启丢失。
2. **批量操作确定性**:遍历订单/订单簿必须按稳定键(orderId 升序)排序,各副本产出顺序/seq 一致。
3. **撤单/下架释放资金必须可对账**:解冻额 = 该单冻结额中尚未结算的残余,不多不少。

## 1. 冻结额随单模型(资金对账的地基)

现状:`exchange-gateway` 是空壳,下单冻结编排未实现,`Order` 无冻结金额字段。为让"撮合驱动解冻"可对账,确立:

- 下单编排(网关)按币种算冻结额 `lockedAmount`(定点 long)与 `lockedAsset`,随 `EventNewOrderReq` 传入。
  - 限价买:`lockedAsset=quote`,`lockedAmount = amount + fee`(amount=price×qty)
  - 限价卖:`lockedAsset=base`,`lockedAmount = qty`
- `Order` 新增 `lockedAmount`(long)、`lockedAsset`(String)、`lockedRemaining`(long,初始=lockedAmount)。
- **每次成交结算**:从 `lockedRemaining` 扣减该笔消耗的冻结额(买方扣 `tradeQuote+buyFee`,卖方扣 `tradeQty`),与资产侧 SETTLE 扣冻结一致。
- **撤单**:对 `lockedRemaining` 残余发一条 UNFREEZE(release)到资产,`lockedRemaining` 清零。
- 单一真相源 = 随单的 `lockedRemaining`,撮合不重算冻结公式,天然对账。

> 若暂不接网关:`lockedAmount` 缺省时,撮合按 `Symbol` 费率+价格**降级重算**残余冻结(需与未来网关公式严格一致)。优先走随单模型。

## 2. 结算流协议扩展:UNFREEZE(release)

现有可靠 IPC 结算流(`MatchSettlementStream`,stream 2000)只承载成交(→ 资产 BATCH_SETTLE)。新增一类消息:

- **CANCEL_RELEASE**:`{userId, accountType, asset, amount, bizNo(=cancelId), orderId}` → forwarder → 资产 `UNFREEZE`(已有 `handleUnfreeze`/`ledger.unfreeze`)。
- 走同一条可靠录制流,和成交同等"绝不丢"保证(丢了资金冻死)。
- 资产侧 `UNFREEZE` 已支持幂等(processedBizNos),cancelId 作幂等键。

## 3. 五个接口(消息类型)

| 接口 | MSG | 处理 |
|---|---|---|
| 单撤 | `MSG_CANCEL`(0x02,已有) | 移出簿 + **补:对 lockedRemaining 发 CANCEL_RELEASE** |
| 用户全撤 | `MSG_CANCEL_USER`(新) | 按 orderId 升序遍历该用户挂单,逐笔=单撤 |
| 标的全撤 | `MSG_CANCEL_SYMBOL`(新) | 按 orderId 升序遍历该 symbol 订单簿,逐笔=单撤 |
| 上架 | `MSG_LIST_SYMBOL`(新) | 日志写入 symbol 配置(含 scale/费率/精度/风控)+ 置 ACTIVE |
| 下架 | `MSG_DELIST_SYMBOL`(新) | **安全序列**,见 §4 |

每个撤单/下架子操作消耗 seq 并产出对应资产事件,seq 无洞(沿用现有确定性 seq 机制)。

## 4. 下架安全序列(不能裸删)

```
DELIST(symbol):
  1. 置 SUSPENDED —— 拒绝新单(isTradeable=false),订单簿保留
  2. 标的全撤 —— 按 orderId 升序撤掉所有挂单,每笔发 CANCEL_RELEASE 解冻
  3. (合约)持仓处理 —— 强平/接管未平仓位;现货无此步
  4. 校验:订单簿空 且 无活跃持仓
  5. 置 DELISTED,可回收订单簿内存
```
现 `deleteSymbol` 仅"有挂单/持仓就拒绝"(护栏),缺 2–3 的自动编排,本次补齐。

## 5. 幂等与确定性

- 每个运维消息带 `bizNo`(cancelId / listId / delistId),资产侧解冻用它幂等。
- 批量撤单在**单条 Raft 日志**内完成整批(原子),中途不产生可见中间态;但每笔子撤单各自的资产 UNFREEZE 事件独立幂等。
- 遍历一律稳定排序;不读 wall-clock,用 cluster timestamp。
- 快照:symbol 生命周期状态、Order 的 lockedRemaining 都要进快照。

## 6. 实施顺序

1. **标的上架/下架入 Raft**(无资金账,先落):MSG_LIST/DELIST + 安全序列 + 快照。
2. **Order 冻结额字段 + 结算流 CANCEL_RELEASE 扩展**(资金地基)。
3. **单撤补解冻** → **用户全撤 / 标的全撤**(复用单撤 + 确定性遍历)。
4. 对账测试:下单冻结 → 部分成交 → 撤单,验证 `解冻额 = 冻结额 − 已结算`,账本 available/frozen 复原。
