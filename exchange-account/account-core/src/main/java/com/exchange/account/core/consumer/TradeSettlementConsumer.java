//package com.exchange.account.core.consumer;
//
//import lombok.extern.slf4j.Slf4j;
//import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
//import org.springframework.kafka.annotation.KafkaListener;
//import org.springframework.stereotype.Component;
//
///**
// * 撮合成交结算消费者（降级路径，默认禁用）。
// *
// * <h3>架构说明</h3>
// * <p>Cluster 模式（默认）下，成交结算通过以下路径完成：
// * <pre>
// *   撮合引擎 → Kafka(match-results) → TradeSettlementForwarder
// *                                         │
// *                                         ▼
// *                                   Asset Cluster Ingress
// *                                         │
// *                                         ▼
// *                                   AssetClusteredService.onSessionMessage()
// *                                         │
// *                                         ├── BalanceLedger.settleTrade()  [Raft 共识]
// *                                         └── asyncSettleFlows()           [DB 异步写]
// * </pre>
// * <p>本类仅在 {@code asset.cluster.enabled=false} 时激活（单机降级），
// * 避免与 {@link com.exchange.account.core.cluster.client.TradeSettlementForwarder}
// * 使用同一 Kafka group-id 重复消费。
// *
// * <h3>降级模式实现要点</h3>
// * <ul>
// *   <li>反序列化 MatchResponse → 遍历 trades</li>
// *   <li>按 tradeId 幂等检查（{@code t_fund_flow} 唯一索引 on bizNo）</li>
// *   <li>调用 {@code AssetService.settleTrade()} 在事务内完成四步划转</li>
// *   <li>异常投递到 DLQ，不阻塞消费</li>
// * </ul>
// */
//@Slf4j
//@Component
//@ConditionalOnProperty(name = "asset.cluster.enabled", havingValue = "false")
//public class TradeSettlementConsumer {
//
//    /**
//     * 降级模式：直接消费 Kafka match-results 并调用 DB 结算。
//     *
//     * <p>此路径仅在 {@code asset.cluster.enabled=false} 时生效；
//     * 正常 Cluster 模式下此 Bean 不会被注册。
//     */
//    @KafkaListener(
//            topics    = "${exchange.kafka.topics.match-results:match-results}",
//            groupId   = "${spring.kafka.consumer.group-id:account-service}",
//            concurrency = "3"
//    )
//    public void onMatchResult(String message) {
//        // 降级模式占位实现：收到消息仅记日志，不做实际结算
//        // 如需激活降级路径，请注入 AssetService + ObjectMapper 并实现结算逻辑
//        log.warn("[TradeSettlementConsumer] Degraded mode — received message but no settlement impl. " +
//                "Enable asset.cluster.enabled=true to use Cluster path.");
//    }
//}
