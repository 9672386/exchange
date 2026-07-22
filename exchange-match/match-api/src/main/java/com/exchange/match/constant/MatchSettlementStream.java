package com.exchange.match.constant;

/**
 * 撮合成交结算流的 Aeron 契约常量（跨服务共享）。
 *
 * <h3>为什么单独一条结算流</h3>
 * <p>撮合成交需要发给两类下游,但可靠性要求截然不同:
 * <ul>
 *   <li><b>结算(资产服务)</b>:一笔都不能丢——丢了就漏结算,买卖双方冻结资金永不释放。</li>
 *   <li><b>实时行情/风控</b>:可以丢,消费方能从其它途径补齐,不该拖累撮合。</li>
 * </ul>
 *
 * <p>若两者共用一条 UDP MDC 流,慢的实时消费者一背压,结算录制会被一起拖到丢弃。
 * 因此结算走<b>专用的进程内 IPC 流</b>({@link #SETTLEMENT_CHANNEL} / {@link #SETTLEMENT_STREAM}),
 * 由 Archive 本地可靠录制,与实时 UDP 广播物理隔离。
 *
 * <p>发布侧:{@code AeronMatchResultPublisher}(match-core)可靠写入此流。
 * 消费侧:{@code TradeSettlementForwarder}(account-core)按此 stream 找 Archive 录制回放。
 * 常量放在 match-api,两侧共享而互不依赖对方的 core。
 */
public final class MatchSettlementStream {

    private MatchSettlementStream() {}

    /** 结算录制专用 IPC 通道（进程内,零拷贝,不受 UDP 订阅方背压影响）。 */
    public static final String SETTLEMENT_CHANNEL = "aeron:ipc";

    /** 结算录制专用 stream ID（与撮合 Cluster 内部 stream、实时 MDC stream 均不冲突）。 */
    public static final int SETTLEMENT_STREAM = 2000;
}
