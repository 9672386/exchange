package com.exchange.account.api.dto;

import com.exchange.account.api.enums.AccountType;
import com.exchange.account.api.enums.FundFlowType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 资产账本状态变更事件（Asset Cluster → Kafka → Persistence Service）。
 *
 * <h3>发布时机</h3>
 * <p>每次 {@link com.exchange.account.core.cluster.AssetClusteredService} 处理完一条 Ingress 消息后，
 * 由 Leader 节点发布到 Kafka {@code asset-state-changes} 主题。Follower 节点不发布（Raft 已保证一致性）。
 *
 * <h3>事件类型</h3>
 * <ul>
 *   <li>{@code FREEZE} — 冻结资产（下单）</li>
 *   <li>{@code UNFREEZE} — 解冻资产（撤单）</li>
 *   <li>{@code SETTLE_BUYER_DEDUCT} — 买方结算扣款（quote 冻结扣减）</li>
 *   <li>{@code SETTLE_BUYER_CREDIT} — 买方结算入账（base 可用增加）</li>
 *   <li>{@code SETTLE_SELLER_DEDUCT} — 卖方结算扣款（base 冻结扣减）</li>
 *   <li>{@code SETTLE_SELLER_CREDIT} — 卖方结算入账（quote 可用增加）</li>
 * </ul>
 *
 * <h3>幂等保障</h3>
 * <p>{@code eventId} = {@code eventType + ":" + bizNo + ":" + userId + ":" + asset}，
 * Persistence Service 以此做去重（DB 唯一索引 on bizNo+flowType）。
 *
 * <h3>余额快照</h3>
 * <p>{@code available} / {@code frozen} 是操作完成后的账本余额快照，
 * 直接 UPSERT 到 {@code t_user_asset}，保证 DB 与内存最终一致。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AssetStateChangeEvent {

    /** 事件 ID（幂等键）= eventType:bizNo:userId:asset */
    private String eventId;

    /** 事件类型（与 FundFlowType 对应，额外区分结算的四个子事件） */
    private String eventType;

    /** 用户 ID */
    private Long userId;

    /** 账户类型（如 SPOT、FUTURES） */
    private AccountType accountType;

    /** 资产代码（如 BTC、USDT） */
    private String asset;

    /** 操作后可用余额（用于 upsert t_user_asset） */
    private BigDecimal available;

    /** 操作后冻结余额（用于 upsert t_user_asset） */
    private BigDecimal frozen;

    /** 变动金额（有符号：正数=入账，负数=出账；用于写 t_fund_flow） */
    private BigDecimal amount;

    /** 资金流水类型 */
    private FundFlowType flowType;

    /** 关联业务单号（orderId / tradeId） */
    private String bizNo;

    /** 备注 */
    private String remark;

    /** Cluster 协调时间戳（来自 onSessionMessage 的 timestamp 参数） */
    private long clusterTimestamp;
}
