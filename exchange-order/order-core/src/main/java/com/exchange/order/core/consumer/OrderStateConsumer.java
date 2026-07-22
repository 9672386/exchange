package com.exchange.order.core.consumer;

import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * 撮合引擎订单状态变更消费者。
 *
 * <p>订阅 Kafka {@code state-changes} 主题，将撮合引擎产生的订单状态变更（成交、撤单、拒绝等）
 * 持久化至数据库，并更新订单服务内部状态。
 *
 * <h3>消息来源</h3>
 * <p>撮合引擎 {@code BatchKafkaService} 在每批次撮合结束后将 {@code MatchResponse} 列表序列化为 JSON 并推送至此主题。
 *
 * <h3>幂等保证</h3>
 * <p>同一 tradeId / orderId 的消息可能因重试而重复投递，实现时需根据业务唯一键做幂等处理。
 */
@Slf4j
@Component
public class OrderStateConsumer {

    // TODO: 注入 OrderRepository、OrderService，用于更新订单状态

    /**
     * 消费订单状态变更消息。
     *
     * <p>单分区顺序消费（{@code concurrency = "1"}）保证同一订单状态机的顺序性。
     * 高吞吐场景可按 symbol 分区后提高并发度。
     *
     * @param message Kafka 消息体（JSON 格式的 MatchResponse 或 OrderStateEvent）
     */
    @KafkaListener(
            topics = "${exchange.kafka.topics.state-changes:state-changes}",
            groupId = "${spring.kafka.consumer.group-id:order-service}",
            concurrency = "1"
    )
    public void onOrderStateChange(String message) {
        // TODO: 实现
        // 1. 反序列化 message → OrderStateEvent / MatchResponse
        // 2. 根据 orderId 查询现有订单记录
        // 3. 校验状态流转合法性（防止状态回退）
        // 4. 更新 executedQty、avgPrice、status 等字段
        // 5. 持久化（MyBatis-Plus updateById）
        // 6. 如订单全部成交（FILLED），发布领域事件通知下游（行情、资产等）
        log.debug("[OrderStateConsumer] received message: {}", message);
        throw new UnsupportedOperationException("TODO: implement OrderStateConsumer");
    }
}
