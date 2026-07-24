package com.exchange.match.request;

import lombok.Data;

import java.io.Serializable;

/**
 * 标的下架请求(进 Raft 日志的状态机写操作)。
 *
 * <p>安全序列:SUSPENDED(停新单)→ 该标的挂单全撤 + 解冻 →(合约)持仓处理 → DELISTED。
 * {@code delistId} 作幂等键。
 */
@Data
public class EventDelistSymbolReq implements Serializable {

    /** 幂等键(下架单号)。 */
    private String delistId;

    /** 交易对。 */
    private String symbol;

    /**
     * 是否强制下架:
     * <ul>
     *   <li>{@code true} —— 自动撤掉该标的所有挂单(+解冻)后置 DELISTED(依赖批量撤单能力)。</li>
     *   <li>{@code false} —— 仅置 SUSPENDED;若仍有挂单/持仓则拒绝置 DELISTED,需先清空。</li>
     * </ul>
     */
    private boolean force;
}
