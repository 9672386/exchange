package com.exchange.account.core.gateway;

import com.exchange.account.api.dto.AssetDTO;
import com.exchange.account.api.dto.CreditDebitReq;
import com.exchange.account.api.dto.FreezeReq;
import com.exchange.account.api.dto.InternalTransferReq;
import com.exchange.account.api.enums.AccountType;
import com.exchange.account.core.cluster.protocol.AssetMsgType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.aeron.cluster.client.AeronCluster;
import io.aeron.cluster.client.EgressListener;
import io.aeron.driver.MediaDriver;
import io.aeron.logbuffer.Header;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Asset Cluster 网关服务（同步请求/响应 + 分片路由）。
 *
 * <h3>核心机制：correlationId 同步等待</h3>
 * <pre>
 *   HTTP 线程
 *     │ 1. 生成 correlationId
 *     │ 2. 注册 CompletableFuture
 *     │ 3. 将编码消息放入 per-shard OfferQueue
 *     │ 4. future.get(timeout)
 *     │                      ▼
 *     │             EgressPoller 线程（per shard）
 *     │               │ 1. 从 OfferQueue 取出 → client.offer()
 *     │               │ 2. client.pollEgress() → EgressListener.onMessage()
 *     │               │ 3. 解析 correlationId → 完成对应 future
 *     │ 5. ← 收到结果
 * </pre>
 *
 * <h3>线程安全</h3>
 * <p>旧实现用一个共享 {@code UnsafeBuffer sendBuffer}，多个 HTTP 线程并发写入导致数据损坏，
 * 且 {@code AeronCluster.offer()} 和 {@code pollEgress()} 同时在不同线程执行违反 Aeron 单线程约定。
 *
 * <p>新实现：HTTP 线程把编码好的 {@code byte[]} 放入
 * {@code BlockingQueue<OfferRequest>}（per shard）；EgressPoller 线程持有私有缓冲区，
 * 统一负责 {@code offer()} 和 {@code pollEgress()}，消除所有并发问题。
 *
 * <h3>分片路由</h3>
 * <p>每个 {@link ShardRouter#getTotalShards()} 分片维护一个独立的 AeronCluster 客户端
 * 和 EgressPoller 线程。请求按 {@code userId} 哈希路由到对应分片。单节点时只有 shard 0。
 */
@Slf4j
@Service
public class AssetGatewayService {

    private static final long TIMEOUT_MS = 5_000;

    /** Ingress offer 反压重试上限（超限失败该请求，避免 poller 线程被单个请求占死）。 */
    private static final int OFFER_MAX_RETRIES = 100_000;

    private final ShardRouter  shardRouter;
    private final ObjectMapper objectMapper;

    /** shard → AeronCluster client */
    private final List<AeronCluster>               clusterClients = new ArrayList<>();
    /** shard → MediaDriver（每个 shard 独立驱动） */
    private final List<MediaDriver>                mediaDrivers   = new ArrayList<>();
    /**
     * shard → per-shard Offer 队列。
     *
     * <p>HTTP 线程将编码后的消息放入队列；EgressPoller 线程（该 shard 的唯一写线程）
     * 从队列取出后调用 {@code client.offer()}，保证 Aeron 单线程访问约束。
     */
    private final List<BlockingQueue<OfferRequest>> offerQueues   = new ArrayList<>();
    /** correlationId → pending future（跨分片共享，correlationId 全局唯一） */
    private final ConcurrentHashMap<String, CompletableFuture<String>> pendingRequests = new ConcurrentHashMap<>();

    private final List<Thread>  egressPollers = new ArrayList<>();
    private final AtomicBoolean running       = new AtomicBoolean(false);

    public AssetGatewayService(ShardRouter shardRouter) {
        this.shardRouter  = shardRouter;
        this.objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    @PostConstruct
    public void connect() {
        running.set(true);
        for (int shard = 0; shard < shardRouter.getTotalShards(); shard++) {
            final int shardId = shard;
            String ingress = shardRouter.getIngress(shard);
            log.info("[AssetGateway] Connecting shard={} ingress={}", shard, ingress);

            MediaDriver driver = MediaDriver.launchEmbedded();
            mediaDrivers.add(driver);

            AeronCluster client = AeronCluster.connect(
                    new AeronCluster.Context()
                            .aeronDirectoryName(driver.aeronDirectoryName())
                            .ingressChannel(ingress)
                            .egressListener(new GatewayEgressListener(shardId)));
            clusterClients.add(client);

            // 每个 shard 独立的 Offer 队列（无界，HTTP 线程不阻塞）
            BlockingQueue<OfferRequest> queue = new LinkedBlockingQueue<>();
            offerQueues.add(queue);

            // EgressPoller 线程：唯一负责该 shard 的 offer() + pollEgress()
            Thread poller = new Thread(() -> {
                // 私有缓冲区（单线程，无需同步）
                UnsafeBuffer localBuffer = new UnsafeBuffer(ByteBuffer.allocateDirect(65536));
                while (running.get()) {
                    // 1. 先排空 offer 队列，把 HTTP 线程的请求发送给 Cluster
                    OfferRequest req;
                    while ((req = queue.poll()) != null) {
                        localBuffer.putBytes(0, req.data(), 0, req.data().length);
                        long result;
                        int  retries = 0;
                        while ((result = client.offer(localBuffer, 0, req.data().length)) < 0) {
                            if (result == io.aeron.Publication.CLOSED
                                    || result == io.aeron.Publication.NOT_CONNECTED
                                    || result == io.aeron.Publication.MAX_POSITION_EXCEEDED) {
                                // Cluster 连接已关闭，立即失败对应的 future
                                failPending(req.correlationId(),
                                        "Asset Cluster not connected, shard=" + shardId);
                                break;
                            }
                            if (++retries > OFFER_MAX_RETRIES) {
                                // 反压超限：失败该请求，不能永久占住 poller 线程
                                failPending(req.correlationId(),
                                        "Asset Cluster ingress back-pressured, shard=" + shardId);
                                break;
                            }
                            // 反压期间必须继续 poll egress：
                            // 否则已发出请求的响应无人消费，全部 5s 超时
                            try { client.pollEgress(); } catch (Exception ignored) {}
                            Thread.yield();
                        }
                    }
                    // 2. 再 poll egress，处理来自 Cluster 的响应
                    try {
                        client.pollEgress();
                    } catch (Exception e) {
                        if (running.get()) {
                            log.error("[AssetGateway] EgressPoller shard={} error", shardId, e);
                        }
                    }
                    Thread.yield();
                }
            }, "asset-egress-poller-" + shard);
            poller.setDaemon(true);
            poller.start();
            egressPollers.add(poller);

            log.info("[AssetGateway] Shard={} connected", shard);
        }
    }

    @PreDestroy
    public void disconnect() {
        running.set(false);
        clusterClients.forEach(AeronCluster::close);
        mediaDrivers.forEach(MediaDriver::close);
        log.info("[AssetGateway] Disconnected all shards");
    }

    // =========================================================================
    // Public API（供 Controller 调用）
    // =========================================================================

    /**
     * 查询单资产余额（直接读内存账本，RT &lt; 5 ms）。
     */
    public AssetDTO queryBalance(Long userId, AccountType accountType, String asset) throws Exception {
        String correlationId = correlationId();
        Map<String, Object> req = new HashMap<>();
        req.put("correlationId", correlationId);
        req.put("userId", userId);
        req.put("accountType", accountType.name());
        req.put("asset", asset);

        String respJson = sendAndWait(userId, AssetMsgType.BALANCE_QUERY, req, correlationId);
        JsonNode resp = objectMapper.readTree(respJson);
        return buildAssetDTO(userId, accountType, asset, resp);
    }

    /**
     * 查询用户某账户类型下所有资产余额。
     */
    public List<AssetDTO> queryAllBalances(Long userId, AccountType accountType) throws Exception {
        String correlationId = correlationId();
        Map<String, Object> req = new HashMap<>();
        req.put("correlationId", correlationId);
        req.put("userId", userId);
        req.put("accountType", accountType.name());

        String respJson = sendAndWait(userId, AssetMsgType.BALANCE_QUERY, req, correlationId);
        JsonNode resp = objectMapper.readTree(respJson);
        return buildAllBalanceDTOs(userId, accountType, resp);
    }

    /**
     * 查询用户所有账户类型下所有资产余额（汇总视图）。
     */
    public List<AssetDTO> queryAllBalancesByType(Long userId) throws Exception {
        String correlationId = correlationId();
        Map<String, Object> req = new HashMap<>();
        req.put("correlationId", correlationId);
        req.put("userId", userId);
        // 不传 accountType → Cluster 返回全部账户类型

        String respJson = sendAndWait(userId, AssetMsgType.BALANCE_QUERY, req, correlationId);
        JsonNode resp = objectMapper.readTree(respJson);
        return buildAllBalancesByTypeDTOs(userId, resp);
    }

    /**
     * 冻结资产（同步等待 FREEZE_OK / FREEZE_FAIL）。
     */
    public void freeze(FreezeReq req) throws Exception {
        String correlationId = correlationId();
        Map<String, Object> body = new HashMap<>();
        body.put("correlationId", correlationId);
        body.put("userId", req.getUserId());
        body.put("accountType", req.getAccountType().name());
        body.put("asset", req.getAsset());
        body.put("amount", req.getAmount().toPlainString());
        body.put("orderId", req.getOrderId());

        String resp = sendAndWait(req.getUserId(), AssetMsgType.FREEZE, body, correlationId);
        checkStatus(resp, "FREEZE");
    }

    /**
     * 解冻资产（同步等待 UNFREEZE_OK）。
     */
    public void unfreeze(FreezeReq req) throws Exception {
        String correlationId = correlationId();
        Map<String, Object> body = new HashMap<>();
        body.put("correlationId", correlationId);
        body.put("userId", req.getUserId());
        body.put("accountType", req.getAccountType().name());
        body.put("asset", req.getAsset());
        body.put("amount", req.getAmount().toPlainString());
        body.put("orderId", req.getOrderId());

        String resp = sendAndWait(req.getUserId(), AssetMsgType.UNFREEZE, body, correlationId);
        checkStatus(resp, "UNFREEZE");
    }

    /**
     * 批量冻结（单用户，多订单，原子）。
     *
     * @param userId      用户 ID
     * @param accountType 账户类型（批次默认值，子项可覆盖）
     * @param items       冻结子项列表，每项包含 orderId / asset / amount
     * @throws Exception 余额不足或超时
     */
    public void batchFreeze(Long userId, AccountType accountType,
                            List<Map<String, Object>> items) throws Exception {
        String correlationId = correlationId();
        Map<String, Object> body = new HashMap<>();
        body.put("correlationId", correlationId);
        body.put("userId", userId);
        body.put("accountType", accountType.name());
        body.put("items", items);

        String resp = sendAndWait(userId, AssetMsgType.BATCH_FREEZE, body, correlationId);
        checkStatus(resp, "BATCH_FREEZE");
    }

    /**
     * 批量结算（多用户对）。
     *
     * <p>将多笔 trade 打包成一条 Ingress 消息，一次 Raft 共识完成全部结算。
     * 正常结算不可能失败（资金在下单时已 FREEZE）。若 Cluster 响应 status != OK，
     * 说明系统级 Bug，直接抛出异常，由调用方（TradeSettlementForwarder）
     * 让订阅循环退出 → 5s 后重试。
     *
     * @param routingUserId 路由用户 ID（用于分片选择；通常取第一笔 trade 的 buyerId）
     * @param trades        成交参数列表
     * @throws Exception 超时、Cluster 连接异常或结算失败（系统级错误）
     */
    public void batchSettle(Long routingUserId, List<Map<String, Object>> trades) throws Exception {
        batchSettle(routingUserId, trades, -1L);
    }

    /**
     * 批量结算（含 Archive 位点），供 TradeSettlementForwarder 调用。
     *
     * <p>{@code archivePosition} 随 BATCH_SETTLE 一同写入 Raft 日志，在 Cluster 内
     * 与结算原子提交，保证位点与账本状态天然一致。
     *
     * @param archivePosition 当前 fragment 在 Match Archive 中的 byte position；
     *                        -1 表示不携带（兼容外部调用）
     */
    public void batchSettle(Long routingUserId, List<Map<String, Object>> trades,
                            long archivePosition) throws Exception {
        String correlationId = correlationId();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("correlationId", correlationId);
        body.put("trades", trades);
        if (archivePosition >= 0) {
            body.put("archivePosition", archivePosition);
        }

        String resp = sendAndWait(routingUserId, AssetMsgType.BATCH_SETTLE, body, correlationId);
        checkStatus(resp, "BATCH_SETTLE");
    }

    /**
     * 加钱（充值到账 / 奖励发放 / 补偿），直接增加 available。
     *
     * <p>幂等键：{@code req.getBizNo()}。同一 bizNo 重复调用 Cluster 会忽略，不重复操作。
     *
     * @throws Exception 超时、Cluster 连接异常
     */
    public void credit(CreditDebitReq req) throws Exception {
        String correlationId = correlationId();
        Map<String, Object> body = new HashMap<>();
        body.put("correlationId", correlationId);
        body.put("userId", req.getUserId());
        body.put("accountType", req.getAccountType().name());
        body.put("asset", req.getAsset());
        body.put("amount", req.getAmount().toPlainString());
        body.put("bizNo", req.getBizNo());
        if (req.getRemark() != null) body.put("remark", req.getRemark());

        String resp = sendAndWait(req.getUserId(), AssetMsgType.CREDIT, body, correlationId);
        checkStatus(resp, "CREDIT");
    }

    /**
     * 减钱（提现扣款 / 风控扣罚），直接减少 available（不经过冻结流程）。
     *
     * <p>若可用余额不足，Cluster 返回错误，Gateway 抛出 {@link IllegalStateException}。
     *
     * @throws Exception 余额不足、超时或 Cluster 连接异常
     */
    public void debit(CreditDebitReq req) throws Exception {
        String correlationId = correlationId();
        Map<String, Object> body = new HashMap<>();
        body.put("correlationId", correlationId);
        body.put("userId", req.getUserId());
        body.put("accountType", req.getAccountType().name());
        body.put("asset", req.getAsset());
        body.put("amount", req.getAmount().toPlainString());
        body.put("bizNo", req.getBizNo());
        if (req.getRemark() != null) body.put("remark", req.getRemark());

        String resp = sendAndWait(req.getUserId(), AssetMsgType.DEBIT, body, correlationId);
        checkStatus(resp, "DEBIT");
    }

    /**
     * 同用户跨账户类型内部划转（如 FUNDING→SPOT，原子操作）。
     *
     * <p>出账和入账在单次 Raft 日志写入中完成，bizNo 作为幂等键（Snowflake 格式）。
     *
     * @throws Exception 余额不足、fromType == toType、超时或 Cluster 连接异常
     */
    public void internalTransfer(InternalTransferReq req) throws Exception {
        String correlationId = correlationId();
        Map<String, Object> body = new HashMap<>();
        body.put("correlationId", correlationId);
        body.put("userId", req.getUserId());
        body.put("fromAccountType", req.getFromAccountType().name());
        body.put("toAccountType", req.getToAccountType().name());
        body.put("asset", req.getAsset());
        body.put("amount", req.getAmount().toPlainString());
        body.put("bizNo", req.getBizNo());
        if (req.getRemark() != null) body.put("remark", req.getRemark());

        String resp = sendAndWait(req.getUserId(), AssetMsgType.INTERNAL_TRANSFER, body, correlationId);
        checkStatus(resp, "INTERNAL_TRANSFER");
    }

    /**
     * 查询 Asset Cluster 中持久化的 Match Archive 消费位点。
     *
     * <p>TradeSettlementForwarder 启动时调用，取回上次 Raft 提交的 byte position，
     * 从该点续读 Match Archive，保证"结算已提交"与"位点前进"严格原子。
     *
     * @param routingUserId 路由用户（用于分片选择；单节点传 0L 即可）
     * @return Archive byte position（0 表示从头开始）
     */
    public long queryMatchArchivePosition(Long routingUserId) throws Exception {
        String correlationId = correlationId();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("correlationId", correlationId);

        String respJson = sendAndWait(routingUserId, AssetMsgType.MATCH_POSITION_QUERY, body, correlationId);
        JsonNode resp = objectMapper.readTree(respJson);
        if (resp.has("error")) {
            throw new IllegalStateException("MATCH_POSITION_QUERY error: " + resp.get("error").asText());
        }
        return resp.path("position").asLong(0L);
    }

    // =========================================================================
    // Private helpers
    // =========================================================================

    /**
     * 序列化请求体并将其放入 per-shard Offer 队列，然后等待 EgressPoller 的响应。
     *
     * <p>此方法可安全地被多个 HTTP 线程并发调用：
     * <ul>
     *   <li>每个线程仅使用局部 {@code byte[]}（无共享缓冲区）。</li>
     *   <li>{@code client.offer()} 由 EgressPoller 单线程完成。</li>
     *   <li>{@code CompletableFuture} 写入由 EgressPoller 线程完成。</li>
     * </ul>
     */
    private String sendAndWait(Long userId, byte msgType,
                               Map<String, Object> body, String correlationId) throws Exception {
        int shardId = shardRouter.getShardId(userId);

        // 将消息编码为 [msgType][JSON] 字节数组（线程局部，无共享）
        byte[] jsonBytes = objectMapper.writeValueAsBytes(body);
        byte[] data = new byte[1 + jsonBytes.length];
        data[0] = msgType;
        System.arraycopy(jsonBytes, 0, data, 1, jsonBytes.length);

        CompletableFuture<String> future = new CompletableFuture<>();
        pendingRequests.put(correlationId, future);
        try {
            // 将请求放入 per-shard 队列，由 EgressPoller 线程完成 offer
            offerQueues.get(shardId).put(new OfferRequest(data, correlationId));
            return future.get(TIMEOUT_MS, TimeUnit.MILLISECONDS);
        } finally {
            pendingRequests.remove(correlationId);
        }
    }

    private void checkStatus(String respJson, String op) throws Exception {
        JsonNode resp = objectMapper.readTree(respJson);
        if (resp.has("error")) {
            throw new IllegalStateException("[" + op + "] Asset Cluster error: " + resp.get("error").asText());
        }
    }

    private AssetDTO buildAssetDTO(Long userId, AccountType accountType, String asset, JsonNode resp) {
        AssetDTO dto = new AssetDTO();
        dto.setUserId(userId);
        dto.setAccountType(accountType);
        dto.setAsset(asset);
        BigDecimal available = new BigDecimal(resp.path("available").asText("0"));
        BigDecimal frozen    = new BigDecimal(resp.path("frozen").asText("0"));
        dto.setAvailableBalance(available);
        dto.setFrozenBalance(frozen);
        dto.setTotalBalance(available.add(frozen));
        return dto;
    }

    private List<AssetDTO> buildAllBalanceDTOs(Long userId, AccountType accountType, JsonNode resp) {
        List<AssetDTO> list = new ArrayList<>();
        JsonNode balances = resp.path("balances");
        if (balances.isObject()) {
            balances.fields().forEachRemaining(entry -> {
                String   assetCode = entry.getKey();
                JsonNode bal       = entry.getValue();
                AssetDTO dto = new AssetDTO();
                dto.setUserId(userId);
                dto.setAccountType(accountType);
                dto.setAsset(assetCode);
                BigDecimal available = new BigDecimal(bal.path("available").asText("0"));
                BigDecimal frozen    = new BigDecimal(bal.path("frozen").asText("0"));
                dto.setAvailableBalance(available);
                dto.setFrozenBalance(frozen);
                dto.setTotalBalance(available.add(frozen));
                list.add(dto);
            });
        }
        return list;
    }

    private List<AssetDTO> buildAllBalancesByTypeDTOs(Long userId, JsonNode resp) {
        List<AssetDTO> list = new ArrayList<>();
        JsonNode allBalances = resp.path("allBalances");
        if (allBalances.isObject()) {
            allBalances.fields().forEachRemaining(typeEntry -> {
                AccountType accountType;
                try {
                    accountType = AccountType.valueOf(typeEntry.getKey());
                } catch (IllegalArgumentException e) {
                    log.warn("[AssetGateway] Unknown accountType in response: {}", typeEntry.getKey());
                    return;
                }
                JsonNode assets = typeEntry.getValue();
                if (assets.isObject()) {
                    assets.fields().forEachRemaining(assetEntry -> {
                        String   assetCode = assetEntry.getKey();
                        JsonNode bal       = assetEntry.getValue();
                        AssetDTO dto = new AssetDTO();
                        dto.setUserId(userId);
                        dto.setAccountType(accountType);
                        dto.setAsset(assetCode);
                        BigDecimal available = new BigDecimal(bal.path("available").asText("0"));
                        BigDecimal frozen    = new BigDecimal(bal.path("frozen").asText("0"));
                        dto.setAvailableBalance(available);
                        dto.setFrozenBalance(frozen);
                        dto.setTotalBalance(available.add(frozen));
                        list.add(dto);
                    });
                }
            });
        }
        return list;
    }

    private static String correlationId() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    /** 以异常完成 pending future（offer 失败路径）。 */
    private void failPending(String correlationId, String reason) {
        CompletableFuture<String> f = pendingRequests.remove(correlationId);
        if (f != null) {
            f.completeExceptionally(new IllegalStateException(reason));
        }
    }

    // =========================================================================
    // 内部类型
    // =========================================================================

    /**
     * per-shard Offer 请求载体（不可变）。
     *
     * @param data          编码后的消息字节：[msgType][JSON body]
     * @param correlationId 请求标识，用于 offer 失败时完成对应的 future
     */
    private record OfferRequest(byte[] data, String correlationId) {}

    // =========================================================================
    // EgressListener（每个 shard 一个实例，在 EgressPoller 线程中回调）
    // =========================================================================

    private class GatewayEgressListener implements EgressListener {

        private final int shardId;

        GatewayEgressListener(int shardId) {
            this.shardId = shardId;
        }

        @Override
        public void onMessage(long clusterSessionId, long timestamp,
                              DirectBuffer buffer, int offset, int length, Header header) {
            if (length < 1) return;
            // byte 0 = msgType, rest = JSON
            byte[] jsonBytes = new byte[length - 1];
            buffer.getBytes(offset + 1, jsonBytes);
            String json = new String(jsonBytes, StandardCharsets.UTF_8);

            try {
                JsonNode node = objectMapper.readTree(json);
                String correlationId = node.path("correlationId").asText("");
                if (!correlationId.isEmpty()) {
                    CompletableFuture<String> future = pendingRequests.remove(correlationId);
                    if (future != null) {
                        future.complete(json);
                        log.debug("[AssetGateway] Egress matched correlationId={} shard={}", correlationId, shardId);
                    } else {
                        log.warn("[AssetGateway] No pending request for correlationId={}", correlationId);
                    }
                }
            } catch (Exception e) {
                log.error("[AssetGateway] Failed to parse egress message shard={}", shardId, e);
            }
        }
    }
}
