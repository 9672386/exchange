package com.exchange.account.core.cluster;

import com.exchange.account.api.dto.AssetStateChangeEvent;
import com.exchange.account.api.enums.AccountType;
import com.exchange.account.api.enums.FundFlowType;
import com.exchange.account.core.cluster.event.AssetEventPublisher;
import com.exchange.account.core.cluster.ledger.Balance;
import com.exchange.account.core.cluster.ledger.BalanceLedger;
import com.exchange.account.core.cluster.protocol.AssetMsgType;
import com.exchange.account.core.cluster.ledger.BalanceLedger.FreezeItem;
import com.exchange.common.event.CoreSystemEvent;
import com.exchange.common.event.SystemEventReporter;
import com.exchange.common.id.SnowflakeId;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.aeron.ExclusivePublication;
import io.aeron.Image;
import io.aeron.cluster.codecs.CloseReason;
import io.aeron.cluster.service.ClientSession;
import io.aeron.cluster.service.Cluster;
import io.aeron.cluster.service.ClusteredService;
import io.aeron.logbuffer.Header;
import lombok.extern.slf4j.Slf4j;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 资产账户 Aeron Cluster 服务（纯内存账本 + Raft 共识）。
 *
 * <h3>设计原则：关注点分离</h3>
 * <ul>
 *   <li><b>本类职责</b>：BalanceLedger 状态管理、Raft 共识、Cluster Egress 回包。</li>
 *   <li><b>不做的事</b>：不直接写任何数据库。账本变更后仅发布 {@link AssetStateChangeEvent}
 *       到 Aeron Archive，由独立的 {@code account-persist} 服务消费并落库。</li>
 * </ul>
 *
 * <h3>账户类型（AccountType）</h3>
 * <p>所有 Ingress 消息的 JSON body 中须携带 {@code accountType} 字段（枚举名字符串），
 * 如 {@code "SPOT"}、{@code "FUTURES"}，缺省为 {@code SPOT}。
 * 账本按 userId → accountType → asset 三层隔离，跨账户转账需专用 Transfer 消息。
 *
 * <h3>请求有效期校验</h3>
 * <p>FREEZE / BATCH_FREEZE orderId / tradeId 须为 Snowflake 格式，
 * 生成时间戳与当前 Cluster 时间差超过 30 min 则直接拒绝。
 *
 * <h3>消息格式（Ingress）</h3>
 * <pre>
 *   [1 byte msgType][JSON body]
 *   JSON 中必须包含 "correlationId" 和 "accountType" 字段。
 * </pre>
 *
 * <h3>确定性约束</h3>
 * <p>所有账本操作在 Cluster Service Thread 上顺序执行，BigDecimal 精度确定，无随机、无 I/O。
 */
@Slf4j
public class AssetClusteredService implements ClusteredService {

    private static final long EVICT_INTERVAL_MS = 60_000L;

    private final BalanceLedger       ledger;
    private final AssetEventPublisher eventPublisher;
    private final ObjectMapper        objectMapper;

    /**
     * 系统事件上报器(仅观测)。
     *
     * <p>所有调用均传入 cluster timestamp 作为参考时间,状态机内不读 wall-clock。
     * 计数为节点本地观测数据,不入快照、不参与判定。
     */
    private final SystemEventReporter eventReporter;

    /** 运行时状态快照（Service Thread 单写，HTTP 线程读）。可为 null（测试）。 */
    private final ClusterRuntimeStatus runtimeStatus;

    private Cluster cluster;
    private long    evictTimerCorrelationId = -1L;

    private final UnsafeBuffer egressBuffer = new UnsafeBuffer(ByteBuffer.allocateDirect(65536));

    public AssetClusteredService(BalanceLedger ledger, AssetEventPublisher eventPublisher) {
        this(ledger, eventPublisher, SystemEventReporter.noop(), null);
    }

    public AssetClusteredService(BalanceLedger ledger, AssetEventPublisher eventPublisher,
                                 SystemEventReporter eventReporter) {
        this(ledger, eventPublisher, eventReporter, null);
    }

    public AssetClusteredService(BalanceLedger ledger, AssetEventPublisher eventPublisher,
                                 SystemEventReporter eventReporter,
                                 ClusterRuntimeStatus runtimeStatus) {
        this.ledger         = ledger;
        this.eventPublisher = eventPublisher;
        this.eventReporter  = eventReporter != null ? eventReporter : SystemEventReporter.noop();
        this.runtimeStatus  = runtimeStatus;
        this.objectMapper   = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    // =========================================================================
    // Lifecycle
    // =========================================================================

    @Override
    public void onStart(Cluster cluster, Image snapshotImage) {
        this.cluster = cluster;
        log.info("[AssetCluster] onStart — role={}, memberId={}", cluster.role(), cluster.memberId());
        if (snapshotImage != null) {
            log.info("[AssetCluster] Loading snapshot...");
            loadSnapshot(snapshotImage);
        } else {
            log.info("[AssetCluster] No snapshot — starting with empty ledger");
        }
        if (runtimeStatus != null) {
            runtimeStatus.markStarted(cluster.memberId(), cluster.role().name());
            // 启动即做一次冷更新，让状态接口在无流量时也有账本规模
            runtimeStatus.updateCold(ledger.ledgerEntryCount(), cluster.time());
            publishHotStatus(cluster.time());
        }
        evictTimerCorrelationId = cluster.time();
        cluster.scheduleTimer(evictTimerCorrelationId, cluster.time() + EVICT_INTERVAL_MS);
    }

    @Override
    public void onSessionOpen(ClientSession session, long timestamp) {
        log.info("[AssetCluster] Session opened — sessionId={}", session.id());
    }

    @Override
    public void onSessionClose(ClientSession session, long timestamp, CloseReason closeReason) {
        log.info("[AssetCluster] Session closed — sessionId={}, reason={}", session.id(), closeReason);
    }

    // =========================================================================
    // Core dispatch
    // =========================================================================

    @Override
    public void onSessionMessage(ClientSession session, long timestamp,
                                 DirectBuffer buffer, int offset, int length,
                                 Header header) {
        if (length < 1) return;
        final byte msgType = buffer.getByte(offset);
        final int  jsonLen = length - 1;
        try {
            switch (msgType) {
                case AssetMsgType.FREEZE               -> handleFreeze(session, timestamp, buffer, offset + 1, jsonLen);
                case AssetMsgType.UNFREEZE             -> handleUnfreeze(session, timestamp, buffer, offset + 1, jsonLen);
                case AssetMsgType.SETTLE_TRADE         -> handleSettle(session, timestamp, buffer, offset + 1, jsonLen);
                case AssetMsgType.BALANCE_QUERY        -> handleBalanceQuery(session, buffer, offset + 1, jsonLen);
                case AssetMsgType.BATCH_FREEZE         -> handleBatchFreeze(session, timestamp, buffer, offset + 1, jsonLen);
                case AssetMsgType.BATCH_SETTLE         -> handleBatchSettle(session, timestamp, buffer, offset + 1, jsonLen);
                case AssetMsgType.MATCH_POSITION_QUERY -> handleMatchPositionQuery(session, buffer, offset + 1, jsonLen);
                case AssetMsgType.CREDIT               -> handleCredit(session, timestamp, buffer, offset + 1, jsonLen);
                case AssetMsgType.DEBIT                -> handleDebit(session, timestamp, buffer, offset + 1, jsonLen);
                case AssetMsgType.INTERNAL_TRANSFER    -> handleInternalTransfer(session, timestamp, buffer, offset + 1, jsonLen);
                default -> log.warn("[AssetCluster] Unknown msgType=0x{}", Integer.toHexString(msgType & 0xFF));
            }
        } catch (IllegalStateException e) {
            log.warn("[AssetCluster] Business error: {}", e.getMessage());
            sendEgress(session, resolveFailType(msgType), "{\"error\":\"" + e.getMessage() + "\"}");
        } catch (Exception e) {
            log.error("[AssetCluster] Unexpected error", e);
            sendEgress(session, AssetMsgType.ERROR, "{\"error\":\"internal error\"}");
        } finally {
            publishHotStatus(timestamp);
        }
    }

    /**
     * 把 O(1) 运行时指标写入 {@link ClusterRuntimeStatus}（Service Thread 单写）。
     * 供 HTTP 线程无锁读取，避免直接触碰状态机的 HashMap。
     */
    private void publishHotStatus(long clusterTime) {
        if (runtimeStatus == null || cluster == null) return;
        runtimeStatus.updateHot(
                cluster.role().name(),
                cluster.memberId(),
                ledger.currentSeq(),
                ledger.getMatchArchivePosition(),
                ledger.userCount(),
                ledger.processedBizNosSize(),
                ledger.permanentBizNosSize(),
                clusterTime);
    }

    // =========================================================================
    // Timer
    // =========================================================================

    @Override
    public void onTimerEvent(long correlationId, long timestamp) {
        if (correlationId == evictTimerCorrelationId) {
            int evicted = ledger.evictExpiredBizNos(timestamp);
            log.info("[AssetCluster] BizNo eviction: evicted={}, remaining={}",
                    evicted, ledger.processedBizNosSize());

            // 补齐"事件停止后最后一个窗口"的统计输出。
            // 按时间触发的日志依赖新事件到达才会检查，若某类事件突然停止，
            // 其残余增量需要由定时器兜底输出，否则会一直沉默到下次发生。
            eventReporter.flushPending(timestamp);

            // 冷更新：O(n) 的账本条目总数，随驱逐节奏（默认 60s）刷新，避免放热路径。
            // 同时刷一次热指标，保证无流量时状态接口也不会陈旧。
            if (runtimeStatus != null) {
                runtimeStatus.updateCold(ledger.ledgerEntryCount(), timestamp);
                publishHotStatus(timestamp);
            }

            evictTimerCorrelationId = timestamp;
            cluster.scheduleTimer(evictTimerCorrelationId, timestamp + EVICT_INTERVAL_MS);
        }
    }

    // =========================================================================
    // Handlers
    // =========================================================================

    private void handleFreeze(ClientSession session, long timestamp,
                              DirectBuffer buffer, int offset, int length) throws IOException {
        JsonNode    req           = parseJson(buffer, offset, length);
        String      correlationId = req.path("correlationId").asText("");
        Long        userId        = req.get("userId").asLong();
        AccountType accountType   = parseAccountType(req, AccountType.SPOT);
        String      asset         = req.get("asset").asText();
        BigDecimal  amt           = new BigDecimal(req.get("amount").asText());
        String      orderId       = req.get("orderId").asText();

        checkBizNoExpiry(orderId, timestamp, "FREEZE");
        ledger.freeze(userId, accountType, asset, amt, orderId, timestamp);

        Balance snap = ledger.getBalance(userId, accountType, asset);
        publishIfLeader(AssetStateChangeEvent.builder()
                .eventId("FREEZE:" + orderId + ":" + userId + ":" + accountType + ":" + asset)
                .eventType("FREEZE")
                .userId(userId).accountType(accountType).asset(asset)
                .available(snap.getAvailable()).frozen(snap.getFrozen())
                .amount(amt.negate())
                .flowType(FundFlowType.FREEZE)
                .bizNo(orderId).remark("freeze for order")
                .clusterTimestamp(timestamp).build());

        sendEgress(session, AssetMsgType.FREEZE_OK,
                "{\"correlationId\":\"" + correlationId + "\",\"orderId\":\"" + orderId + "\",\"status\":\"OK\"}");
        log.debug("[AssetCluster] FREEZE OK userId={} accountType={} asset={} amount={}",
                userId, accountType, asset, amt);
    }

    private void handleUnfreeze(ClientSession session, long timestamp,
                                DirectBuffer buffer, int offset, int length) throws IOException {
        JsonNode    req           = parseJson(buffer, offset, length);
        String      correlationId = req.path("correlationId").asText("");
        Long        userId        = req.get("userId").asLong();
        AccountType accountType   = parseAccountType(req, AccountType.SPOT);
        String      asset         = req.get("asset").asText();
        BigDecimal  amt           = new BigDecimal(req.get("amount").asText());
        String      orderId       = req.get("orderId").asText();

        // 撤单解冻不做超时校验：orderId 可能是老单，时间超 30min 也需解冻
        ledger.unfreeze(userId, accountType, asset, amt, orderId, timestamp);

        Balance snap = ledger.getBalance(userId, accountType, asset);
        publishIfLeader(AssetStateChangeEvent.builder()
                .eventId("UNFREEZE:" + orderId + ":" + userId + ":" + accountType + ":" + asset)
                .eventType("UNFREEZE")
                .userId(userId).accountType(accountType).asset(asset)
                .available(snap.getAvailable()).frozen(snap.getFrozen())
                .amount(amt)
                .flowType(FundFlowType.UNFREEZE)
                .bizNo(orderId).remark("unfreeze for order")
                .clusterTimestamp(timestamp).build());

        sendEgress(session, AssetMsgType.UNFREEZE_OK,
                "{\"correlationId\":\"" + correlationId + "\",\"orderId\":\"" + orderId + "\",\"status\":\"OK\"}");
        log.debug("[AssetCluster] UNFREEZE OK userId={} accountType={} asset={} amount={}",
                userId, accountType, asset, amt);
    }

    private void handleSettle(ClientSession session, long timestamp,
                              DirectBuffer buffer, int offset, int length) throws IOException {
        JsonNode    req           = parseJson(buffer, offset, length);
        String      correlationId = req.path("correlationId").asText("");
        Long        buyerId       = req.get("buyerId").asLong();
        Long        sellerId      = req.get("sellerId").asLong();
        AccountType accountType   = parseAccountType(req, AccountType.SPOT);
        String      baseAsset     = req.get("baseAsset").asText();
        String      quoteAsset    = req.get("quoteAsset").asText();
        BigDecimal  qty           = new BigDecimal(req.get("qty").asText());
        BigDecimal  quoteAmt      = new BigDecimal(req.get("quoteAmt").asText());
        BigDecimal  buyFee        = new BigDecimal(req.get("buyFee").asText());
        BigDecimal  sellFee       = new BigDecimal(req.get("sellFee").asText());
        String      tradeId       = req.get("tradeId").asText();

        ledger.settleTrade(buyerId, sellerId, accountType, baseAsset, quoteAsset,
                qty, quoteAmt, buyFee, sellFee, tradeId, timestamp);

        if (cluster.role() == Cluster.Role.LEADER && eventPublisher != null) {
            publishSettleEvents(buyerId, sellerId, accountType, baseAsset, quoteAsset,
                    qty, quoteAmt, buyFee, sellFee, tradeId, timestamp);
        }

        sendEgress(session, AssetMsgType.SETTLE_OK,
                "{\"correlationId\":\"" + correlationId + "\",\"tradeId\":\"" + tradeId + "\",\"status\":\"OK\"}");
        log.debug("[AssetCluster] SETTLE OK tradeId={} accountType={}", tradeId, accountType);
    }

    private void handleBalanceQuery(ClientSession session,
                                    DirectBuffer buffer, int offset, int length) throws IOException {
        JsonNode    req           = parseJson(buffer, offset, length);
        String      correlationId = req.path("correlationId").asText("");
        Long        userId        = req.get("userId").asLong();
        AccountType accountType   = parseAccountType(req, null);
        String      asset         = req.has("asset") ? req.get("asset").asText() : null;

        String json;
        if (accountType != null && asset != null) {
            // 查单资产
            Balance bal = ledger.getBalance(userId, accountType, asset);
            json = objectMapper.writeValueAsString(Map.of(
                    "correlationId", correlationId,
                    "userId", userId, "accountType", accountType.name(), "asset", asset,
                    "available", bal.getAvailable(), "frozen", bal.getFrozen()));
        } else if (accountType != null) {
            // 查某账户类型下所有资产
            Map<String, Balance> all = ledger.getAllBalances(userId, accountType);
            json = objectMapper.writeValueAsString(Map.of(
                    "correlationId", correlationId,
                    "userId", userId, "accountType", accountType.name(), "balances", all));
        } else {
            // 查全部账户类型（不传 accountType）
            Map<AccountType, Map<String, Balance>> all = ledger.getAllBalancesByType(userId);
            json = objectMapper.writeValueAsString(Map.of(
                    "correlationId", correlationId,
                    "userId", userId, "allBalances", all));
        }
        sendEgress(session, AssetMsgType.BALANCE_RESP, json);
    }

    // =========================================================================
    // 批量 Handlers
    // =========================================================================

    private void handleBatchFreeze(ClientSession session, long timestamp,
                                   DirectBuffer buffer, int offset, int length) throws IOException {
        JsonNode    req           = parseJson(buffer, offset, length);
        String      correlationId = req.path("correlationId").asText("");
        Long        userId        = req.get("userId").asLong();
        AccountType batchType     = parseAccountType(req, AccountType.SPOT);

        JsonNode itemsNode = req.get("items");
        List<FreezeItem> items = new ArrayList<>();
        for (JsonNode n : itemsNode) {
            // 子项可单独指定 accountType，缺省继承批次级别
            AccountType itemType = n.has("accountType")
                    ? AccountType.valueOf(n.get("accountType").asText()) : batchType;
            items.add(new FreezeItem(
                    n.get("orderId").asText(),
                    itemType,
                    n.get("asset").asText(),
                    new BigDecimal(n.get("amount").asText())));
        }

        if (!items.isEmpty()) {
            checkBizNoExpiry(items.get(0).orderId(), timestamp, "BATCH_FREEZE");
        }

        List<FreezeItem> processed = ledger.batchFreeze(userId, items, timestamp);

        for (FreezeItem item : processed) {
            Balance snap = ledger.getBalance(userId, item.accountType(), item.asset());
            publishIfLeader(AssetStateChangeEvent.builder()
                    .eventId("FREEZE:" + item.orderId() + ":" + userId + ":" + item.accountType() + ":" + item.asset())
                    .eventType("FREEZE")
                    .userId(userId).accountType(item.accountType()).asset(item.asset())
                    .available(snap.getAvailable()).frozen(snap.getFrozen())
                    .amount(item.amount().negate())
                    .flowType(FundFlowType.FREEZE)
                    .bizNo(item.orderId()).remark("batch freeze for order")
                    .clusterTimestamp(timestamp).build());
        }

        sendEgress(session, AssetMsgType.BATCH_FREEZE_RESP,
                "{\"correlationId\":\"" + correlationId + "\",\"status\":\"OK\",\"count\":" + processed.size() + "}");
        log.debug("[AssetCluster] BATCH_FREEZE OK userId={} accountType={} processed={} total={}",
                userId, batchType, processed.size(), items.size());
    }

    private void handleBatchSettle(ClientSession session, long timestamp,
                                   DirectBuffer buffer, int offset, int length) throws IOException {
        JsonNode req            = parseJson(buffer, offset, length);
        String   correlationId  = req.path("correlationId").asText("");
        long     archivePosition = req.path("archivePosition").asLong(-1L);
        // 顶层 accountType 作为批次默认值；各 trade 子项可覆盖
        AccountType batchType = parseAccountType(req, AccountType.SPOT);

        JsonNode tradesNode = req.get("trades");
        List<Map<String, Object>> trades = new ArrayList<>();
        for (JsonNode n : tradesNode) {
            Map<String, Object> t = new HashMap<>();
            t.put("tradeId",     n.get("tradeId").asText());
            t.put("buyerId",     n.get("buyerId").asLong());
            t.put("sellerId",    n.get("sellerId").asLong());
            t.put("baseAsset",   n.get("baseAsset").asText());
            t.put("quoteAsset",  n.get("quoteAsset").asText());
            t.put("qty",         n.get("qty").asText());
            t.put("quoteAmt",    n.get("quoteAmt").asText());
            t.put("buyFee",      n.path("buyFee").asText("0"));
            t.put("sellFee",     n.path("sellFee").asText("0"));
            String at = n.has("accountType") ? n.get("accountType").asText() : batchType.name();
            t.put("accountType", at);
            trades.add(t);
        }

        ledger.batchSettle(trades, timestamp);

        if (archivePosition >= 0) {
            ledger.updateMatchArchivePosition(archivePosition);
        }

        if (cluster.role() == Cluster.Role.LEADER && eventPublisher != null) {
            for (Map<String, Object> t : trades) {
                String      tradeId     = (String) t.get("tradeId");
                Long        buyerId     = ((Number) t.get("buyerId")).longValue();
                Long        sellerId    = ((Number) t.get("sellerId")).longValue();
                AccountType accountType = AccountType.valueOf((String) t.get("accountType"));
                String      baseAsset   = (String) t.get("baseAsset");
                String      quoteAsset  = (String) t.get("quoteAsset");
                BigDecimal  qty         = new BigDecimal((String) t.get("qty"));
                BigDecimal  quoteAmt    = new BigDecimal((String) t.get("quoteAmt"));
                BigDecimal  buyFee      = new BigDecimal((String) t.getOrDefault("buyFee",  "0"));
                BigDecimal  sellFee     = new BigDecimal((String) t.getOrDefault("sellFee", "0"));
                publishSettleEvents(buyerId, sellerId, accountType, baseAsset, quoteAsset,
                        qty, quoteAmt, buyFee, sellFee, tradeId, timestamp);
            }
        }

        sendEgress(session, AssetMsgType.BATCH_SETTLE_RESP,
                "{\"correlationId\":\"" + correlationId + "\",\"status\":\"OK\",\"count\":" + trades.size() + "}");
        log.debug("[AssetCluster] BATCH_SETTLE OK count={}", trades.size());
    }

    private void handleCredit(ClientSession session, long timestamp,
                              DirectBuffer buffer, int offset, int length) throws IOException {
        JsonNode    req           = parseJson(buffer, offset, length);
        String      correlationId = req.path("correlationId").asText("");
        Long        userId        = req.get("userId").asLong();
        AccountType accountType   = requireAccountType(req, "accountType", "CREDIT");
        String      asset         = req.get("asset").asText();
        BigDecimal  amount        = new BigDecimal(req.get("amount").asText());
        String      bizNo         = req.get("bizNo").asText();
        String      remark        = req.path("remark").asText("credit");

        ledger.credit(userId, accountType, asset, amount, bizNo, timestamp);

        Balance snap = ledger.getBalance(userId, accountType, asset);
        publishIfLeader(AssetStateChangeEvent.builder()
                .eventId("CREDIT:" + bizNo + ":" + userId + ":" + accountType + ":" + asset)
                .eventType("CREDIT")
                .userId(userId).accountType(accountType).asset(asset)
                .available(snap.getAvailable()).frozen(snap.getFrozen())
                .amount(amount)
                .flowType(FundFlowType.CREDIT)
                .bizNo(bizNo).remark(remark)
                .clusterTimestamp(timestamp).build());

        sendEgress(session, AssetMsgType.CREDIT_OK,
                "{\"correlationId\":\"" + correlationId + "\",\"bizNo\":\"" + bizNo + "\",\"status\":\"OK\"}");
        log.debug("[AssetCluster] CREDIT OK userId={} accountType={} asset={} amount={} bizNo={}",
                userId, accountType, asset, amount, bizNo);
    }

    private void handleDebit(ClientSession session, long timestamp,
                             DirectBuffer buffer, int offset, int length) throws IOException {
        JsonNode    req           = parseJson(buffer, offset, length);
        String      correlationId = req.path("correlationId").asText("");
        Long        userId        = req.get("userId").asLong();
        AccountType accountType   = requireAccountType(req, "accountType", "DEBIT");
        String      asset         = req.get("asset").asText();
        BigDecimal  amount        = new BigDecimal(req.get("amount").asText());
        String      bizNo         = req.get("bizNo").asText();
        String      remark        = req.path("remark").asText("debit");

        ledger.debit(userId, accountType, asset, amount, bizNo, timestamp);

        Balance snap = ledger.getBalance(userId, accountType, asset);
        publishIfLeader(AssetStateChangeEvent.builder()
                .eventId("DEBIT:" + bizNo + ":" + userId + ":" + accountType + ":" + asset)
                .eventType("DEBIT")
                .userId(userId).accountType(accountType).asset(asset)
                .available(snap.getAvailable()).frozen(snap.getFrozen())
                .amount(amount.negate())
                .flowType(FundFlowType.DEBIT)
                .bizNo(bizNo).remark(remark)
                .clusterTimestamp(timestamp).build());

        sendEgress(session, AssetMsgType.DEBIT_OK,
                "{\"correlationId\":\"" + correlationId + "\",\"bizNo\":\"" + bizNo + "\",\"status\":\"OK\"}");
        log.debug("[AssetCluster] DEBIT OK userId={} accountType={} asset={} amount={} bizNo={}",
                userId, accountType, asset, amount, bizNo);
    }

    private void handleInternalTransfer(ClientSession session, long timestamp,
                                        DirectBuffer buffer, int offset, int length) throws IOException {
        JsonNode    req           = parseJson(buffer, offset, length);
        String      correlationId = req.path("correlationId").asText("");
        Long        userId        = req.get("userId").asLong();
        AccountType fromType      = requireAccountType(req, "fromAccountType", "INTERNAL_TRANSFER");
        AccountType toType        = requireAccountType(req, "toAccountType", "INTERNAL_TRANSFER");
        String      asset         = req.get("asset").asText();
        BigDecimal  amount        = new BigDecimal(req.get("amount").asText());
        String      bizNo         = req.get("bizNo").asText();
        String      remark        = req.path("remark").asText("internal transfer");

        checkBizNoExpiry(bizNo, timestamp, "INTERNAL_TRANSFER");
        ledger.internalTransfer(userId, fromType, toType, asset, amount, bizNo, timestamp);

        // 发布出账事件（TRANSFER_OUT）
        Balance fromSnap = ledger.getBalance(userId, fromType, asset);
        publishIfLeader(AssetStateChangeEvent.builder()
                .eventId("TRANSFER_OUT:" + bizNo + ":" + userId + ":" + fromType + ":" + asset)
                .eventType("TRANSFER_OUT")
                .userId(userId).accountType(fromType).asset(asset)
                .available(fromSnap.getAvailable()).frozen(fromSnap.getFrozen())
                .amount(amount.negate())
                .flowType(FundFlowType.TRANSFER_OUT)
                .bizNo(bizNo).remark(remark)
                .clusterTimestamp(timestamp).build());

        // 发布入账事件（TRANSFER_IN）
        Balance toSnap = ledger.getBalance(userId, toType, asset);
        publishIfLeader(AssetStateChangeEvent.builder()
                .eventId("TRANSFER_IN:" + bizNo + ":" + userId + ":" + toType + ":" + asset)
                .eventType("TRANSFER_IN")
                .userId(userId).accountType(toType).asset(asset)
                .available(toSnap.getAvailable()).frozen(toSnap.getFrozen())
                .amount(amount)
                .flowType(FundFlowType.TRANSFER_IN)
                .bizNo(bizNo).remark(remark)
                .clusterTimestamp(timestamp).build());

        sendEgress(session, AssetMsgType.TRANSFER_OK,
                "{\"correlationId\":\"" + correlationId + "\",\"bizNo\":\"" + bizNo + "\",\"status\":\"OK\"}");
        log.debug("[AssetCluster] TRANSFER OK userId={} from={} to={} asset={} amount={} bizNo={}",
                userId, fromType, toType, asset, amount, bizNo);
    }

    private void handleMatchPositionQuery(ClientSession session,
                                          DirectBuffer buffer, int offset, int length) throws IOException {
        JsonNode req = parseJson(buffer, offset, length);
        String correlationId = req.path("correlationId").asText("");
        long position = ledger.getMatchArchivePosition();
        sendEgress(session, AssetMsgType.MATCH_POSITION_RESP,
                "{\"correlationId\":\"" + correlationId + "\",\"position\":" + position + "}");
        log.debug("[AssetCluster] MATCH_POSITION_QUERY → position={}", position);
    }

    // =========================================================================
    // Snapshot
    // =========================================================================

    /** 每个用户分块消息最多包含的用户数（控制单条消息大小，避免超过 Aeron maxMessageLength）。 */
    private static final int SNAPSHOT_USERS_PER_CHUNK = 200;
    /** Snapshot offer 最大重试次数（有界，避免永久自旋阻塞 Service Thread）。 */
    private static final int SNAPSHOT_OFFER_MAX_RETRIES = 500_000;

    /**
     * 分块写快照。
     *
     * <h3>为什么分块</h3>
     * <p>旧实现把整个账本序列化成一条消息 offer：账本超过 Aeron maxMessageLength
     * （约 term buffer 的 1/8）后 offer 永久失败 → 无限自旋 → 状态机停摆。
     * 分块后每条消息大小可控，规模不再受限。
     *
     * <h3>格式</h3>
     * <pre>
     *   N 条:  {"type":"users","data":{userId:{accountType:{asset:Balance}}}}
     *   1 条:  {"type":"end","userChunks":N,"bizNos":{...},"permanentBizNos":[...],"matchArchivePosition":P}
     * </pre>
     */
    @Override
    public void onTakeSnapshot(ExclusivePublication snapshotPublication) {
        log.info("[AssetCluster] Taking snapshot (chunked)...");
        try {
            Map<Long, Map<String, Map<String, Balance>>> full = ledger.exportLedger();

            int chunks = 0;
            Map<Long, Map<String, Map<String, Balance>>> batch = new HashMap<>();
            for (Map.Entry<Long, Map<String, Map<String, Balance>>> e : full.entrySet()) {
                batch.put(e.getKey(), e.getValue());
                if (batch.size() >= SNAPSHOT_USERS_PER_CHUNK) {
                    offerSnapshotMessage(snapshotPublication,
                            Map.of("type", "users", "data", batch));
                    chunks++;
                    batch = new HashMap<>();
                }
            }
            if (!batch.isEmpty()) {
                offerSnapshotMessage(snapshotPublication, Map.of("type", "users", "data", batch));
                chunks++;
            }

            // 结束标记：携带幂等表 + 位点 + seq + 分块数（加载端校验完整性）
            offerSnapshotMessage(snapshotPublication, Map.of(
                    "type",                 "end",
                    "userChunks",           chunks,
                    "bizNos",               ledger.exportProcessedBizNos(),
                    "permanentBizNos",      ledger.exportPermanentBizNos(),
                    "matchArchivePosition", ledger.getMatchArchivePosition(),
                    "seq",                  ledger.currentSeq()));

            final int  chunkCount = chunks;
            final int  userCount  = full.size();
            eventReporter.record(CoreSystemEvent.SNAPSHOT_TAKEN, cluster.time(),
                    () -> "chunks=" + chunkCount + " users=" + userCount);
            log.info("[AssetCluster] Snapshot written — {} user chunks, {} users", chunks, full.size());
        } catch (Exception e) {
            eventReporter.record(CoreSystemEvent.SNAPSHOT_FAILED, cluster.time(),
                    () -> "cause=" + e.getClass().getSimpleName() + ": " + e.getMessage());
            throw new RuntimeException("Asset snapshot failed", e);
        }
    }

    /** 有界重试 offer 一条快照消息；终态错误或超限直接抛异常（快照失败必须显式暴露）。 */
    private void offerSnapshotMessage(ExclusivePublication pub, Map<String, Object> msg) throws IOException {
        byte[] bytes = objectMapper.writeValueAsBytes(msg);
        UnsafeBuffer buf = new UnsafeBuffer(bytes);
        long result;
        int  retries = 0;
        while ((result = pub.offer(buf, 0, bytes.length)) < 0) {
            if (result == io.aeron.Publication.CLOSED
                    || result == io.aeron.Publication.NOT_CONNECTED
                    || result == io.aeron.Publication.MAX_POSITION_EXCEEDED) {
                throw new IllegalStateException("Snapshot publication unavailable, result=" + result);
            }
            if (++retries > SNAPSHOT_OFFER_MAX_RETRIES) {
                throw new IllegalStateException("Snapshot offer timed out after " + retries + " retries");
            }
            Thread.yield();
        }
    }

    /**
     * 分块读快照。
     *
     * <p>使用 {@link io.aeron.ImageFragmentAssembler} 重组跨 MTU 分片的消息
     * （旧实现用裸 handler,单条消息超过 MTU 时各分片互相覆盖,快照恢复必然损坏）。
     * 兼容旧格式：消息含 {@code ledger} 字段则按单消息格式解析。
     */
    @SuppressWarnings("unchecked")
    private void loadSnapshot(Image snapshotImage) {
        Map<Long, Map<String, Map<String, Balance>>> ledgerData = new HashMap<>();
        final JsonNode[] endNode  = {null};
        final int[]      chunks   = {0};
        final boolean[]  legacy   = {false};
        final Map<Long, Map<String, Map<String, Balance>>>[] legacyData = new Map[]{null};

        io.aeron.ImageFragmentAssembler assembler = new io.aeron.ImageFragmentAssembler(
                (buf, off, len, hdr) -> {
                    try {
                        byte[] bytes = new byte[len];
                        buf.getBytes(off, bytes);
                        JsonNode node = objectMapper.readTree(bytes);

                        if (node.has("ledger")) {
                            // 旧格式：单消息全量
                            legacy[0]     = true;
                            legacyData[0] = convertLedgerNode(node.get("ledger"));
                            endNode[0]    = node;
                        } else if ("users".equals(node.path("type").asText())) {
                            ledgerData.putAll(convertLedgerNode(node.get("data")));
                            chunks[0]++;
                        } else if ("end".equals(node.path("type").asText())) {
                            endNode[0] = node;
                        }
                    } catch (IOException e) {
                        throw new RuntimeException("Snapshot fragment parse failed", e);
                    }
                });

        while (!snapshotImage.isEndOfStream()) {
            int fragments = snapshotImage.poll(assembler, 10);
            if (fragments <= 0) Thread.yield();
        }

        if (endNode[0] == null) {
            if (chunks[0] == 0 && !legacy[0]) return;   // 空快照
            throw new IllegalStateException("Snapshot incomplete: end marker missing");
        }

        JsonNode end = endNode[0];
        if (!legacy[0]) {
            int expected = end.path("userChunks").asInt(-1);
            if (expected >= 0 && expected != chunks[0]) {
                throw new IllegalStateException(String.format(
                        "Snapshot incomplete: expected %d user chunks, got %d", expected, chunks[0]));
            }
        }

        Map<String, Long> bizNos = objectMapper.convertValue(
                end.get("bizNos"),
                objectMapper.getTypeFactory().constructMapType(Map.class, String.class, Long.class));

        List<String> permanentBizNos = end.has("permanentBizNos")
                ? objectMapper.convertValue(end.get("permanentBizNos"),
                        objectMapper.getTypeFactory().constructCollectionType(List.class, String.class))
                : null;

        long archivePos = end.path("matchArchivePosition").asLong(0L);
        long restoredSeq = end.path("seq").asLong(0L);

        ledger.restore(legacy[0] ? legacyData[0] : ledgerData, bizNos, permanentBizNos, archivePos, restoredSeq);
        // 参考时间用 cluster.time()，不能用 archivePos（那是字节位点，不是时间戳）
        eventReporter.record(CoreSystemEvent.SNAPSHOT_RESTORED, cluster.time(),
                () -> "legacy=" + legacy[0] + " chunks=" + chunks[0]
                        + " matchArchivePosition=" + archivePos
                        + " seq=" + restoredSeq
                        + " bizNos=" + ledger.processedBizNosSize());
        log.info("[AssetCluster] Snapshot restored — legacy={}, chunks={}, matchArchivePosition={}, bizNos={}",
                legacy[0], chunks[0], archivePos, ledger.processedBizNosSize());
    }

    /** JSON 节点 → userId(Long) → accountType(String) → asset(String) → Balance */
    private Map<Long, Map<String, Map<String, Balance>>> convertLedgerNode(JsonNode node) {
        return objectMapper.convertValue(
                node,
                objectMapper.getTypeFactory().constructMapType(
                        Map.class,
                        objectMapper.getTypeFactory().constructType(Long.class),
                        objectMapper.getTypeFactory().constructMapType(
                                Map.class,
                                objectMapper.getTypeFactory().constructType(String.class),
                                objectMapper.getTypeFactory().constructMapType(
                                        Map.class, String.class, Balance.class))));
    }

    // =========================================================================
    // Role / terminate
    // =========================================================================

    @Override
    public void onRoleChange(Cluster.Role newRole) {
        if (runtimeStatus != null) runtimeStatus.updateRole(newRole.name());
        eventReporter.record(CoreSystemEvent.CLUSTER_ROLE_CHANGED, cluster.time(),
                () -> "memberId=" + cluster.memberId() + " newRole=" + newRole);
        log.info("[AssetCluster] Role changed → {}", newRole);
    }

    @Override
    public void onTerminate(Cluster cluster) {
        log.info("[AssetCluster] Terminating");
    }

    // =========================================================================
    // Private helpers
    // =========================================================================

    /**
     * 从 JSON 节点解析 accountType，字段不存在或不合法时返回 defaultType。
     *
     * <p>仅用于<b>查询和委托类</b>操作。资金写操作（CREDIT/DEBIT/TRANSFER）
     * 必须用 {@link #requireAccountType}——静默 fallback 会把钱记入错误账户。
     */
    private AccountType parseAccountType(JsonNode req, AccountType defaultType) {
        if (req.has("accountType")) {
            try {
                return AccountType.valueOf(req.get("accountType").asText());
            } catch (IllegalArgumentException e) {
                log.warn("[AssetCluster] Unknown accountType: {}, using default={}",
                        req.get("accountType").asText(), defaultType);
            }
        }
        return defaultType;
    }

    /**
     * 严格解析必填的 accountType 字段（资金写操作专用）。
     *
     * @throws IllegalStateException 字段缺失或值非法——直接拒绝，绝不 fallback
     */
    private AccountType requireAccountType(JsonNode req, String field, String opName) {
        if (!req.has(field)) {
            reportInvalid(opName, field, "missing");
            throw new IllegalStateException("[" + opName + "] missing required field: " + field);
        }
        String raw = req.get(field).asText();
        try {
            return AccountType.valueOf(raw);
        } catch (IllegalArgumentException e) {
            reportInvalid(opName, field, raw);
            throw new IllegalStateException("[" + opName + "] invalid " + field + ": " + raw);
        }
    }

    /**
     * 上报参数非法事件。
     *
     * <p>期望恒为 0：网关侧应已完成强校验，状态机再次拒绝说明网关有遗漏。
     * 此处用 {@code cluster.time()} 而非 wall-clock，保持状态机内时间来源统一。
     */
    private void reportInvalid(String opName, String field, String value) {
        eventReporter.record(CoreSystemEvent.REQUEST_INVALID, cluster.time(),
                () -> "op=" + opName + " field=" + field + " value=" + value);
    }

    private void checkBizNoExpiry(String bizNoId, long nowMs, String opName) {
        if (SnowflakeId.isExpired(bizNoId, BalanceLedger.BIZNO_TTL_MS, nowMs)) {
            // 确定性：错误消息也使用 cluster timestamp 作为参考
            long snowflakeTs = SnowflakeId.tryExtractTimestampMs(bizNoId, nowMs);
            // 期望恒为 0：持续非零说明合法请求被误杀，需调大 BIZNO_TTL_MS 或排查 Ingress 积压
            long age = nowMs - snowflakeTs;
            eventReporter.record(CoreSystemEvent.REQUEST_EXPIRED, nowMs,
                    () -> "op=" + opName + " bizNo=" + bizNoId
                            + " age=" + age + "ms ttl=" + BalanceLedger.BIZNO_TTL_MS + "ms");
            throw new IllegalStateException(String.format(
                    "[%s] bizNo expired: id=%s, age=%dms, ttl=%dms",
                    opName, bizNoId, nowMs - snowflakeTs, BalanceLedger.BIZNO_TTL_MS));
        }
    }

    private void publishIfLeader(AssetStateChangeEvent event) {
        if (cluster.role() == Cluster.Role.LEADER && eventPublisher != null) {
            eventPublisher.publish(event);
        }
    }

    private void publishSettleEvents(Long buyerId, Long sellerId,
                                     AccountType accountType,
                                     String baseAsset, String quoteAsset,
                                     BigDecimal qty, BigDecimal quoteAmt,
                                     BigDecimal buyFee, BigDecimal sellFee,
                                     String tradeId, long timestamp) {
        Balance bq = ledger.getBalance(buyerId,  accountType, quoteAsset);
        Balance bb = ledger.getBalance(buyerId,  accountType, baseAsset);
        Balance sb = ledger.getBalance(sellerId, accountType, baseAsset);
        Balance sq = ledger.getBalance(sellerId, accountType, quoteAsset);

        eventPublisher.publish(AssetStateChangeEvent.builder()
                .eventId("SETTLE_BUY_DEDUCT:" + tradeId + ":" + buyerId + ":" + accountType + ":" + quoteAsset)
                .eventType("SETTLE_BUYER_DEDUCT").userId(buyerId).accountType(accountType).asset(quoteAsset)
                .available(bq.getAvailable()).frozen(bq.getFrozen())
                .amount(quoteAmt.add(buyFee).negate()).flowType(FundFlowType.TRADE_DEDUCT)
                .bizNo(tradeId).remark("settle buy deduct").clusterTimestamp(timestamp).build());

        eventPublisher.publish(AssetStateChangeEvent.builder()
                .eventId("SETTLE_BUY_CREDIT:" + tradeId + ":" + buyerId + ":" + accountType + ":" + baseAsset)
                .eventType("SETTLE_BUYER_CREDIT").userId(buyerId).accountType(accountType).asset(baseAsset)
                .available(bb.getAvailable()).frozen(bb.getFrozen())
                .amount(qty).flowType(FundFlowType.TRADE_CREDIT)
                .bizNo(tradeId).remark("settle buy credit").clusterTimestamp(timestamp).build());

        eventPublisher.publish(AssetStateChangeEvent.builder()
                .eventId("SETTLE_SELL_DEDUCT:" + tradeId + ":" + sellerId + ":" + accountType + ":" + baseAsset)
                .eventType("SETTLE_SELLER_DEDUCT").userId(sellerId).accountType(accountType).asset(baseAsset)
                .available(sb.getAvailable()).frozen(sb.getFrozen())
                .amount(qty.negate()).flowType(FundFlowType.TRADE_DEDUCT)
                .bizNo(tradeId).remark("settle sell deduct").clusterTimestamp(timestamp).build());

        eventPublisher.publish(AssetStateChangeEvent.builder()
                .eventId("SETTLE_SELL_CREDIT:" + tradeId + ":" + sellerId + ":" + accountType + ":" + quoteAsset)
                .eventType("SETTLE_SELLER_CREDIT").userId(sellerId).accountType(accountType).asset(quoteAsset)
                .available(sq.getAvailable()).frozen(sq.getFrozen())
                .amount(quoteAmt.subtract(sellFee)).flowType(FundFlowType.TRADE_CREDIT)
                .bizNo(tradeId).remark("settle sell credit").clusterTimestamp(timestamp).build());
    }

    private JsonNode parseJson(DirectBuffer buffer, int offset, int length) throws IOException {
        byte[] bytes = new byte[length];
        buffer.getBytes(offset, bytes);
        return objectMapper.readTree(bytes);
    }

    /** Egress offer 最大重试次数。超限放弃回包（客户端超时重试），绝不无限自旋阻塞状态机。 */
    private static final int EGRESS_OFFER_MAX_RETRIES = 100_000;

    /**
     * 发送 Egress 响应（有界重试）。
     *
     * <p>回包失败只影响单个客户端（其 Gateway future 超时后自行重试），
     * 账本状态已通过 Raft 提交，安全。因此这里<b>绝不允许无限自旋</b>——
     * 那会阻塞 Cluster Service Thread，让所有用户的资产操作停摆。
     */
    private void sendEgress(ClientSession session, byte msgType, String json) {
        byte[] jsonBytes = json.getBytes(StandardCharsets.UTF_8);
        final long now = cluster.time();
        if (1 + jsonBytes.length > egressBuffer.capacity()) {
            int len = jsonBytes.length;
            eventReporter.record(CoreSystemEvent.EGRESS_DROPPED, now,
                    () -> "reason=payload_too_large bytes=" + len);
            log.error("[AssetCluster] Egress payload too large ({} bytes), dropping response msgType=0x{}",
                    jsonBytes.length, Integer.toHexString(msgType & 0xFF));
            return;
        }
        egressBuffer.putByte(0, msgType);
        egressBuffer.putBytes(1, jsonBytes);
        long result;
        int  retries = 0;
        while ((result = session.offer(egressBuffer, 0, 1 + jsonBytes.length)) < 0) {
            if (result == io.aeron.Publication.CLOSED
                    || result == io.aeron.Publication.NOT_CONNECTED
                    || result == io.aeron.Publication.MAX_POSITION_EXCEEDED) {
                final long r = result;
                eventReporter.record(CoreSystemEvent.EGRESS_DROPPED, now,
                        () -> "reason=unavailable result=" + r);
                log.warn("[AssetCluster] Egress unavailable (result={}), dropping response", result);
                return;
            }
            if (++retries > EGRESS_OFFER_MAX_RETRIES) {
                eventReporter.record(CoreSystemEvent.EGRESS_DROPPED, now,
                        () -> "reason=backpressure_timeout retries=" + EGRESS_OFFER_MAX_RETRIES);
                log.error("[AssetCluster] Egress offer timed out after {} retries, dropping response", retries);
                return;
            }
            Thread.yield();
        }
    }

    private byte resolveFailType(byte ingressType) {
        return switch (ingressType) {
            case AssetMsgType.FREEZE               -> AssetMsgType.FREEZE_FAIL;
            case AssetMsgType.SETTLE_TRADE         -> AssetMsgType.SETTLE_FAIL;
            case AssetMsgType.BATCH_FREEZE         -> AssetMsgType.BATCH_FREEZE_RESP;
            case AssetMsgType.BATCH_SETTLE         -> AssetMsgType.BATCH_SETTLE_RESP;
            case AssetMsgType.MATCH_POSITION_QUERY -> AssetMsgType.MATCH_POSITION_RESP;
            case AssetMsgType.CREDIT               -> AssetMsgType.CREDIT_FAIL;
            case AssetMsgType.DEBIT                -> AssetMsgType.DEBIT_FAIL;
            case AssetMsgType.INTERNAL_TRANSFER    -> AssetMsgType.TRANSFER_FAIL;
            default                                -> AssetMsgType.ERROR;
        };
    }
}
