# 模块依赖审计报告

> 范围:exchange 全部 11 个业务模块 + framework/common
> 目标:验证「服务间只通过 `-api` 耦合,不直接依赖别人的 `-core`」
> 结论:**整体健康,只有 1 处真实跨服务违规**

---

## 1. 总体结论

扫描所有模块 pom 的内部依赖后,分层情况**出乎意料地干净**:

| 模块 | 依赖的内部模块 | 评价 |
|---|---|---|
| user / quote / risk / order / message / admin 的 `-core` | 仅自己的 `-api` | ✅ 合规 |
| 上述服务的 `-web` | 自己的 `-core` + `framework-web` | ✅ 合规(同服务内) |
| match-core | match-api + framework-transport | ✅ 合规 |
| **account-core** | account-api + **match-core** | ❌ **跨服务依赖别人的 core** |
| account-persist | account-core | ⚠️ 同服务内,但耦合了部署单元 |

除 account 外,**每个服务的 core 都只依赖自己的 api**——这是很好的基线。问题集中在一处。

> `framework-transport` / `framework-web` / `exchange-common` 是共享基础设施(类比 JDK),被依赖不算违规。

---

## 2. 唯一的跨服务违规:account-core → match-core

### 2.1 事实

`account-core` 的 `TradeSettlementForwarder` 直接引用了撮合的**内部模型**:

```java
import com.exchange.match.core.model.MatchResponse;   // 在 match-core
import com.exchange.match.core.model.Trade;           // 在 match-core
```

后果就是这次编译时你亲眼看到的:**match-core 一旦不编译,account-core 直接被卡死**——两个本应独立的服务被 core 级依赖绑死了。

### 2.2 根因

撮合的 `com.exchange.match.core.model` 包**混装了两类东西**:

- **跨服务传输对象**:`Trade`、`MatchResponse`(成交结果要发给资产/风控/行情)
- **引擎内部领域模型**:`Order`、`OrderBook`、`Position`、以及一堆枚举

它们共享同一批枚举(`OrderSide` / `OrderType` / `MatchStatus` / `PositionSide` / `PositionAction` / `TradeSide`),所以传输对象和内部模型纠缠在一起,无法单独拿出来放进 api。

### 2.3 account-core 实际用到的字段(关键:表面很小)

`TradeSettlementForwarder` 对撮合模型的**全部**使用:

```
response.getTrades()                              // List<Trade>
trade.getTradeId() / getSymbol()
     .getBuyUserId() / getSellUserId()
     .getQuantity() / getAmount()
     .getBuyFee() / getSellFee()                  // 共 8 个字段
```

也就是说,跨服务契约本质上就是**「一批成交,每笔 8 个字段」**。而 `Trade` 实际有 17 个字段、`MatchResponse` 有 20+ 字段和 3 个嵌套类(`PositionChange`/`CancelInfo`/`RejectInfo`)——account 用不到的占绝大多数。

---

## 3. 重构方案(三选一)

### 方案 A —— 把 Trade + MatchResponse 整体下沉到 match-api

把传输模型物理移动到 `match-api`。

| 项 | 成本 |
|---|---|
| 连带下沉 | `Trade` + `MatchResponse` + 6 个共享枚举(否则编译不过) |
| 波及文件 | match-core 内引用 `Trade`/`MatchResponse` 各 **14 个文件** + 全部用到那 6 个枚举的文件(Order/OrderBook/所有 matcher) |
| 风险 | 高——枚举被引擎核心大量引用,包名一变牵一发动全身 |
| 收益 | 最彻底,api 成为唯一契约 |

**不推荐**:因为那 6 个枚举同时是引擎内部模型的一部分,下沉等于把半个 domain model 搬进 api,得不偿失。

### 方案 B —— 在 match-api 新建精简契约 DTO(推荐)

不动 match-core 内部,在 `match-api` 新建**只含跨服务字段**的 DTO:

```java
// match-api: com.exchange.match.api.dto
public class TradeDTO {           // 8 个字段,正好覆盖结算所需
    String tradeId, symbol;
    Long buyUserId, sellUserId;
    BigDecimal quantity, amount, buyFee, sellFee;
}
public class MatchResultDTO {     // 跨服务成交结果
    List<TradeDTO> trades;
}
```

- **撮合侧**:`AeronMatchResultPublisher` 把内部 `MatchResponse` 映射成 `MatchResultDTO` 再发 MDC(加一个 30 行的 mapper)
- **资产侧**:`TradeSettlementForwarder` 依赖改为 `match-api`,反序列化 `MatchResultDTO`
- **account-core 的 pom**:`match-core` → `match-api`

| 项 | 成本 |
|---|---|
| 新增 | 2 个 DTO + 1 个 mapper(撮合侧) |
| 改动 | account-core pom 1 行 + TradeSettlementForwarder 类型替换;撮合侧发布器加映射 |
| 波及 | 局部,不碰引擎内部和枚举 |
| 风险 | 低 |
| 收益 | 契约清晰、字段最小化、两服务彻底解耦 |

**推荐**:成本可控,且顺带修正了「资产拿到一堆用不到的字段」的坏味道。

### 方案 C —— 暂时接受,记为技术债

把 account-core → match-core 标注为已知债务,不动。适用于「先跑起来、以后再说」。缺点:两个服务的构建/发布仍然绑死,本次的连锁编译失败会复现。

---

## 4. 次要观察:account-persist → account-core

`account-persist` 依赖了 `account-core`,用到:

- `AssetService` / `AssetServiceImpl`(DB 服务)
- `AeronArchiveEventPublisher`(**集群侧**的类,只为复用 channel/stream 常量)
- `AssetPersistService`

这**不是跨服务违规**(persist 和 core 同属 exchange-account),但有两个小问题:

1. persist 是独立部署单元(消费 Archive 落库),却因依赖 core 而被迫携带 Aeron 集群、状态机等一大堆它用不到的代码。
2. 复用 `AeronArchiveEventPublisher` 的常量属于「为了两个常量拉进整个类」。

**建议**(低优先级):把 persist 和 core 共享的常量(RECORDING_CHANNEL / RECORDING_STREAM)、以及 `AssetStateChangeEvent`(已在 account-api)沉到 `account-api`,让 persist 只依赖 `account-api`。

---

## 5. 建议的落地顺序

| # | 事项 | 优先级 | 说明 |
|---|---|---|---|
| 0 | **先提交当前编译通过的成果**(尤其 `exchange-parent/pom.xml`) | 🔴 立刻 | 防止 parent 再次丢失 |
| 1 | 方案 B:match-api 建 TradeDTO/MatchResultDTO,account-core 改依赖 match-api | 🟡 建议 | 消除唯一的跨服务违规 |
| 2 | 常量/事件下沉 account-api,account-persist 只依赖 account-api | 🟢 可选 | 让 persist 部署更轻 |

---

## 6. 一句话总结

架构基线是好的——**11 个服务里 10 个的 core 都只依赖自己的 api**。唯一的破口是 `account-core` 直接引了 `match-core` 的两个传输类(`Trade`/`MatchResponse`),根因是撮合把「传输 DTO」和「引擎内部模型」混在同一个 model 包。用**方案 B**(match-api 建精简契约 DTO)可低风险修掉,顺带把跨服务字段收敛到真正需要的 8 个。
