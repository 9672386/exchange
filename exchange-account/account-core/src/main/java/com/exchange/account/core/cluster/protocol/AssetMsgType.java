package com.exchange.account.core.cluster.protocol;

/**
 * Asset Cluster 消息类型常量。
 *
 * <p>消息格式：{@code [1 byte msgType][N bytes JSON body]}
 *
 * <h3>Ingress（Order Service / TradeSettlementForwarder → Asset Cluster）</h3>
 * <pre>
 *   0x01  FREEZE          — 冻结资产（下单时，单笔）
 *   0x02  UNFREEZE        — 解冻资产（撤单时，单笔）
 *   0x03  SETTLE_TRADE    — 成交结算（单笔）
 *   0x04  BALANCE_QUERY   — 查询余额
 *   0x05  BATCH_FREEZE    — 批量冻结（单用户，多 orderId，原子：任一失败全部回滚）
 *   0x06  BATCH_SETTLE          — 批量结算（多用户对，每笔独立幂等，局部失败不阻断）
 *   0x07  MATCH_POSITION_QUERY  — 查询 Cluster 已消费的 Match Archive 位点
 *   0x08  CREDIT                — 加钱（充值入账/奖励/补偿，直接增加 available）
 *   0x09  DEBIT                 — 减钱（提现扣款/风控扣罚，直接减少 available）
 *   0x0A  INTERNAL_TRANSFER     — 同用户跨账户类型划转（如 SPOT→FUTURES）
 * </pre>
 *
 * <h3>Egress（Asset Cluster → 调用方）</h3>
 * <pre>
 *   0x10  FREEZE_OK           — 冻结成功
 *   0x11  FREEZE_FAIL         — 冻结失败（余额不足）
 *   0x12  UNFREEZE_OK         — 解冻成功
 *   0x13  SETTLE_OK           — 结算成功
 *   0x14  SETTLE_FAIL         — 结算失败（余额不足）
 *   0x15  BALANCE_RESP        — 余额查询结果
 *   0x18  BATCH_FREEZE_RESP        — 批量冻结响应（OK 或 FAIL + error）
 *   0x19  BATCH_SETTLE_RESP        — 批量结算响应（逐笔状态列表）
 *   0x1A  MATCH_POSITION_RESP      — Match Archive 位点查询响应
 *   0x1B  CREDIT_OK                — 加钱成功
 *   0x1C  CREDIT_FAIL              — 加钱失败
 *   0x1D  DEBIT_OK                 — 减钱成功
 *   0x1E  DEBIT_FAIL               — 减钱失败（余额不足）
 *   0x1F  ERROR                    — 通用错误
 *   0x20  TRANSFER_OK              — 内部划转成功
 *   0x21  TRANSFER_FAIL            — 内部划转失败（余额不足）
 * </pre>
 */
public final class AssetMsgType {

    private AssetMsgType() {}

    // ── Ingress ──────────────────────────────────────────────────────────────
    public static final byte FREEZE        = 0x01;
    public static final byte UNFREEZE      = 0x02;
    public static final byte SETTLE_TRADE  = 0x03;
    public static final byte BALANCE_QUERY = 0x04;
    /** 批量冻结：单用户，多 (orderId, asset, amount)，整体原子。 */
    public static final byte BATCH_FREEZE  = 0x05;
    /** 批量结算：多 trade 对，每笔独立幂等，局部失败不阻断。 */
    public static final byte BATCH_SETTLE  = 0x06;
    /** 查询 Cluster 已消费的 Match Archive byte position（TradeSettlementForwarder 启动时用）。 */
    public static final byte MATCH_POSITION_QUERY = 0x07;
    /** 加钱（充值入账 / 奖励 / 补偿），直接增加 available。bizNo = depositId / rewardId。 */
    public static final byte CREDIT               = 0x08;
    /** 减钱（提现扣款 / 风控扣罚），直接减少 available。bizNo = withdrawId。 */
    public static final byte DEBIT                = 0x09;
    /** 同用户跨账户类型划转（如 SPOT→FUTURES），原子操作。bizNo = transferId。 */
    public static final byte INTERNAL_TRANSFER    = 0x0A;

    // ── Egress ───────────────────────────────────────────────────────────────
    public static final byte FREEZE_OK          = 0x10;
    public static final byte FREEZE_FAIL        = 0x11;
    public static final byte UNFREEZE_OK        = 0x12;
    public static final byte SETTLE_OK          = 0x13;
    public static final byte SETTLE_FAIL        = 0x14;
    public static final byte BALANCE_RESP       = 0x15;
    public static final byte BATCH_FREEZE_RESP      = 0x18;
    public static final byte BATCH_SETTLE_RESP      = 0x19;
    /** Match Archive 位点查询响应：{@code {"correlationId":"...","position":N}} */
    public static final byte MATCH_POSITION_RESP    = 0x1A;
    public static final byte CREDIT_OK              = 0x1B;
    public static final byte CREDIT_FAIL            = 0x1C;
    public static final byte DEBIT_OK               = 0x1D;
    public static final byte DEBIT_FAIL             = 0x1E;
    public static final byte ERROR                  = 0x1F;
    public static final byte TRANSFER_OK            = 0x20;
    public static final byte TRANSFER_FAIL          = 0x21;
}
