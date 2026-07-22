package com.exchange.match.core.transport;

import com.exchange.match.core.event.service.EventPublishService;
import com.exchange.match.request.EventCanalReq;
import com.exchange.match.request.EventNewOrderReq;
import com.exchange.transport.aeron.config.AeronConfigFactory;
import com.exchange.transport.aeron.config.AeronConfigFactory.SubscriberChannelConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.aeron.Aeron;
import io.aeron.Subscription;
import io.aeron.logbuffer.FragmentHandler;
import io.aeron.logbuffer.Header;
import lombok.extern.slf4j.Slf4j;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.BusySpinIdleStrategy;
import org.agrona.concurrent.IdleStrategy;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/**
 * Aeron MDC 入站订阅者 —— 撮合引擎的订单接收入口。
 *
 * <h3>职责</h3>
 * <p>替代原 {@code MatchEventController} 中 HTTP 下单路径（{@code /api/match/event/new-order}
 * 和 {@code /api/match/event/cancel-order}），通过 Aeron UDP 订阅接收 Gateway 下发的订单事件，
 * 并将其直接喂入现有 <b>Disruptor RingBuffer</b>，后续撮合链路保持不变。
 *
 * <h3>消息协议（MVP：JSON over Aeron）</h3>
 * <pre>
 *   [1 byte type] [N bytes JSON body]
 *
 *   type = 0x01  → EventNewOrderReq（新订单）
 *   type = 0x02  → EventCanalReq   （撤单）
 * </pre>
 * <p>后续可替换为 SBE（Simple Binary Encoding）实现零拷贝，改动范围仅在此类。
 *
 * <h3>激活条件</h3>
 * <pre>
 *   aeron.enabled=true          # 默认 true
 *   aeron.order-receiver=true   # 默认 true，false 时保留纯 HTTP 入口
 * </pre>
 *
 * <h3>环境变量</h3>
 * <pre>
 *   MATCH_CONTROL_ADDR   MDC 控制地址，格式 ip:port（由 Gateway 侧 Publisher 配置相同值）
 *   STREAM_ID            Aeron stream ID，默认 1001
 *   AERON_INTERFACE      本机 ENI IP（AWS 环境必填）
 * </pre>
 */
@Slf4j
@Component
@ConditionalOnBean(Aeron.class)
@ConditionalOnProperty(name = "aeron.order-receiver", havingValue = "true", matchIfMissing = true)
public class AeronOrderReceiver implements InitializingBean, DisposableBean {

    /* ── 消息类型标志字节 ─────────────────────────────────────────── */
    public static final byte MSG_NEW_ORDER = 0x01;
    public static final byte MSG_CANCEL    = 0x02;

    /* ── 轮询批次大小：每次 poll 最多消费的 fragment 数 ─────────────── */
    private static final int FRAGMENT_LIMIT = 10;

    @Autowired
    private Aeron aeron;

    @Autowired
    private EventPublishService eventPublishService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private Subscription subscription;
    private Thread pollThread;
    private volatile boolean running;

    /* ══════════════════════════════════════════════════════════════
     *  Spring 生命周期
     * ══════════════════════════════════════════════════════════════ */

    @Override
    public void afterPropertiesSet() {
        SubscriberChannelConfig cfg = AeronConfigFactory.buildSubscriberFromEnv(null);

        subscription = aeron.addSubscription(cfg.getChannel(), cfg.getStreamId());
        log.info("[AeronOrderReceiver] Subscribed — channel={}, streamId={}",
                cfg.getChannel(), cfg.getStreamId());

        running = true;
        pollThread = new Thread(this::pollLoop, "aeron-order-receiver");
        pollThread.setDaemon(true);
        pollThread.start();
    }

    @Override
    public void destroy() {
        running = false;
        if (pollThread != null) {
            pollThread.interrupt();
            try {
                pollThread.join(3_000);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
        }
        if (subscription != null) {
            subscription.close();
        }
        log.info("[AeronOrderReceiver] Shutdown complete");
    }

    /* ══════════════════════════════════════════════════════════════
     *  轮询循环
     * ══════════════════════════════════════════════════════════════ */

    private void pollLoop() {
        // BusySpin：延迟最低，撮合引擎对 CPU 成本敏感度低于延迟。
        // 资源受限时可换 SleepingIdleStrategy / BackoffIdleStrategy。
        IdleStrategy idle = new BusySpinIdleStrategy();

        while (running && !Thread.currentThread().isInterrupted()) {
            try {
                int fragments = subscription.poll(fragmentHandler, FRAGMENT_LIMIT);
                idle.idle(fragments);
            } catch (Exception e) {
                log.error("[AeronOrderReceiver] Poll error", e);
                // 不退出循环，避免单次异常导致整个接收通道停止
            }
        }
        log.info("[AeronOrderReceiver] Poll loop exited");
    }

    /* ══════════════════════════════════════════════════════════════
     *  消息解码与分发
     * ══════════════════════════════════════════════════════════════ */

    private final FragmentHandler fragmentHandler = (DirectBuffer buffer, int offset, int length, Header header) -> {
        try {
            if (length < 1) {
                log.warn("[AeronOrderReceiver] Received empty fragment, skipped");
                return;
            }

            // 第一个字节为消息类型
            byte msgType = buffer.getByte(offset);
            int bodyOffset = offset + 1;
            int bodyLength = length - 1;

            byte[] bodyBytes = new byte[bodyLength];
            buffer.getBytes(bodyOffset, bodyBytes);

            dispatch(msgType, bodyBytes);

        } catch (Exception e) {
            log.error("[AeronOrderReceiver] Failed to decode fragment at offset={}, length={}", offset, length, e);
        }
    };

    private void dispatch(byte msgType, byte[] bodyBytes) throws Exception {
        switch (msgType) {
            case MSG_NEW_ORDER -> {
                EventNewOrderReq req = objectMapper.readValue(bodyBytes, EventNewOrderReq.class);
                log.debug("[AeronOrderReceiver] New order received: orderId={}, userId={}",
                        req.getOrderId(), req.getUserId());
                // 直接喂入 Disruptor RingBuffer，后续链路完全不变
                eventPublishService.publishNewOrderEvent(req);
            }
            case MSG_CANCEL -> {
                EventCanalReq req = objectMapper.readValue(bodyBytes, EventCanalReq.class);
                log.debug("[AeronOrderReceiver] Cancel received: orderId={}, userId={}",
                        req.getOrderId(), req.getUserId());
                eventPublishService.publishCanalEvent(req);
            }
            default ->
                log.warn("[AeronOrderReceiver] Unknown message type: 0x{}", String.format("%02X", msgType));
        }
    }

    /* ══════════════════════════════════════════════════════════════
     *  工具方法（供 Gateway 侧 Publisher 编码使用，保持协议一致）
     * ══════════════════════════════════════════════════════════════ */

    /**
     * 将消息类型和 JSON body 编码为 Aeron fragment 字节数组。
     * Gateway 侧 Publisher 调用同名工具方法，保证协议一致。
     */
    public static byte[] encode(byte msgType, String jsonBody) {
        byte[] bodyBytes = jsonBody.getBytes(StandardCharsets.UTF_8);
        byte[] frame = new byte[1 + bodyBytes.length];
        frame[0] = msgType;
        System.arraycopy(bodyBytes, 0, frame, 1, bodyBytes.length);
        return frame;
    }
}
