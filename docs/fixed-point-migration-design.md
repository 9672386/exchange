# BigDecimal → 定点 long 改造设计

> 状态:设计已定,实现分阶段。范围 = 资产 + 撮合全量内部定点化,按币种可配 scale。

## 0. 一句话结论

**热路径的内存状态机与服务间事件全部用 `long`(定点原始单位)表示金额/数量;冷边界(对外 HTTP DTO、数据库列)保持 `BigDecimal`,只在进出边界做一次 `BigDecimal↔long` 转换。**

"全量"指内部状态机与撮合乘除全部定点化,**不**指改动对外 JSON 协议或 DB 表结构——那样会破坏外部客户端与对账系统,收益却在冷边界、可忽略。分配热点在内部,改内部即可拿下几乎全部收益。

---

## 1. 表示法

一个金额/数量的真实值 `v` 用整数 `raw` 表示:

```
raw = round(v × 10^scale)      // scale = 该资产的最小精度位数
v   = raw / 10^scale
```

- `scale` **按资产**取值(来自 `AssetScaleRegistry`)。
- `raw` 类型 `long`(63 位有效)。scale=8 时可表示到 ±9.2×10^10 个单位,覆盖 BTC/USDT/绝大多数币。
- **同一资产的两个 raw 必定同 scale**,故加减比较是纯 long 运算,无需换算。
- 跨资产(撮合 price×qty→amount)才需要 scale 换算,见 §4。

### 1.1 溢出策略:fail-closed

所有 long 加减用 `Math.addExact / Math.subtractExact`,溢出抛异常而非静默回绕。资金宁可拒绝也不可错账。乘法走 128 位中间量(§4),不会中途溢出;最终落 long 前做范围检查,超界抛异常。

---

## 2. 按币种 scale 的确定性(Raft 状态机的关键约束)

状态机里 `raw` 是裸整数,**它代表多少真实金额完全取决于该资产的 scale**。若两个副本对同一资产用了不同 scale,同一条日志重放会得到不同真实余额 → 状态分裂 → 灾难。

因此 `AssetScaleRegistry` 必须满足:

1. **不可变**:进程启动后 scale 表只读,运行期不改。
2. **各副本一致**:scale 表是部署制品的一部分(同一份配置随 jar 一起分发),不依赖运行期外部查询。
3. **fail-closed**:遇到未登记 scale 的资产 → **抛异常拒绝该笔请求**,绝不用默认值兜底。默认值会让不同版本配置的副本静默产生不同 raw。
4. **可检测**:快照写入时附带 `assetScales` 指纹(asset→scale 映射),加载时若与本地注册表冲突则拒绝启动并告警。

> scale 变更(给某资产提高精度)= 一次协调的停机迁移(全量按新 scale 重算 raw + 换快照),不能热改。这是刻意的:金额精度是账务根基。

---

## 3. 阶段一:资产账本(纯加减,低风险)

`BalanceLedger` / `Balance` 只有 `add / subtract / compareTo`,**零乘除、零舍入决策**。改造:

- `Balance.available/frozen`: `BigDecimal` → `long`(该资产 scale 下的 raw)。
- `freeze/unfreeze/deductFrozen/credit/debit/total`: `BigDecimal` 运算 → `Math.addExact/subtractExact` + `<`/`>` 比较。
- `Balance` 本身不持有 asset;scale 由 `BalanceLedger`(知道 asset key)在**入口转换**时应用。`settleTrade` 的 `qty` 是 base 资产 raw,`quoteAmt/buyFee/sellFee` 是 quote 资产 raw——全部由撮合侧预先算好传入,账本只在各自资产 scale 内加减,无跨 scale 运算。
- 幂等表、seq、matchArchivePosition 不变。
- **边界转换**:
  - 入口(`AssetClusteredService` 各 handler):把 DTO 的 `BigDecimal amount` 按 `scale(asset)` 转 `long raw` 后再进账本。
  - 出口(`AssetStateChangeEvent`、状态查询):账本 `long raw` → `BigDecimal`(供 persist 落 DB、供查询展示)。事件在热 IPC 路径上,优先直接携带 `long raw + asset`(由 scale 还原),persist 侧转 `BigDecimal` 落库。
- **快照**:`Balance` 的 long 直接序列化(比 BigDecimal 更省更快)。

风险:低。无舍入、无溢出(余额量级远小于 9.2e18)。

---

## 4. 阶段二:撮合引擎(乘除,高风险)

### 4.1 涉及点

| 位置 | 运算 | 说明 |
|---|---|---|
| `Trade.calculateAmount` | `amount = price × quantity` | 跨 scale 乘法 |
| `Trade.calculateFees` | `fee = amount × feeRate` | 定点 × 费率 |
| `Symbol.calculateFee` | `tradeValue × feeRate` | 同上 |
| `MatchEngineServiceImpl` | `matchPrice = Σamount / Σqty`、`avgCost`、`PnL = qty × Δprice` | 带舍入除法 + 乘法 |
| `OrderBook` | `TreeMap<BigDecimal price>` → `TreeMap<Long priceRaw>` | 比较器改为 long 自然序 |
| `Order` | `price/quantity/filled/remaining/leverage` | 字段 long 化 |

### 4.2 跨 scale 乘法(核心难点)

`amount_raw`(quote scale `Sa=10^sa`)由 `price_raw`(scale `sp`)、`qty_raw`(scale `sq`)得:

```
amount_raw = price_raw × qty_raw × 10^sa / (10^sp × 10^sq)
           = mulDiv128(price_raw, qty_raw, 10^sa, 10^(sp+sq), rounding)
```

`price_raw × qty_raw` 可达 ~1e18×1e18,**必超 long**,故用 128 位中间量:`Math.multiplyHigh` 取高 64 位 + 低 64 位组成 unsigned 128,再做 128÷64 带余除法与舍入。封装为 `FixedPoint.mulDiv(a, b, num, den, RoundingMode)`。

### 4.3 费率与除法

- `feeRate` 用定点表示(`feeScale`,如 1e-8):`fee_raw = mulDiv(amount_raw, feeRateRaw, 1, 10^feeScale, rounding)`。
- 均价/PnL 除法:`mulDiv(a, b, num, den, rounding)` 统一处理,舍入模式沿用现有 `HALF_UP`(quantityPrecision 场景用 `DOWN`,与旧 `setScale(qtyPrec, ROUND_DOWN)` 一致)。
- **舍入策略集中定义、逐点标注**,并在对拍测试里锁死。

### 4.4 确定性附注

`Trade` 构造函数用了 `LocalDateTime.now()`(wall-clock)——状态机确定性隐患,本次顺带改为由 cluster timestamp 注入(不读挂钟)。

---

## 5. 边界不变量汇总

| 层 | 类型 | 是否变 |
|---|---|---|
| 内存状态机(Balance/Order/OrderBook/持仓) | `long` raw | **变** |
| 撮合乘除 | `long` + 128 位中间量 | **变** |
| 服务间热事件(AssetStateChange/结算流) | `long` raw + asset/symbol | **变** |
| 对外 HTTP DTO(JSON) | `BigDecimal` | 不变(边界转换) |
| DB 列(t_user_asset/t_fund_flow) | `DECIMAL/BigDecimal` | 不变(persist 转换) |

---

## 6. 测试与灰度

1. **对拍(oracle)测试**:随机生成大量 price/qty/rate/余额序列,同一序列分别用「旧 BigDecimal 路径」与「新定点 long 路径」执行,**逐笔比对**余额、成交金额、手续费、均价、PnL,断言零偏差(在共同 scale/舍入下)。这是敢上线的前提。
2. **边界用例**:最小精度尾数、最大量级(逼近 9.2e18)、费率为 0、单位数量、价格穿透多档。
3. **快照往返**:long 快照 dump→load 值稳定;`assetScales` 指纹校验。
4. **确定性重放**:同一日志两次重放得到同一 long 状态。
5. 全量 `mvn install` 编译通过。

---

## 7. 风险排序与顺序

| 步骤 | 风险 | 说明 |
|---|---|---|
| FixedPoint 工具 + AssetScaleRegistry | 中 | 128 位 mulDiv 是全局正确性根基,单测覆盖优先 |
| 阶段一 账本 | 低 | 纯加减,边界转换 |
| 阶段二 撮合 | 高 | 乘除+舍入+订单簿比较器,需对拍 |
| 边界/persist/事件 | 中 | 转换点集中,易回归 |

实现顺序:工具 → 账本 → 对拍(账本) → 撮合 → 对拍(撮合) → 全量编译。

---

## 8. 阶段二执行规范(热路径方案,Option Y)——逐文件

> 范围已定:**撮合内部状态用 long,对外输出 DTO 仍 BigDecimal**;冷配置(Symbol 滑点/tickSize/风控/杠杆/min-max)留 BigDecimal。改动收敛在 match-core 内部,不触碰 match-api 的 Trade/MatchResponse、结算 forwarder、资产侧、下单 DTO 的对外契约。

### 8.1 边界决策:为什么 Trade/MatchResponse 留 BigDecimal

`Trade`/`MatchResponse` 在 match-api,经结算流跨到资产服务。若 Trade 直接携带 raw long,资产侧解读 amount 就必须和撮合用**完全一致**的 quote 资产 scale → 跨服务 scale 强耦合,任一侧配置漂移即错账。故:

- 撮合**内部**(Order/OrderBook/matcher 工作值、amount/fee/均价/PnL 计算)全部 long + `FixedPoint.mulDiv`。
- 生成 `Trade`/`MatchResponse` 时用 `toBd(raw, scale)` 转回 BigDecimal 输出。
- 资产侧 `handleSettle` 维持按 BigDecimal 字符串解析(§3 已实现的 `toRaw`),契约不变。
- 收益:消除撮合热循环里的 BigDecimal 中间量分配;Trade 一笔一次的 BigDecimal 属于冷输出,可接受。

### 8.2 scale 来源(撮合内部)

| 值 | scale | 来源 |
|---|---|---|
| price / filled 价格档 | `priceScale` | `Symbol.pricePrecision` |
| quantity / filled / remaining | `baseScale` | `Symbol.quantityPrecision` |
| amount / fee(内部计算中间量) | `quoteScale` | quote 资产精度(match 侧 `AssetScaleRegistry.scaleOf(quoteCurrency)`,与资产服务同一份 scale 表) |

`amount_raw = mulDiv(price_raw, qty_raw, 10^(priceScale+baseScale−quoteScale), HALF_UP)`;
`fee_raw    = mulDiv(amount_raw, feeRateRaw, 10^feeScale, HALF_UP)`(feeRate 以 feeScale=8 定点)。

### 8.3 逐文件改动

1. **`Order`**:price/quantity/filledQuantity/remainingQuantity/leverage → `long`(price@priceScale,量@baseScale,leverage@固定 8)。`canMatch/updateFilledQuantity/getFillRate/isFullyFilled/isPartiallyFilled` 改 long 比较/加减;`getFillRate` 用 `mulDiv`。**顺带修**:构造函数与 `updateFilledQuantity` 的 `LocalDateTime.now()` → 由外部传入 cluster timestamp(状态机确定性)。
2. **`OrderBook`**:`NavigableMap<BigDecimal,…>` → `NavigableMap<Long,…>`(买单仍 `reverseOrder()`);`lastPrice/highPrice/lowPrice`→long(priceScale),`volume24h`→long(baseScale);`getBestBid/Ask`、深度、`PriceLevel`、`OrderBookSnapshot` 相应 long 化;对外行情查询在 controller 边界 `toBd`。
3. **matcher(`AbstractOrderMatcher`/`Market`/`Limit`)**:价格比较、`matchQuantity=min`、`createTrade` 全 long;`createTrade` 内 amount/fee 用 §8.2 公式算 long,再 `toBd` 填入 `Trade`。
4. **`Symbol`**:`feeRate` 增加 `feeRateRaw`(long@feeScale)供撮合用;`calculateFee` 保留 BigDecimal 版给冷路径,新增 long 版。tickSize/滑点/风控/min-max **不动**(冷)。
5. **`MatchEngineServiceImpl`**:`matchPrice=Σamount/Σqty`、`avgCost`、`PnL` 用 `mulDiv`(变量除数已对拍验证);持仓均价/张数 long 化;与 Symbol 冷配置(滑点/tickSize)交互处做 `toBd`/`toRaw` 边界转换。
6. **下单入场**(`EventNewOrderReq`→`Order`):BigDecimal → long,按 symbol 的 price/base scale `toRaw`(精度超限 fail-closed 拒单)。
7. **持仓/强平模型**:涉及金额/数量字段 long 化(quote/base scale),PnL/保证金用 `mulDiv`。

### 8.4 对拍(收口前必须)

新增 match 侧 oracle 测试:随机 price/qty/feeRate/多档序列,「旧 BigDecimal 撮合」vs「定点 long 撮合」**逐笔比对** amount/fee/matchPrice/avgCost/PnL 与最终成交序列,断言零偏差后方可合入。

### 8.5 执行方式建议

因涉及资金+行情核心且类型改动无处不在(任何中间态不可编译),建议按 8.3 的 1→7 顺序**分块编译推进**(每块 `mvn -pl exchange-match/... test-compile` 过了再下一块),而非一次性大改。基础 `FixedPoint.mulDiv` 已对拍(10 的幂除数 86 万 + 任意除数 12.8 万,零偏差),数学层已可信。
