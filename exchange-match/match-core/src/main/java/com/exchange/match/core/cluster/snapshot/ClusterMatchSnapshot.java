package com.exchange.match.core.cluster.snapshot;

import com.exchange.match.core.model.Order;
import com.exchange.match.core.model.Position;
import com.exchange.match.core.model.Symbol;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * Aeron Cluster 快照数据模型。
 *
 * <p>在 {@code ClusteredService.onTakeSnapshot()} 中序列化为 JSON 写入 Archive；
 * 在 {@code onStart(cluster, snapshotImage)} 中反序列化恢复引擎内存状态。
 *
 * <h3>设计原则</h3>
 * <ul>
 *   <li>只包含恢复订单薄所需的最小数据集，不包含 Kafka Offset 等运行时元数据。</li>
 *   <li>使用 {@link Order} 对象列表（而非价格深度）保证个单可被精确重建与撤销。</li>
 *   <li>Jackson 默认序列化 — 所有字段均为 public 可见（通过 Lombok @Data getter）。</li>
 * </ul>
 */
@Data
public class ClusterMatchSnapshot {

    /** 快照对应的 Raft Log Position（用于调试与对账） */
    private long logPosition;

    /** 快照生成时间戳（集群协调时间，ms） */
    private long clusterTimestamp;

    /** 已注册的所有交易对配置 */
    private Map<String, Symbol> symbols;

    /**
     * 各交易对的活跃挂单列表（仅 ACTIVE 状态）。
     * Key = symbol，Value = 按 price-time 优先顺序排列的订单列表。
     * 恢复时依次调用 {@code OrderBook.addOrder()} 重建价格队列。
     */
    private Map<String, List<Order>> activeOrders;

    /**
     * 所有用户仓位快照（合约交易专用）。
     * Key = "userId_symbol"（与 MemoryManager 内部键一致）。
     */
    private Map<String, Position> positions;

    /** 各交易对订单薄统计元数据（最新成交价、24h 高低价、成交量） */
    private Map<String, OrderBookMeta> orderBookMeta;

    /** 订单薄非功能性统计字段（不影响撮合结果，仅用于行情展示） */
    @Data
    public static class OrderBookMeta {
        private BigDecimal lastPrice;
        private BigDecimal highPrice;
        private BigDecimal lowPrice;
        private BigDecimal volume24h;
        private long createTime;
    }
}
