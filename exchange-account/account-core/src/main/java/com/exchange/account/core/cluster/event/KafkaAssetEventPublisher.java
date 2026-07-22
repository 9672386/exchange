//package com.exchange.account.core.cluster.event;
//
//import com.exchange.account.api.dto.AssetStateChangeEvent;
//import com.fasterxml.jackson.core.JsonProcessingException;
//import com.fasterxml.jackson.databind.ObjectMapper;
//import com.fasterxml.jackson.databind.SerializationFeature;
//import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
//import lombok.extern.slf4j.Slf4j;
//import org.springframework.beans.factory.annotation.Value;
//import org.springframework.kafka.core.KafkaTemplate;
//import org.springframework.stereotype.Component;
//
///**
// * Kafka 实现的资产事件发布器。
// *
// * <p>将 {@link AssetStateChangeEvent} 序列化为 JSON，发布到 Kafka {@code asset-state-changes} 主题。
// *
// * <h3>Kafka Key</h3>
// * <p>使用 {@code userId + ":" + asset} 作为消息 Key，保证同一用户同一资产的事件有序（分区内顺序）。
// * Persistence Service 按序消费，避免并发 UPSERT 冲突。
// *
// * <h3>发送失败处理</h3>
// * <p>发送失败仅记录 ERROR 日志，不抛出异常（不阻塞 Cluster Service Thread）。
// * BalanceLedger 已通过 Raft 持久化，Snapshot 重启后账本可恢复；DB 端允许短暂不一致。
// * 生产场景可补充 DLQ（Dead Letter Queue）或重试队列。
// */
//@Slf4j
//@Component
//@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(
//        name = "asset.kafka.publisher.enabled", havingValue = "true")
//public class KafkaAssetEventPublisher implements AssetEventPublisher {
//
//    private static final String DEFAULT_TOPIC = "asset-state-changes";
//
//    private final KafkaTemplate<String, String> kafkaTemplate;
//    private final ObjectMapper objectMapper;
//    private final String topic;
//
//    public KafkaAssetEventPublisher(
//            KafkaTemplate<String, String> kafkaTemplate,
//            @Value("${exchange.kafka.topics.asset-state-changes:" + DEFAULT_TOPIC + "}") String topic) {
//        this.kafkaTemplate = kafkaTemplate;
//        this.topic = topic;
//        this.objectMapper = new ObjectMapper()
//                .registerModule(new JavaTimeModule())
//                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
//    }
//
//    @Override
//    public void publish(AssetStateChangeEvent event) {
//        try {
//            String payload = objectMapper.writeValueAsString(event);
//            String key = event.getUserId() + ":" + event.getAsset();
//            kafkaTemplate.send(topic, key, payload)
//                    .whenComplete((result, ex) -> {
//                        if (ex != null) {
//                            log.error("[AssetEventPublisher] Failed to publish event eventId={} type={}",
//                                    event.getEventId(), event.getEventType(), ex);
//                        } else {
//                            log.debug("[AssetEventPublisher] Published eventId={} type={} to partition={}",
//                                    event.getEventId(), event.getEventType(),
//                                    result.getRecordMetadata().partition());
//                        }
//                    });
//        } catch (JsonProcessingException e) {
//            log.error("[AssetEventPublisher] Serialization failed for event eventId={}", event.getEventId(), e);
//        }
//    }
//}
