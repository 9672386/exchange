//package com.exchange.account.persist.consumer;
//
//import com.exchange.account.api.dto.AssetStateChangeEvent;
//import com.exchange.account.core.service.AssetService;
//import com.exchange.account.core.service.FundFlowService;
//import com.fasterxml.jackson.databind.ObjectMapper;
//import com.fasterxml.jackson.databind.SerializationFeature;
//import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
//import lombok.RequiredArgsConstructor;
//import lombok.extern.slf4j.Slf4j;
//import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
//import org.springframework.kafka.annotation.KafkaListener;
//import org.springframework.kafka.support.Acknowledgment;
//import org.springframework.kafka.support.KafkaHeaders;
//import org.springframework.messaging.handler.annotation.Header;
//import org.springframework.messaging.handler.annotation.Payload;
//import org.springframework.stereotype.Component;
//
//import java.util.Set;
//import java.util.concurrent.ConcurrentHashMap;
//
///**
// * 资产状态变更持久化消费者。
// *
// * <p>订阅 Kafka {@code asset-state-changes} 主题，将每条事件写入 DB：
// * <ol>
// *   <li>插入 {@code t_fund_flow}（资金流水，append-only）。</li>
// *   <li>UPSERT {@code t_user_asset}（余额快照，最终一致）。</li>
// * </ol>
// *
// * <h3>幂等保证</h3>
// * <p>以 {@link AssetStateChangeEvent#getEventId()} 为幂等键：
// * <ul>
// *   <li>内存缓存（本地 ConcurrentHashSet）：防止同一 JVM 实例内的重复处理。</li>
// *   <li>DB 唯一索引（{@code t_fund_flow.bizNo + flowType}）：跨重启的持久化幂等。</li>
// * </ul>
// *
// * <h3>顺序消费</h3>
// * <p>Kafka Key = {@code userId:asset}，同一用户同一资产的事件在同一分区内有序，
// * 保证 UPSERT 余额时不出现旧值覆盖新值的情况。
// *
// * <h3>失败处理</h3>
// * <p>异常时不提交 offset（手动 ACK 模式），由 Kafka 自动重试；
// * 超过重试次数后投递到 DLT（Dead Letter Topic）{@code asset-state-changes.DLT}。
// */
///**
// * @deprecated 已被 {@link com.exchange.account.persist.subscriber.AssetArchiveSubscriber} 替代。
// *             保留此类仅供回退；正常情况下通过 {@code asset.kafka.consumer.enabled=false}（默认）禁用。
// */
//@Deprecated
//@Slf4j
//@Component
//@RequiredArgsConstructor
//@ConditionalOnProperty(name = "asset.kafka.consumer.enabled", havingValue = "true", matchIfMissing = false)
//public class AssetPersistenceConsumer {
//
//    private final FundFlowService fundFlowService;
//    private final AssetService    assetService;
//
//    private final ObjectMapper objectMapper = new ObjectMapper()
//            .registerModule(new JavaTimeModule())
//            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
//
//    /** 内存幂等缓存（重启后清空，依赖 DB 唯一索引兜底） */
//    private final Set<String> processedEventIds = ConcurrentHashMap.newKeySet();
//
//    @KafkaListener(
//            topics   = "${exchange.kafka.topics.asset-state-changes:asset-state-changes}",
//            groupId  = "${spring.kafka.consumer.group-id:asset-persist}",
//            concurrency = "3"
//    )
//    public void onAssetStateChange(
//            @Payload String message,
//            @Header(KafkaHeaders.RECEIVED_KEY) String key,
//            @Header(KafkaHeaders.OFFSET) long offset,
//            Acknowledgment ack) {
//
//        AssetStateChangeEvent event = null;
//        try {
//            event = objectMapper.readValue(message, AssetStateChangeEvent.class);
//
//            // 1. 内存幂等检查
//            if (!processedEventIds.add(event.getEventId())) {
//                log.debug("[AssetPersist] Duplicate event skipped — eventId={}", event.getEventId());
//                ack.acknowledge();
//                return;
//            }
//
//            log.debug("[AssetPersist] Processing eventId={} type={} userId={} asset={} amount={}",
//                    event.getEventId(), event.getEventType(),
//                    event.getUserId(), event.getAsset(), event.getAmount());
//
//            // 2. 写资金流水（t_fund_flow，eventId 幂等）
//            fundFlowService.record(
//                    event.getUserId(),
//                    event.getAccountType(),
//                    event.getAsset(),
//                    event.getFlowType(),
//                    event.getAmount(),
//                    null,              // assetSnapshot = null（余额已在 available/frozen 字段中）
//                    event.getBizNo(),
//                    event.getEventId(),
//                    event.getRemark());
//
//            // 3. UPSERT 余额快照（t_user_asset，按 userId+accountType+asset 定位）
//            assetService.upsertBalance(
//                    event.getUserId(),
//                    event.getAccountType(),
//                    event.getAsset(),
//                    event.getAvailable(),
//                    event.getFrozen());
//
//            ack.acknowledge();
//            log.debug("[AssetPersist] Persisted eventId={} offset={}", event.getEventId(), offset);
//
//        } catch (Exception e) {
//            String eventId = event != null ? event.getEventId() : "unknown";
//            log.error("[AssetPersist] Failed to persist eventId={} key={} offset={} — will retry",
//                    eventId, key, offset, e);
//            // 不调用 ack.acknowledge()，让 Kafka 重试
//            // 超过 retry 次数后由 DefaultErrorHandler 投递到 DLT
//            throw new RuntimeException("Asset persistence failed", e);
//        }
//    }
//}
