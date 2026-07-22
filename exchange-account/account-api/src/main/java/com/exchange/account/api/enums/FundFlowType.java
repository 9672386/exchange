package com.exchange.account.api.enums;

/**
 * 资金流水子类型（具体业务操作）。
 *
 * <p>子类型归属于 {@link FundFlowBizType} 主类型，两者共同描述一笔流水：
 * <ul>
 *   <li>主类型用于前端大类筛选、报表分组。</li>
 *   <li>子类型用于精确标识操作语义。</li>
 * </ul>
 *
 * <p>通过 {@link #getBizType()} 直接获取所属主类型，无需额外映射。
 */
public enum FundFlowType {

    // ── 充提 ─────────────────────────────────────────────────────────────────

    /** 充值（外部入金） */
    DEPOSIT(FundFlowBizType.DEPOSIT_WITHDRAW, "充值"),

    /** 提现（外部出金） */
    WITHDRAW(FundFlowBizType.DEPOSIT_WITHDRAW, "提现"),

    // ── 委托管理 ─────────────────────────────────────────────────────────────

    /** 下单冻结（委托资金预扣） */
    FREEZE(FundFlowBizType.ORDER, "下单冻结"),

    /** 撤单解冻（委托资金退还） */
    UNFREEZE(FundFlowBizType.ORDER, "撤单解冻"),

    // ── 成交结算 ─────────────────────────────────────────────────────────────

    /** 成交扣款（从冻结余额扣除） */
    TRADE_DEDUCT(FundFlowBizType.TRADE, "成交扣款"),

    /** 成交入账（可用余额增加） */
    TRADE_CREDIT(FundFlowBizType.TRADE, "成交入账"),

    // ── 人工调账 ─────────────────────────────────────────────────────────────

    /**
     * 人工加钱（系统直接增加可用余额）。
     *
     * <p>适用场景：充值到账（充值平台回调后写入账本）、奖励发放、活动补偿等。
     * bizNo 填充外部单号（如 depositId / rewardId），确保幂等。
     */
    CREDIT(FundFlowBizType.ADJUSTMENT, "加钱"),

    /**
     * 人工减钱（系统直接减少可用余额）。
     *
     * <p>适用场景：提现扣款（提现申请审核通过后）、风控扣罚等。
     * bizNo 填充外部单号（如 withdrawId），确保幂等。
     */
    DEBIT(FundFlowBizType.ADJUSTMENT, "减钱"),

    // ── 内部划转 ─────────────────────────────────────────────────────────────

    /**
     * 内部划出（出账方流水）。
     *
     * <p>同用户跨账户类型（如 SPOT→FUTURES）：出账方 accountType = SPOT。
     * 用户间内部转账（A→B）：出账方 userId = A。
     * bizNo 填充划转单号（transferId）。
     */
    TRANSFER_OUT(FundFlowBizType.TRANSFER, "划出"),

    /**
     * 内部划入（入账方流水）。
     *
     * <p>与 {@link #TRANSFER_OUT} 配对，必须同时写入，共用同一 bizNo。
     */
    TRANSFER_IN(FundFlowBizType.TRANSFER, "划入"),

    // ── 手续费 ───────────────────────────────────────────────────────────────

    /** 手续费扣除 */
    FEE_DEDUCT(FundFlowBizType.FEE, "手续费扣除"),

    /** 手续费返还（返佣等） */
    FEE_REBATE(FundFlowBizType.FEE, "手续费返还");

    // ── 字段 ─────────────────────────────────────────────────────────────────

    /** 所属主类型 */
    private final FundFlowBizType bizType;

    /** 中文描述（用于对外展示） */
    private final String desc;

    FundFlowType(FundFlowBizType bizType, String desc) {
        this.bizType = bizType;
        this.desc    = desc;
    }

    /** 获取所属主类型（无需额外映射）。 */
    public FundFlowBizType getBizType() {
        return bizType;
    }

    /** 获取子类型中文描述。 */
    public String getDesc() {
        return desc;
    }
}
