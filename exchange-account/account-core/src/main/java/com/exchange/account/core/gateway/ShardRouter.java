package com.exchange.account.core.gateway;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Asset Cluster 分片路由器。
 *
 * <h3>当前：单节点（Shard 0）</h3>
 * <p>所有请求路由到同一个 Cluster 节点（{@code ASSET_CLUSTER_INGRESS_0}）。
 *
 * <h3>扩展为多分片</h3>
 * <ol>
 *   <li>新增环境变量 {@code ASSET_CLUSTER_INGRESS_1}, {@code ASSET_CLUSTER_INGRESS_2}, ...</li>
 *   <li>扩大 {@code totalShards}；{@link #getShardId(Long)} 按 userId hash 取模分片。</li>
 *   <li>{@link AssetGatewayService} 对每个分片维护一个独立的 AeronCluster 客户端。</li>
 *   <li>无需修改 {@link AssetClusteredService} 代码。</li>
 * </ol>
 *
 * <h3>一致性哈希（远期）</h3>
 * <p>如需动态扩缩容不停服，可替换 {@code userId % totalShards} 为一致性哈希环。
 */
@Slf4j
@Component
public class ShardRouter {

    private final List<String> ingressEndpoints;
    private final int totalShards;

    public ShardRouter(
            @Value("#{'${asset.cluster.ingress.endpoints:aeron:udp?endpoint=localhost:20140}'.split(',')}")
            List<String> ingressEndpoints) {
        this.ingressEndpoints = ingressEndpoints;
        this.totalShards = ingressEndpoints.size();
        log.info("[ShardRouter] Initialized — totalShards={} endpoints={}", totalShards, ingressEndpoints);
    }

    /**
     * 根据 userId 计算所属分片 ID（0 ~ totalShards-1）。
     *
     * <p>使用 {@code Math.abs(userId.hashCode()) % totalShards} 避免负数。
     * Long.hashCode() = (int)(value ^ (value >>> 32)) 对大 userId 有良好分散性。
     */
    public int getShardId(Long userId) {
        return (int) (Math.abs(userId) % totalShards);
    }

    /**
     * 获取指定分片的 Aeron Ingress 地址。
     */
    public String getIngress(int shardId) {
        if (shardId < 0 || shardId >= totalShards) {
            throw new IllegalArgumentException("Invalid shardId=" + shardId + " totalShards=" + totalShards);
        }
        return ingressEndpoints.get(shardId);
    }

    public int getTotalShards() {
        return totalShards;
    }
}
