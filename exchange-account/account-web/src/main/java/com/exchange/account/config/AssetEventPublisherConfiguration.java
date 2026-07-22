package com.exchange.account.config;

import com.exchange.account.core.cluster.event.AeronArchiveEventPublisher;
import com.exchange.account.core.cluster.event.AssetEventPublisher;
import com.exchange.common.event.SystemEventReporter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 资产状态变更事件发布器装配。
 *
 * <h3>为什么需要这个类</h3>
 * <p>{@link AeronArchiveEventPublisher} 此前既没有 {@code @Component} 也没有任何
 * {@code @Bean} 定义，从未被实例化——意味着 Archive 事件发布链路实际是断开的：
 * 账本变更不会产生 {@code AssetStateChangeEvent}，{@code account-persist} 收不到任何数据，
 * {@code t_fund_flow} / {@code t_user_asset} 不会被写入。
 *
 * <p>而 {@code KafkaAssetEventPublisher} 带无条件 {@code @Component}，
 * 在"去 Kafka"架构下反而成了唯一候选，与设计意图相反。
 *
 * <h3>装配规则</h3>
 * <ul>
 *   <li>默认启用 Archive 发布器（{@code asset.event.publisher=archive}）</li>
 *   <li>{@code connect()} 由 {@code AssetClusterLifecycle} 在 Cluster 启动后调用，
 *       此处只负责创建实例</li>
 *   <li>Kafka 发布器需显式开启 {@code asset.kafka.publisher.enabled=true}（降级用）</li>
 * </ul>
 */
@Slf4j
@Configuration
public class AssetEventPublisherConfiguration {

    /**
     * Aeron Archive 事件发布器（默认实现）。
     *
     * <p>注入 {@link SystemEventReporter}，使事件丢弃能够被计数与告警
     * （{@code EVENT_PUBLISH_DROPPED}，期望恒为 0）。
     */
    @Bean
    @ConditionalOnMissingBean(AssetEventPublisher.class)
    @ConditionalOnProperty(name = "asset.event.publisher", havingValue = "archive", matchIfMissing = true)
    public AssetEventPublisher aeronArchiveEventPublisher(SystemEventReporter eventReporter) {
        log.info("[AssetEventPublisherConfig] Using AeronArchiveEventPublisher");
        return new AeronArchiveEventPublisher(eventReporter);
    }
}
