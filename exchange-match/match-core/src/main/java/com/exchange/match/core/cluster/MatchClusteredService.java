package com.exchange.match.core.cluster;

import com.exchange.common.event.CoreSystemEvent;
import com.exchange.common.event.SystemEventReporter;
import com.exchange.common.id.SnowflakeId;
import com.exchange.match.core.cluster.snapshot.ClusterMatchSnapshot;
import com.exchange.match.core.memory.MemoryManager;
import com.exchange.match.core.memory.MemoryStats;
import com.exchange.match.core.model.*;
import com.exchange.match.core.service.MatchEngineService;
import com.exchange.match.core.transport.AeronMatchResultPublisher;
import com.exchange.match.request.EventCanalReq;
import com.exchange.match.request.EventNewOrderReq;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.aeron.ExclusivePublication;
import io.aeron.Image;
import io.aeron.cluster.service.ClientSession;
import io.aeron.cluster.service.Cluster;
import io.aeron.cluster.service.ClusteredService;
import io.aeron.logbuffer.Header;
import lombok.extern.slf4j.Slf4j;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 撮合引擎 Aeron Cluster 服务。
 *
 * <h3>职责</h3>
 * <ul>
 *   <li>通过 Cluster Ingress 接收来自 Order Service 的 NewOrderEvent / CancelOrderEvent。</li>
 *   <li>调用现有 {@link MatchEngineService} 执行内存撮合（复用现有撮合算法，零修改）。</li>
 *   <li>撮合结果通过两条路径输出：
 *     <ol>
 *       <li>Cluster Egress → 回包给 Order Service（确认收单 / 拒绝通知）。</li>
 *       <li>Aeron MDC Publication → 广播 TradeEvent 给 Asset / Risk / Quote 服务。</li>
 *     </ol>
 *   </li>
 *   <li>实现 {@code onTakeSnapshot} / {@code onLoadSnapshot} 保证崩溃重启后状态完整恢复。</li>
 * </ul>
 *
 * <h3>确定性约束</h3>
 * <p>所有节点在相同输入下必须产生相同输出。禁止在此类中直接调用
 * {@code System.currentTimeMillis()} 或 {@code LocalDateTime.now()}；
 * 统一使用 {@code onSessionMessage} 提供的 {@code timestamp}（集群协调时间）。
 *
 * <h3>线程模型</h3>
 * <p>Aeron Cluster 框架保证 {@code onSessionMessage}、{@code onTakeSnapshot}、
 * {@code onLoadSnapshot} 均在同一单线程（Service Thread）上依次调用，无需额外同步。
 */
@Slf4j
public class MatchClusteredService implements ClusteredService {

    /**
     * 委托超时阈值：10 秒。
     *
     * <p>orderId 须为 Snowflake 格式，引擎解析其嵌入的生成时间戳；
     * 若 {@code clusterTimestamp - snowflakeTs > ORDER_TIMEOUT_MS}，立即拒绝，
     * 防止积压或延迟重放的老委托影响撮合质量。
     * 非 Snowflake 格式的 orderId（无法解析时间）跳过此校验，保持向后兼容。
     */
    private static final long ORDER_TIMEOUT_MS = 10_000L;

    // ---- 消息类型字节常量（与 AeronOrderReceiver 保持一致） ----------------------
    /** Ingress: 新建订单 */
    public static final byte MSG_NEW_ORDER   = 0x01;
    /** Ingress: 撤单 */
    public static final byte MSG_CANCEL      = 0x02;

    /** Egress: 引擎接受订单（挂单成功或全部成交） */
    public static final byte MSG_ACK         = 0x10;
    /** Egress: 引擎拒绝订单 */
    public static final byte MSG_REJECT      = 0x11;
    /** Egress: 撤单确认 */
    public static final byte MSG_CANCEL_ACK  = 0x12;

    // ---- 依赖（由 Spring 通过构造器注入，不在 ClusteredService 内使用 Spring） ---
    /** Egress offer 有界重试上限，避免无限自旋阻塞 Service Thread。 */
    private static final int  EGRESS_OFFER_MAX_RETRIES = 100_000;
    /** 冷指标（O(n) 规模统计）抽样间隔:每处理这么多条消息刷新一次。 */
    private static final long COLD_UPDATE_EVERY_N_MSG  = 1024L;

    private final MatchEngineService        matchEngineService;
    private final MemoryManager             memoryManager;
    /** 可为 null：Aeron 未启用时跳过 MDC 广播 */
    private final AeronMatchResultPublisher aeronPublisher;
    private final ObjectMapper              objectMapper;

    /** 系统事件上报器（仅观测；状态机内均传 cluster timestamp）。 */
    private final SystemEventReporter       eventReporter;
    /** 运行时状态快照（Service Thread 单写，HTTP 线程读）。可为 null。 */
    private final MatchRuntimeStatus        runtimeStatus;

    // ---- 运行时状态 ------------------------------------------------------------
    private Cluster cluster;
    /** 已处理消息计数，用于冷指标抽样（非状态机状态，仅本地观测）。 */
    private long    messageCount = 0L;

    /** 复用发送缓冲区（单线程，无需 ThreadLocal） */
    private final UnsafeBuffer egressBuffer = new UnsafeBuffer(ByteBuffer.allocateDirect(4096));

    public MatchClusteredService(MatchEngineService matchEngineService,
                                 MemoryManager memoryManager,
                                 AeronMatchResultPublisher aeronPublisher) {
        this(matchEngineService, memoryManager, aeronPublisher, SystemEventReporter.noop(), null);
    }

    public MatchClusteredService(MatchEngineService matchEngineService,
                                 MemoryManager memoryManager,
                                 AeronMatchResultPublisher aeronPublisher,
                                 SystemEventReporter eventReporter,
                                 MatchRuntimeStatus runtimeStatus) {
        this.matchEngineService = matchEngineService;
        this.memoryManager      = memoryManager;
        this.aeronPublisher     = aeronPublisher;
        this.eventReporter      = eventReporter != null ? eventReporter : SystemEventReporter.noop();
        this.runtimeStatus      = runtimeStatus;
        this.objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    // =========================================================================
    // ClusteredService lifecycle
    // =========================================================================

    @Override
    public void onStart(Cluster cluster, Image snapshotImage) {
        this.cluster = cluster;
        log.info("[MatchCluster] onStart — role={}, memberId={}",
                cluster.role(), cluster.memberId());

        if (snapshotImage != null) {
            log.info("[MatchCluster] Loading snapshot from Archive position={}",
                    snapshotImage.position());
            loadSnapshot(snapshotImage);
        } else {
            log.info("[MatchCluster] No snapshot found — starting with empty order book");
        }

        if (runtimeStatus != null) {
            runtimeStatus.markStarted(cluster.memberId(), cluster.role().name());
            publishHotStatus();
            publishColdStatus(cluster.time());
        }
    }

    @Override
    public void onSessionOpen(ClientSession session, long timestamp) {
        log.info("[MatchCluster] Session opened — sessionId={}", session.id());
    }

    @Override
    public void onSessionClose(ClientSession session, long timestamp,
                               io.aeron.cluster.codecs.CloseReason closeReason) {
        log.info("[MatchCluster] Session closed — sessionId={}, reason={}", session.id(), closeReason);
    }

    // =========================================================================
    // Core message dispatch
    // =========================================================================

    @Override
    public void onSessionMessage(ClientSession session, long timestamp,
                                 DirectBuffer buffer, int offset, int length,
                                 Header header) {
        if (length < 1) {
            log.warn("[MatchCluster] Received empty message, ignoring");
            return;
        }

        final byte msgType = buffer.getByte(offset);
        final int  jsonLen = length - 1;

        try {
            switch (msgType) {
                case MSG_NEW_ORDER  -> handleNewOrder(session, timestamp, buffer, offset + 1, jsonLen);
                case MSG_CANCEL     -> handleCancel(session, timestamp, buffer, offset + 1, jsonLen);
                default             -> {
                    eventReporter.record(CoreSystemEvent.REQUEST_INVALID, timestamp,
                            () -> "unknownMsgType=0x" + Integer.toHexString(msgType & 0xFF));
                    log.warn("[MatchCluster] Unknown msgType=0x{}", Integer.toHexString(msgType & 0xFF));
                }
            }
        } catch (Exception e) {
            log.error("[MatchCluster] Unhandled error processing msgType=0x{}",
                    Integer.toHexString(msgType & 0xFF), e);
            sendEgress(session, MSG_REJECT, "{\"error\":\"internal error\"}");
        } finally {
            afterMessage(timestamp);
        }
    }

    /**
     * 每条消息处理末尾:热更新运行时指标 + 冷指标抽样刷新。
     */
    private void afterMessage(long clusterTime) {
        messageCount++;
        publishHotStatus();
        if (messageCount % COLD_UPDATE_EVERY_N_MSG == 0) {
            publishColdStatus(clusterTime);
        }
    }

    /** 热更新 O(1) 指标(角色、位点、交易对数、订单薄数)。 */
    private void publishHotStatus() {
        if (runtimeStatus == null || cluster == null) return;
        runtimeStatus.updateHot(
                cluster.role().name(),
                cluster.memberId(),
                cluster.logPosition(),
                memoryManager.getAllSymbols().size(),
                memoryManager.getAllOrderBooks().size(),
                cluster.time());
    }

    /** 冷更新 O(n) 规模统计(活跃订单数、仓位数、成交总数)。 */
    private void publishColdStatus(long clusterTime) {
        if (runtimeStatus == null) return;
        MemoryStats stats = memoryManager.getMemoryStats();
        runtimeStatus.updateCold(
                stats.getTotalOrderCount(),
                stats.getPositionCount(),
                stats.getTotalTradeCount(),
                clusterTime);
    }

    // =========================================================================
    // Handlers
    // =========================================================================

    private void handleNewOrder(ClientSession session, long clusterTimestamp,
                                DirectBuffer buffer, int offset, int length) throws IOException {
        byte[] jsonBytes = new byte[length];
        buffer.getBytes(offset, jsonBytes);
        EventNewOrderReq req = objectMapper.readValue(jsonBytes, EventNewOrderReq.class);

        log.debug("[MatchCluster] NEW_ORDER orderId={} symbol={}", req.getOrderId(), req.getSymbol());

        // 委托超时校验：解析 orderId Snowflake 时间戳，超过 10s 直接拒绝。
        // 确定性：以 clusterTimestamp 作为合理性校验参考时间，不读 wall-clock，
        // 保证 Raft 日志重放时判定与原始执行一致（此前用单参重载会读 System.currentTimeMillis）。
        long snowflakeTs = SnowflakeId.tryExtractTimestampMs(req.getOrderId(), clusterTimestamp);
        if (snowflakeTs > 0 && clusterTimestamp - snowflakeTs > ORDER_TIMEOUT_MS) {
            final long age = clusterTimestamp - snowflakeTs;
            // 期望恒为 0：持续非零说明合法委托被超时误杀，需排查 Ingress 积压
            eventReporter.record(CoreSystemEvent.REQUEST_EXPIRED, clusterTimestamp,
                    () -> "orderId=" + req.getOrderId() + " age=" + age + "ms ttl=" + ORDER_TIMEOUT_MS + "ms");
            log.warn("[MatchCluster] ORDER TIMEOUT orderId={} age={}ms", req.getOrderId(), age);
            MatchResponse timeoutResp = new MatchResponse();
            timeoutResp.setOrderId(req.getOrderId());
            timeoutResp.setUserId(req.getUserId());
            timeoutResp.setSymbol(req.getSymbol());
            timeoutResp.setStatus(MatchStatus.REJECTED);
            timeoutResp.setErrorMessage("Order timeout: age " + age + "ms > " + ORDER_TIMEOUT_MS + "ms");
            sendEgress(session, MSG_REJECT, objectMapper.writeValueAsString(timeoutResp));
            return;
        }

        // 将集群时间注入订单（确保所有节点产生相同时间戳）
        Order order = buildOrder(req, clusterTimestamp);

        MatchResponse response = matchEngineService.submitOrder(order);

        // ① Egress 回包给 Order Service（快速确认/拒绝）
        if (response.isRejected()) {
            eventReporter.record(MatchSystemEvent.MATCH_ORDER_REJECTED, clusterTimestamp,
                    () -> "orderId=" + req.getOrderId() + " symbol=" + req.getSymbol()
                            + " reason=" + response.getErrorMessage());
            sendEgress(session, MSG_REJECT, objectMapper.writeValueAsString(response));
        } else {
            sendEgress(session, MSG_ACK, objectMapper.writeValueAsString(response));
        }

        // ② MDC 广播 TradeEvent（仅 Leader 节点广播，Follower 静默）
        if (cluster.role() == Cluster.Role.LEADER && aeronPublisher != null) {
            aeronPublisher.send(response);
        }
    }

    private void handleCancel(ClientSession session, long clusterTimestamp,
                              DirectBuffer buffer, int offset, int length) throws IOException {
        byte[] jsonBytes = new byte[length];
        buffer.getBytes(offset, jsonBytes);
        EventCanalReq req = objectMapper.readValue(jsonBytes, EventCanalReq.class);

        log.debug("[MatchCluster] CANCEL orderId={}", req.getOrderId());

        MatchResponse response = matchEngineService.cancelOrder(req.getOrderId(), req.getUserId());

        // 撤单未命中活跃订单（重复撤单/已成交/userId 不符）——观测撤单质量
        if (response.isRejected()) {
            eventReporter.record(MatchSystemEvent.MATCH_CANCEL_MISS, clusterTimestamp,
                    () -> "orderId=" + req.getOrderId() + " reason=" + response.getErrorMessage());
        }

        sendEgress(session, MSG_CANCEL_ACK, objectMapper.writeValueAsString(response));

        // 撤单也产生状态变更，通过 MDC 广播
        if (cluster.role() == Cluster.Role.LEADER && aeronPublisher != null) {
            aeronPublisher.send(response);
        }
    }

    // =========================================================================
    // Snapshot — onTakeSnapshot
    // =========================================================================

    /**
     * 将当前内存状态序列化为 JSON 写入 Aeron Archive。
     *
     * <p>内容：所有交易对配置、各订单薄中的活跃订单、所有用户仓位。
     * JSON 单块写入（MVP 方案），对于超大订单薄需拆分为多帧。
     */
    @Override
    public void onTakeSnapshot(ExclusivePublication snapshotPublication) {
        log.info("[MatchCluster] Taking snapshot at logPosition={}", cluster.logPosition());
        try {
            ClusterMatchSnapshot snapshot = buildSnapshot();
            byte[] snapshotBytes = objectMapper.writeValueAsBytes(snapshot);

            UnsafeBuffer buf = new UnsafeBuffer(snapshotBytes);
            long result;
            // 自旋重试直到 Publication 接受（不应超过 1 次）
            while ((result = snapshotPublication.offer(buf, 0, buf.capacity())) < 0) {
                log.warn("[MatchCluster] Snapshot offer back-pressured, result={}", result);
                Thread.yield();
            }
            final int symbols    = snapshot.getSymbols().size();
            final int orderBooks = snapshot.getActiveOrders().size();
            eventReporter.record(CoreSystemEvent.SNAPSHOT_TAKEN, cluster.time(),
                    () -> "symbols=" + symbols + " orderBooks=" + orderBooks
                            + " bytes=" + snapshotBytes.length);
            log.info("[MatchCluster] Snapshot written — {} bytes, symbols={}, orderBooks={}",
                    snapshotBytes.length, symbols, orderBooks);
        } catch (Exception e) {
            eventReporter.record(CoreSystemEvent.SNAPSHOT_FAILED, cluster.time(),
                    () -> "cause=" + e.getClass().getSimpleName() + ": " + e.getMessage());
            log.error("[MatchCluster] Failed to take snapshot", e);
            throw new RuntimeException("Snapshot serialization failed", e);
        }
    }

    // =========================================================================
    // Snapshot — onLoadSnapshot (called from onStart)
    // =========================================================================

    private void loadSnapshot(Image snapshotImage) {
        final byte[][] captured = {null};

        // 读取单帧 JSON（MVP：假设快照 < 1 Aeron MTU fragment）
        while (!snapshotImage.isEndOfStream()) {
            int fragments = snapshotImage.poll((buf, offset, length, hdr) -> {
                captured[0] = new byte[length];
                buf.getBytes(offset, captured[0]);
            }, 1);
            if (fragments <= 0) {
                Thread.yield();
            }
        }

        if (captured[0] == null) {
            log.warn("[MatchCluster] Snapshot image was empty, starting fresh");
            return;
        }

        try {
            ClusterMatchSnapshot snapshot = objectMapper.readValue(captured[0], ClusterMatchSnapshot.class);
            restoreFromSnapshot(snapshot);
            final int symbols    = snapshot.getSymbols().size();
            final int orderBooks = snapshot.getActiveOrders().size();
            eventReporter.record(CoreSystemEvent.SNAPSHOT_RESTORED, cluster.time(),
                    () -> "symbols=" + symbols + " orderBooks=" + orderBooks
                            + " logPosition=" + snapshot.getLogPosition());
            log.info("[MatchCluster] Snapshot restored — symbols={}, orderBooks={}", symbols, orderBooks);
        } catch (IOException e) {
            throw new RuntimeException("Snapshot deserialization failed", e);
        }
    }

    // =========================================================================
    // Timer / role / terminate
    // =========================================================================

    @Override
    public void onTimerEvent(long correlationId, long timestamp) {
        // 预留：可用于定期发布盘口深度快照
    }

    @Override
    public void onRoleChange(Cluster.Role newRole) {
        if (runtimeStatus != null) runtimeStatus.updateRole(newRole.name());
        eventReporter.record(CoreSystemEvent.CLUSTER_ROLE_CHANGED, cluster.time(),
                () -> "memberId=" + cluster.memberId() + " newRole=" + newRole);
        log.info("[MatchCluster] Role changed → {}", newRole);
        // Leader 切换后 MDC Publisher 由新 Leader 继续广播
    }

    @Override
    public void onTerminate(Cluster cluster) {
        log.info("[MatchCluster] Cluster terminating");
    }

    // =========================================================================
    // Private helpers
    // =========================================================================

    /**
     * 将 Ingress 请求 DTO 转换为引擎内部 Order 对象。
     * 使用集群协调时间替代 {@code LocalDateTime.now()}，保证所有副本时间戳一致。
     */
    private Order buildOrder(EventNewOrderReq req, long clusterTimestamp) {
        LocalDateTime clusterTime = LocalDateTime.ofInstant(
                Instant.ofEpochMilli(clusterTimestamp), ZoneId.of("UTC"));

        Order order = new Order();
        order.setOrderId(req.getOrderId());
        order.setUserId(req.getUserId());
        order.setSymbol(req.getSymbol());
        order.setPrice(req.getPrice());
        order.setQuantity(req.getQuantity());
        order.setClientOrderId(req.getClientOrderId());
        order.setRemark(req.getRemark());
        order.setCreateTime(clusterTime);   // 覆盖构造器中的 LocalDateTime.now()
        order.setUpdateTime(clusterTime);

        if (req.getSide() != null) {
            order.setSide(OrderSide.valueOf(req.getSide()));
        }
        if (req.getOrderType() != null) {
            order.setType(OrderType.valueOf(req.getOrderType()));
        }
        if (req.getPositionAction() != null) {
            order.setPositionAction(PositionAction.valueOf(req.getPositionAction()));
        }
        return order;
    }

    /** 构建快照对象，遍历 MemoryManager 当前全量状态 */
    private ClusterMatchSnapshot buildSnapshot() {
        ClusterMatchSnapshot snapshot = new ClusterMatchSnapshot();
        snapshot.setLogPosition(cluster.logPosition());
        snapshot.setClusterTimestamp(cluster.time());

        // 1. 交易对配置
        snapshot.setSymbols(new HashMap<>(memoryManager.getAllSymbols()));

        // 2. 各订单薄中的活跃订单（按 price-time 顺序遍历）
        Map<String, List<Order>> activeOrders = new HashMap<>();
        Map<String, ClusterMatchSnapshot.OrderBookMeta> metaMap = new HashMap<>();

        memoryManager.getAllOrderBooks().forEach((symbol, book) -> {
            List<Order> orders = new ArrayList<>(book.getOrderMap().values()
                    .stream()
                    .filter(o -> o.getStatus() == OrderStatus.ACTIVE)
                    .toList());
            // 按创建时间排序保证重建时的时间优先顺序
            orders.sort((a, b) -> a.getCreateTime().compareTo(b.getCreateTime()));
            activeOrders.put(symbol, orders);

            ClusterMatchSnapshot.OrderBookMeta meta = new ClusterMatchSnapshot.OrderBookMeta();
            meta.setLastPrice(book.getLastPrice());
            meta.setHighPrice(book.getHighPrice());
            meta.setLowPrice(book.getLowPrice());
            meta.setVolume24h(book.getVolume24h());
            meta.setCreateTime(book.getCreateTime());
            metaMap.put(symbol, meta);
        });
        snapshot.setActiveOrders(activeOrders);
        snapshot.setOrderBookMeta(metaMap);

        // 3. 仓位（合约交易）
        Map<String, Position> positions = new HashMap<>();
        memoryManager.getAllSymbols().keySet().forEach(symbol ->
                memoryManager.getAllPositions(symbol)
                        .forEach((userId, pos) ->
                                positions.put(userId + "_" + symbol, pos)));
        snapshot.setPositions(positions);

        return snapshot;
    }

    /** 将快照数据还原到 MemoryManager */
    private void restoreFromSnapshot(ClusterMatchSnapshot snapshot) {
        memoryManager.clearAll();

        // 1. 恢复交易对
        snapshot.getSymbols().forEach((sym, symbol) -> memoryManager.addSymbol(symbol));

        // 2. 恢复订单薄（按已排序的订单重建价格队列）
        snapshot.getActiveOrders().forEach((symbol, orders) -> {
            OrderBook book = memoryManager.getOrCreateOrderBook(symbol);

            // 恢复元数据
            ClusterMatchSnapshot.OrderBookMeta meta = snapshot.getOrderBookMeta().get(symbol);
            if (meta != null) {
                book.setLastPrice(meta.getLastPrice());
                book.setHighPrice(meta.getHighPrice());
                book.setLowPrice(meta.getLowPrice());
                book.setVolume24h(meta.getVolume24h());
            }
            // 按时间顺序重建（addOrder 维护 price → CopyOnWriteArrayList<Order> 结构）
            orders.forEach(book::addOrder);
        });

        // 3. 恢复仓位
        if (snapshot.getPositions() != null) {
            snapshot.getPositions().forEach((key, position) ->
                    memoryManager.updatePosition(position));
        }
    }

    /**
     * 向 Cluster Session 发送 Egress 消息，格式：[1 byte msgType][JSON body]。
     *
     * <p>有界重试:回包失败只影响单个客户端(其超时后自行重试),状态机已提交,安全。
     * 绝不无限自旋——那会阻塞 Service Thread,让所有撮合停摆。
     */
    private void sendEgress(ClientSession session, byte msgType, String json) {
        byte[] jsonBytes = json.getBytes(StandardCharsets.UTF_8);
        int totalLen = 1 + jsonBytes.length;
        if (totalLen > egressBuffer.capacity()) {
            eventReporter.record(CoreSystemEvent.EGRESS_DROPPED, cluster.time(),
                    () -> "reason=payload_too_large bytes=" + jsonBytes.length);
            log.error("[MatchCluster] Egress payload too large ({} bytes), dropping", jsonBytes.length);
            return;
        }

        egressBuffer.putByte(0, msgType);
        egressBuffer.putBytes(1, jsonBytes);

        long result;
        int  retries = 0;
        while ((result = session.offer(egressBuffer, 0, totalLen)) < 0) {
            if (result == io.aeron.Publication.CLOSED
                    || result == io.aeron.Publication.NOT_CONNECTED
                    || result == io.aeron.Publication.MAX_POSITION_EXCEEDED) {
                final long r = result;
                eventReporter.record(CoreSystemEvent.EGRESS_DROPPED, cluster.time(),
                        () -> "reason=unavailable result=" + r + " sessionId=" + session.id());
                log.warn("[MatchCluster] Egress unavailable (result={}), sessionId={}", result, session.id());
                return;
            }
            if (++retries > EGRESS_OFFER_MAX_RETRIES) {
                eventReporter.record(CoreSystemEvent.EGRESS_DROPPED, cluster.time(),
                        () -> "reason=backpressure_timeout retries=" + EGRESS_OFFER_MAX_RETRIES);
                log.error("[MatchCluster] Egress offer timed out after {} retries, dropping", retries);
                return;
            }
            Thread.yield();
        }
    }
}
