package com.exchange.account.core.cluster.ledger;

import com.exchange.account.api.enums.AccountType;
import com.exchange.common.event.CoreSystemEvent;
import com.exchange.common.event.SystemEventReporter;
import com.exchange.common.id.SnowflakeId;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 内存资产账本（Asset Service 的核心状态机）。
 *
 * <h3>账本结构</h3>
 * <pre>
 *   userId → accountType → asset → Balance
 * </pre>
 * <p>每个用户下按 {@link AccountType} 完全隔离：
 * 同一用户的 SPOT-USDT 与 FUTURES-USDT 是独立余额，
 * 互不影响，跨账户转账需通过专用 Transfer 操作完成。
 *
 * <h3>线程安全</h3>
 * <p>Aeron Cluster Service Thread 单线程模型，所有操作顺序调用，无需额外同步。
 *
 * <h3>幂等设计</h3>
 * <p>每个操作携带 {@code bizNo}（orderId / tradeId）用于幂等检查；
 * 已处理过的 bizNo 存于 {@code processedBizNos}，防止重复结算。
 *
 * <h3>processedBizNos 有界增长（TTL 淘汰）</h3>
 * <p>{@code Map<String, Long>}，value = 该条目的过期时间（epoch ms）。
 * 过期时间由 bizNo 的 Snowflake 时间戳 + {@link #BIZNO_TTL_MS} 推导；
 * 非 Snowflake 格式则 fallback 到 clusterTimestamp。
 * {@link com.exchange.account.core.cluster.AssetClusteredService} 每分钟通过
 * {@link #evictExpiredBizNos(long)} 驱逐过期条目。
 */
@Slf4j
public class BalanceLedger {

    /** processedBizNos 条目的生存时间：30 分钟。 */
    public static final long BIZNO_TTL_MS = 30L * 60L * 1_000L;

    /**
     * 系统事件上报器(仅观测,不参与状态机判定)。
     *
     * <p>计数是节点本地的观测数据,不入快照、不影响任何分支判断,
     * 因此各副本计数值不同不构成状态分歧。
     */
    private final SystemEventReporter eventReporter;

    /** 默认构造(测试用):事件只计数不输出。 */
    public BalanceLedger() {
        this(SystemEventReporter.noop());
    }

    public BalanceLedger(SystemEventReporter eventReporter) {
        this.eventReporter = eventReporter != null ? eventReporter : SystemEventReporter.noop();
    }

    /**
     * 主账本：userId → accountType → asset → Balance。
     * 普通 HashMap（在 Cluster Service Thread 上单线程访问）。
     */
    private final Map<Long, Map<AccountType, Map<String, Balance>>> ledger = new HashMap<>();

    /**
     * 幂等记录：bizNo key → 过期时间（epoch ms）。
     * key 格式："{PREFIX}:{orderId|tradeId}"。
     */
    private final Map<String, Long> processedBizNos = new HashMap<>();

    /**
     * 永久幂等记录（不参与 TTL 驱逐）：CREDIT / DEBIT 专用。
     *
     * <p>加钱/减钱的 bizNo 来自外部系统（depositId / withdrawId），
     * 外部重试/对账窗口可能远超 30 分钟——TTL 幂等表过期后同一 bizNo
     * 重发会导致<b>重复加钱/扣钱</b>。因此这类操作的幂等键永不驱逐，
     * 随 Snapshot 持久化，跨重启有效。
     *
     * <p>容量：充提频次远低于交易，长期增长可接受；
     * 后续可按业务归档策略（如已终态 N 年以上）离线清理。
     */
    private final java.util.Set<String> permanentBizNos = new java.util.HashSet<>();

    /**
     * Match Archive 已消费位点（byte position）。
     * 与账本一同持久化在 Raft Snapshot 中，确保"结算已提交"与"位点记录"的原子性。
     */
    private long matchArchivePosition = 0L;

    /**
     * 资金流水全局自增序号（状态机产出编号）。
     *
     * <h3>语义</h3>
     * <p>「每产出一条资金流水记录消耗一个 seq，且仅当实际发生状态变更时消耗」。
     * 幂等命中跳过、余额不足抛异常均不消耗。当前值即"自创世以来产出的流水总数"。
     *
     * <h3>确定性</h3>
     * <p>seq 严格在本类（账本状态机）内部自增，所有变更方法都运行在
     * <b>所有副本</b>的 Cluster Service Thread 上，因此各副本 seq 完全一致。
     * <b>绝不可</b>把 seq 自增放到 Leader-only 的事件发布分支——那会导致
     * Follower 计数落后，Leader 切换后主键错乱。
     *
     * <p>随 Snapshot 持久化，重启后从快照值继续，不会回退或跳号。
     *
     * <p>本轮 seq 仅用于运行时观测（状态查询接口）；后续接入
     * {@code t_fund_flow} 主键与事件丢失检测时，再将 seq 逐条写入事件。
     */
    private long seq = 0L;

    // =========================================================================
    // 内嵌值对象
    // =========================================================================

    /**
     * 批量冻结的单个子项。
     *
     * @param orderId     关联订单 ID（幂等键）
     * @param accountType 账户类型
     * @param asset       资产代码
     * @param amount      冻结金额
     */
    public record FreezeItem(String orderId, AccountType accountType, String asset, BigDecimal amount) {}

    // =========================================================================
    // 查询
    // =========================================================================

    /**
     * 获取用户某账户类型下某资产余额，不存在时返回全零余额（不自动创建）。
     */
    public Balance getBalance(Long userId, AccountType accountType, String asset) {
        Map<AccountType, Map<String, Balance>> byType = ledger.get(userId);
        if (byType == null) return new Balance();
        Map<String, Balance> byAsset = byType.get(accountType);
        if (byAsset == null) return new Balance();
        Balance b = byAsset.get(asset);
        return b != null ? b.copy() : new Balance();
    }

    /**
     * 获取用户某账户类型下所有资产余额的快照副本（只读）。
     */
    public Map<String, Balance> getAllBalances(Long userId, AccountType accountType) {
        Map<AccountType, Map<String, Balance>> byType = ledger.get(userId);
        if (byType == null) return Collections.emptyMap();
        Map<String, Balance> byAsset = byType.get(accountType);
        if (byAsset == null) return Collections.emptyMap();
        Map<String, Balance> copy = new HashMap<>();
        byAsset.forEach((asset, bal) -> copy.put(asset, bal.copy()));
        return copy;
    }

    /**
     * 获取用户所有账户类型下所有资产余额的快照副本（只读）。
     * key: accountType → (asset → Balance)
     */
    public Map<AccountType, Map<String, Balance>> getAllBalancesByType(Long userId) {
        Map<AccountType, Map<String, Balance>> byType = ledger.get(userId);
        if (byType == null) return Collections.emptyMap();
        Map<AccountType, Map<String, Balance>> copy = new HashMap<>();
        byType.forEach((type, assets) -> {
            Map<String, Balance> assetCopy = new HashMap<>();
            assets.forEach((asset, bal) -> assetCopy.put(asset, bal.copy()));
            copy.put(type, assetCopy);
        });
        return copy;
    }

    // =========================================================================
    // 冻结 / 解冻
    // =========================================================================

    /**
     * 冻结资产（下单时调用）。
     *
     * @param userId           用户 ID
     * @param accountType      账户类型
     * @param asset            资产代码（如 USDT）
     * @param amount           冻结金额
     * @param orderId          关联订单 ID（幂等键，Snowflake 格式）
     * @param clusterTimestamp Cluster 消息时间戳（TTL fallback）
     * @throws IllegalStateException 可用余额不足
     */
    public void freeze(Long userId, AccountType accountType, String asset,
                       BigDecimal amount, String orderId, long clusterTimestamp) {
        String key = "FREEZE:" + orderId;
        if (isProcessed(key)) {
            reportIdempotentHit("FREEZE", orderId, clusterTimestamp);
            log.debug("[BalanceLedger] Duplicate FREEZE ignored — orderId={}", orderId);
            return;
        }
        Balance balance = getOrCreate(userId, accountType, asset);
        balance.freeze(amount);
        markProcessed(key, orderId, clusterTimestamp);
        advanceSeq(1);   // 1 条 FREEZE 流水
        log.debug("[BalanceLedger] FREEZE userId={} accountType={} asset={} amount={} orderId={}",
                userId, accountType, asset, amount, orderId);
    }

    /**
     * 解冻资产（撤单时调用）。
     */
    public void unfreeze(Long userId, AccountType accountType, String asset,
                         BigDecimal amount, String orderId, long clusterTimestamp) {
        String key = "UNFREEZE:" + orderId;
        if (isProcessed(key)) {
            reportIdempotentHit("UNFREEZE", orderId, clusterTimestamp);
            log.debug("[BalanceLedger] Duplicate UNFREEZE ignored — orderId={}", orderId);
            return;
        }
        Balance balance = getOrCreate(userId, accountType, asset);
        balance.unfreeze(amount);
        markProcessed(key, orderId, clusterTimestamp);
        advanceSeq(1);   // 1 条 UNFREEZE 流水
        log.debug("[BalanceLedger] UNFREEZE userId={} accountType={} asset={} amount={} orderId={}",
                userId, accountType, asset, amount, orderId);
    }

    // =========================================================================
    // 成交结算
    // =========================================================================

    /**
     * 处理一笔成交结算，采用"先整体校验，后原子执行"模式。
     *
     * <p>买卖双方在同一 {@code accountType} 下结算（现货 → SPOT，合约 → FUTURES）。
     *
     * <pre>
     * 现货结算逻辑（以 BTC/USDT 为例）：
     *   买方：USDT frozen -= (quoteAmt + buyFee)；BTC available += qty
     *   卖方：BTC  frozen -= qty；USDT available += (quoteAmt - sellFee)
     * </pre>
     */
    public void settleTrade(Long buyerId, Long sellerId,
                            AccountType accountType,
                            String baseAsset, String quoteAsset,
                            BigDecimal qty, BigDecimal quoteAmt,
                            BigDecimal buyFee, BigDecimal sellFee,
                            String tradeId, long clusterTimestamp) {
        String key = "SETTLE:" + tradeId;
        if (isProcessed(key)) {
            reportIdempotentHit("SETTLE", tradeId, clusterTimestamp);
            log.debug("[BalanceLedger] Duplicate SETTLE ignored — tradeId={}", tradeId);
            return;
        }

        // Phase 1: 整体预校验
        BigDecimal buyerCost = quoteAmt.add(buyFee);
        Balance buyerQuote   = getOrCreate(buyerId,  accountType, quoteAsset);
        Balance sellerBase   = getOrCreate(sellerId, accountType, baseAsset);

        if (buyerQuote.getFrozen().compareTo(buyerCost) < 0) {
            throw new IllegalStateException(String.format(
                    "[SETTLE] Buyer insufficient frozen quoteAsset: userId=%d accountType=%s asset=%s " +
                    "frozen=%s required=%s tradeId=%s",
                    buyerId, accountType, quoteAsset, buyerQuote.getFrozen(), buyerCost, tradeId));
        }
        if (sellerBase.getFrozen().compareTo(qty) < 0) {
            throw new IllegalStateException(String.format(
                    "[SETTLE] Seller insufficient frozen baseAsset: userId=%d accountType=%s asset=%s " +
                    "frozen=%s required=%s tradeId=%s",
                    sellerId, accountType, baseAsset, sellerBase.getFrozen(), qty, tradeId));
        }

        // Phase 2: 原子执行
        buyerQuote.deductFrozen(buyerCost);
        getOrCreate(buyerId,  accountType, baseAsset).credit(qty);
        sellerBase.deductFrozen(qty);
        getOrCreate(sellerId, accountType, quoteAsset).credit(quoteAmt.subtract(sellFee));

        markProcessed(key, tradeId, clusterTimestamp);
        advanceSeq(4);   // 4 条流水：买扣/买入/卖扣/卖入
        log.debug("[BalanceLedger] SETTLE tradeId={} accountType={} buyer={} seller={} qty={} quoteAmt={}",
                tradeId, accountType, buyerId, sellerId, qty, quoteAmt);
    }

    // =========================================================================
    // 加钱 / 减钱 / 内部划转
    // =========================================================================

    /**
     * 加钱（直接增加可用余额）。
     *
     * <p>适用场景：充值到账、奖励发放、活动补偿等。
     * {@code bizNo} 填充外部单号（depositId / rewardId），幂等去重。
     *
     * @param userId           用户 ID
     * @param accountType      目标账户类型
     * @param asset            资产代码
     * @param amount           金额（必须 > 0）
     * @param bizNo            业务单号（幂等键）
     * @param clusterTimestamp Cluster 消息时间戳
     */
    public void credit(Long userId, AccountType accountType, String asset,
                       BigDecimal amount, String bizNo, long clusterTimestamp) {
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalStateException("[CREDIT] amount must be positive: " + amount);
        }
        // 永久幂等（不走 TTL 表）：外部单号重发无时间窗口限制
        String key = "CREDIT:" + bizNo;
        if (!permanentBizNos.add(key)) {
            reportIdempotentHit("CREDIT", bizNo, clusterTimestamp);
            log.debug("[BalanceLedger] Duplicate CREDIT ignored — bizNo={}", bizNo);
            return;
        }
        getOrCreate(userId, accountType, asset).credit(amount);
        advanceSeq(1);   // 1 条 CREDIT 流水
        log.debug("[BalanceLedger] CREDIT userId={} accountType={} asset={} amount={} bizNo={}",
                userId, accountType, asset, amount, bizNo);
    }

    /**
     * 减钱（直接减少可用余额）。
     *
     * <p>适用场景：提现扣款、风控扣罚等。不经过冻结流程，直接扣减 available。
     * {@code bizNo} 填充外部单号（withdrawId），幂等去重。
     *
     * @throws IllegalStateException 可用余额不足
     */
    public void debit(Long userId, AccountType accountType, String asset,
                      BigDecimal amount, String bizNo, long clusterTimestamp) {
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalStateException("[DEBIT] amount must be positive: " + amount);
        }
        // 永久幂等（不走 TTL 表）：外部单号重发无时间窗口限制
        String key = "DEBIT:" + bizNo;
        if (permanentBizNos.contains(key)) {
            reportIdempotentHit("DEBIT", bizNo, clusterTimestamp);
            log.debug("[BalanceLedger] Duplicate DEBIT ignored — bizNo={}", bizNo);
            return;
        }
        Balance balance = getOrCreate(userId, accountType, asset);
        if (balance.getAvailable().compareTo(amount) < 0) {
            throw new IllegalStateException(String.format(
                    "[DEBIT] Insufficient available: userId=%d accountType=%s asset=%s available=%s required=%s bizNo=%s",
                    userId, accountType, asset, balance.getAvailable(), amount, bizNo));
        }
        balance.debit(amount);
        // 执行成功后才记录（余额不足抛出时不占用幂等键，允许补足后重试同一单号）
        permanentBizNos.add(key);
        advanceSeq(1);   // 1 条 DEBIT 流水
        log.debug("[BalanceLedger] DEBIT userId={} accountType={} asset={} amount={} bizNo={}",
                userId, accountType, asset, amount, bizNo);
    }

    /**
     * 同用户跨账户类型内部划转（原子）。
     *
     * <p>在单次 Raft 日志写入中完成出账和入账，不存在中间状态。
     * {@code fromType} 和 {@code toType} 不能相同。
     *
     * @param userId           用户 ID
     * @param fromType         出账账户类型
     * @param toType           入账账户类型
     * @param asset            资产代码
     * @param amount           划转金额（必须 > 0）
     * @param bizNo            划转单号（幂等键，Snowflake 格式）
     * @param clusterTimestamp Cluster 消息时间戳
     * @throws IllegalStateException 出账账户余额不足 / fromType == toType
     */
    public void internalTransfer(Long userId, AccountType fromType, AccountType toType,
                                 String asset, BigDecimal amount, String bizNo, long clusterTimestamp) {
        if (fromType == toType) {
            throw new IllegalStateException("[TRANSFER] fromType and toType must differ: " + fromType);
        }
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalStateException("[TRANSFER] amount must be positive: " + amount);
        }
        String key = "TRANSFER:" + bizNo;
        if (isProcessed(key)) {
            reportIdempotentHit("TRANSFER", bizNo, clusterTimestamp);
            log.debug("[BalanceLedger] Duplicate TRANSFER ignored — bizNo={}", bizNo);
            return;
        }
        Balance from = getOrCreate(userId, fromType, asset);
        if (from.getAvailable().compareTo(amount) < 0) {
            throw new IllegalStateException(String.format(
                    "[TRANSFER] Insufficient available: userId=%d from=%s asset=%s available=%s required=%s bizNo=%s",
                    userId, fromType, asset, from.getAvailable(), amount, bizNo));
        }
        // 原子执行：先扣出账，再增入账
        from.debit(amount);
        getOrCreate(userId, toType, asset).credit(amount);
        markProcessed(key, bizNo, clusterTimestamp);
        advanceSeq(2);   // 2 条流水：TRANSFER_OUT + TRANSFER_IN
        log.debug("[BalanceLedger] TRANSFER userId={} from={} to={} asset={} amount={} bizNo={}",
                userId, fromType, toType, asset, amount, bizNo);
    }

    // =========================================================================
    // 批量操作
    // =========================================================================

    /**
     * 批量冻结（单用户，整体原子）。
     *
     * <h3>原子性</h3>
     * <p>采用"汇总预校验 → 批量执行"两阶段；任一资产不足则整批抛异常，不修改任何状态。
     *
     * @return 本次实际执行冻结的子项列表（幂等跳过的不在其中）
     */
    public List<FreezeItem> batchFreeze(Long userId, List<FreezeItem> items,
                                        long clusterTimestamp) {
        // Phase 1: 过滤幂等 + 按 (accountType, asset) 汇总所需金额
        Map<String, BigDecimal> requiredPerKey = new HashMap<>(); // "TYPE:asset" → total
        List<FreezeItem> toProcess = new ArrayList<>();

        for (FreezeItem item : items) {
            String key = "FREEZE:" + item.orderId();
            if (isProcessed(key)) {
                reportIdempotentHit("BATCH_FREEZE", item.orderId(), clusterTimestamp);
                log.debug("[BalanceLedger] BATCH_FREEZE item skipped (dup) — orderId={}", item.orderId());
                continue;
            }
            toProcess.add(item);
            String mapKey = item.accountType().name() + ":" + item.asset();
            requiredPerKey.merge(mapKey, item.amount(), BigDecimal::add);
        }

        if (toProcess.isEmpty()) {
            log.debug("[BalanceLedger] BATCH_FREEZE all items already processed — userId={}", userId);
            return Collections.emptyList();
        }

        // Phase 2: 整体预校验
        for (Map.Entry<String, BigDecimal> entry : requiredPerKey.entrySet()) {
            String[] parts      = entry.getKey().split(":", 2);
            AccountType type    = AccountType.valueOf(parts[0]);
            String asset        = parts[1];
            BigDecimal required = entry.getValue();
            Balance balance     = getOrCreate(userId, type, asset);

            if (balance.getAvailable().compareTo(required) < 0) {
                throw new IllegalStateException(String.format(
                        "[BATCH_FREEZE] Insufficient available %s/%s: userId=%d available=%s required=%s",
                        type, asset, userId, balance.getAvailable(), required));
            }
        }

        // Phase 3: 原子执行
        for (FreezeItem item : toProcess) {
            Balance balance = getOrCreate(userId, item.accountType(), item.asset());
            balance.freeze(item.amount());
            markProcessed("FREEZE:" + item.orderId(), item.orderId(), clusterTimestamp);
        }
        advanceSeq(toProcess.size());   // 每个实际执行的 item 产出 1 条 FREEZE 流水
        log.debug("[BalanceLedger] BATCH_FREEZE OK userId={} items={}", userId, toProcess.size());
        return toProcess;
    }

    /**
     * 批量结算（多用户对）。
     *
     * <p>每笔 trade 的 map 中须包含 {@code accountType}（{@link AccountType} 枚举名，默认 SPOT）。
     */
    public void batchSettle(List<Map<String, Object>> trades, long clusterTimestamp) {
        for (Map<String, Object> t : trades) {
            String tradeId = (String) t.get("tradeId");
            AccountType accountType = AccountType.valueOf(
                    (String) t.getOrDefault("accountType", AccountType.SPOT.name()));
            settleTrade(
                    ((Number) t.get("buyerId")).longValue(),
                    ((Number) t.get("sellerId")).longValue(),
                    accountType,
                    (String) t.get("baseAsset"),
                    (String) t.get("quoteAsset"),
                    new BigDecimal((String) t.get("qty")),
                    new BigDecimal((String) t.get("quoteAmt")),
                    new BigDecimal((String) t.getOrDefault("buyFee",  "0")),
                    new BigDecimal((String) t.getOrDefault("sellFee", "0")),
                    tradeId,
                    clusterTimestamp);
        }
        log.debug("[BalanceLedger] BATCH_SETTLE OK count={}", trades.size());
    }

    // =========================================================================
    // Match Archive 位点管理
    // =========================================================================

    public long getMatchArchivePosition() {
        return matchArchivePosition;
    }

    public void updateMatchArchivePosition(long position) {
        this.matchArchivePosition = position;
    }

    // =========================================================================
    // seq（资金流水序号）
    // =========================================================================

    /** 当前 seq（自创世以来产出的流水总数）。只读，供状态查询使用。 */
    public long currentSeq() {
        return seq;
    }

    /**
     * 消费 {@code flowCount} 个 seq（即产出了这么多条流水记录）。
     *
     * <p>只在实际发生状态变更后调用，运行在所有副本上，保证 seq 一致。
     */
    private void advanceSeq(long flowCount) {
        seq += flowCount;
    }

    // =========================================================================
    // processedBizNos TTL 驱逐
    // =========================================================================

    /**
     * 驱逐已过期的幂等记录（每分钟由 AssetClusteredService 调用）。
     *
     * @param currentClusterTimestampMs 当前 Cluster 时间（epoch ms）
     * @return 本次驱逐的条目数
     */
    public int evictExpiredBizNos(long currentClusterTimestampMs) {
        int before = processedBizNos.size();
        processedBizNos.entrySet().removeIf(e -> currentClusterTimestampMs > e.getValue());
        int evicted = before - processedBizNos.size();
        if (evicted > 0) {
            log.debug("[BalanceLedger] Evicted {} expired bizNos, remaining={}",
                    evicted, processedBizNos.size());
        }
        return evicted;
    }

    public int processedBizNosSize() {
        return processedBizNos.size();
    }

    /** 用户数，O(1)。 */
    public int userCount() {
        return ledger.size();
    }

    /** 永久幂等表大小，O(1)。 */
    public int permanentBizNosSize() {
        return permanentBizNos.size();
    }

    /**
     * 账本条目总数 (userId×accountType×asset)，O(n)。
     * 仅供低频的状态冷更新调用，不要放在热路径。
     */
    public long ledgerEntryCount() {
        long n = 0;
        for (Map<AccountType, Map<String, Balance>> byType : ledger.values()) {
            for (Map<String, Balance> byAsset : byType.values()) {
                n += byAsset.size();
            }
        }
        return n;
    }

    // =========================================================================
    // Snapshot 序列化支持
    // =========================================================================

    /**
     * 导出完整账本（用于 Cluster Snapshot）。
     *
     * <p>AccountType 枚举 key 转为 String，便于 JSON 序列化/反序列化。
     * 结构：userId → accountType(String) → asset → Balance
     */
    public Map<Long, Map<String, Map<String, Balance>>> exportLedger() {
        Map<Long, Map<String, Map<String, Balance>>> copy = new HashMap<>();
        ledger.forEach((uid, byType) -> {
            Map<String, Map<String, Balance>> typeCopy = new HashMap<>();
            byType.forEach((type, assets) -> {
                Map<String, Balance> assetsCopy = new HashMap<>();
                assets.forEach((asset, bal) -> assetsCopy.put(asset, bal.copy()));
                typeCopy.put(type.name(), assetsCopy);
            });
            copy.put(uid, typeCopy);
        });
        return copy;
    }

    /** 导出幂等记录（用于 Cluster Snapshot） */
    public Map<String, Long> exportProcessedBizNos() {
        return new HashMap<>(processedBizNos);
    }

    /** 导出永久幂等记录（CREDIT/DEBIT，用于 Cluster Snapshot） */
    public java.util.Set<String> exportPermanentBizNos() {
        return new java.util.HashSet<>(permanentBizNos);
    }

    /**
     * 从 Cluster Snapshot 还原账本。
     *
     * @param ledgerData       userId → accountType(String) → asset → Balance
     * @param bizNosData       key → 过期时间
     * @param permanentData    CREDIT/DEBIT 永久幂等键集合
     * @param archivedPosition Match Archive 已消费位点
     * @param restoredSeq      快照中的 seq（从此值继续，不回退）
     */
    public void restore(Map<Long, Map<String, Map<String, Balance>>> ledgerData,
                        Map<String, Long> bizNosData,
                        java.util.Collection<String> permanentData,
                        long archivedPosition,
                        long restoredSeq) {
        ledger.clear();
        processedBizNos.clear();
        permanentBizNos.clear();

        if (ledgerData != null) {
            ledgerData.forEach((uid, byType) -> {
                Map<AccountType, Map<String, Balance>> typeMap = new HashMap<>();
                byType.forEach((typeName, assets) -> {
                    try {
                        AccountType type = AccountType.valueOf(typeName);
                        typeMap.put(type, new HashMap<>(assets));
                    } catch (IllegalArgumentException e) {
                        log.warn("[BalanceLedger] Unknown accountType in snapshot: {}, skipping", typeName);
                    }
                });
                ledger.put(uid, typeMap);
            });
        }
        if (bizNosData != null) processedBizNos.putAll(bizNosData);
        if (permanentData != null) permanentBizNos.addAll(permanentData);
        this.matchArchivePosition = archivedPosition;
        this.seq = restoredSeq;

        log.info("[BalanceLedger] Restored — {} users, {} processed bizNos, {} permanent bizNos, matchArchivePosition={}, seq={}",
                ledger.size(), processedBizNos.size(), permanentBizNos.size(), archivedPosition, restoredSeq);
    }

    // =========================================================================
    // Private helpers
    // =========================================================================

    private Balance getOrCreate(Long userId, AccountType accountType, String asset) {
        return ledger
                .computeIfAbsent(userId,      k -> new HashMap<>())
                .computeIfAbsent(accountType, k -> new HashMap<>())
                .computeIfAbsent(asset,       k -> new Balance());
    }

    private boolean isProcessed(String key) {
        return processedBizNos.containsKey(key);
    }

    /**
     * 上报幂等命中事件(仅观测)。
     *
     * <p>使用 clusterTimestamp 而非 wall-clock,保证各副本日志输出时机一致,
     * 便于跨节点比对。计数本身不属于状态机状态。
     */
    private void reportIdempotentHit(String op, String bizNoId, long clusterTimestamp) {
        eventReporter.record(CoreSystemEvent.IDEMPOTENT_HIT, clusterTimestamp,
                () -> "op=" + op + " bizNo=" + bizNoId);
    }

    private void markProcessed(String key, String bizNoId, long clusterTimestamp) {
        // 确定性：以 clusterTimestamp 作为合理性校验参考时间（不读 wall-clock），
        // 保证 Raft 日志重放时 TTL 计算与原始执行完全一致
        long snowflakeTs = SnowflakeId.tryExtractTimestampMs(bizNoId, clusterTimestamp);
        long refTime     = snowflakeTs > 0 ? snowflakeTs : clusterTimestamp;
        processedBizNos.put(key, refTime + BIZNO_TTL_MS);
    }
}
