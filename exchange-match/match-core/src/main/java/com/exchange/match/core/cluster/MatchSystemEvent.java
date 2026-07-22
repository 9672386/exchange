package com.exchange.match.core.cluster;

import com.exchange.common.event.SystemEvent;

/**
 * 撮合引擎专属系统事件。
 *
 * <p>跨模块通用事件（请求超时、快照、角色变更、Egress 丢弃等）复用
 * {@link com.exchange.common.event.CoreSystemEvent}；此处只放撮合独有的。
 *
 * <p>{@link #code()} 加 {@code MATCH_} 前缀，保证与其他模块事件的全局唯一。
 */
public enum MatchSystemEvent implements SystemEvent {

    /**
     * 订单被撮合引擎拒绝（业务原因，非超时）。
     *
     * <p>如 POST_ONLY 会立即成交、FOK 无法全额成交、价格/数量非法、交易对停牌等。
     * 属正常业务结果，<b>非</b>期望恒为 0；突增可能意味着上游参数问题或行情异常。
     */
    MATCH_ORDER_REJECTED(Severity.INFO, "订单被拒绝（业务原因）", false),

    /**
     * 撤单命中不存在或不属于该用户的订单。
     *
     * <p>可能是重复撤单、订单已成交/已撤、或 userId 不匹配。非致命，用于观测撤单质量。
     */
    MATCH_CANCEL_MISS(Severity.INFO, "撤单未命中活跃订单", false),

    /**
     * 撮合产生成交（累计吞吐观测）。
     *
     * <p>非异常事件，仅用于统计撮合活跃度；配置里应设较大采样间隔。
     */
    MATCH_TRADE_PRODUCED(Severity.INFO, "撮合成交产生", false);

    private final Severity severity;
    private final String   description;
    private final boolean  expectZero;

    MatchSystemEvent(Severity severity, String description, boolean expectZero) {
        this.severity    = severity;
        this.description = description;
        this.expectZero  = expectZero;
    }

    @Override public String   code()        { return name(); }
    @Override public Severity severity()    { return severity; }
    @Override public String   description() { return description; }
    @Override public boolean  expectZero()  { return expectZero; }
}
