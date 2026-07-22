package com.exchange.account.core.cluster.event;

import com.exchange.account.api.dto.AssetStateChangeEvent;

/**
 * 资产事件发布接口（解耦 ClusteredService 与具体 MQ 实现）。
 *
 * <p>生产实现：{@link KafkaAssetEventPublisher}（发布到 Kafka）。
 * 测试/嵌入实现：可替换为内存队列或 No-op。
 *
 * <h3>调用方</h3>
 * <p>仅由 {@link com.exchange.account.core.cluster.AssetClusteredService} 在
 * Cluster Leader 角色下调用（Follower 不发布，避免重复写入）。
 */
public interface AssetEventPublisher {

    /**
     * 发布单条状态变更事件（fire-and-forget，不抛出异常）。
     *
     * @param event 资产状态变更事件
     */
    void publish(AssetStateChangeEvent event);
}
