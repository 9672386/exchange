package com.exchange.account.api.enums;

/**
 * 资金流水主类型（业务大类）。
 *
 * <p>每笔流水先按主类型归类，再细化到 {@link FundFlowType} 子类型：
 *
 * <pre>
 * ORDER            委托管理
 *   ├─ FREEZE        下单冻结
 *   └─ UNFREEZE      撤单解冻
 *
 * TRADE            成交结算
 *   ├─ TRADE_DEDUCT  成交扣款
 *   └─ TRADE_CREDIT  成交入账
 *
 * DEPOSIT_WITHDRAW  充提
 *   ├─ DEPOSIT       充值
 *   └─ WITHDRAW      提现
 *
 * ADJUSTMENT       人工调账
 *   ├─ CREDIT        人工加钱（奖励/补偿/充值入账）
 *   └─ DEBIT         人工减钱（扣款/罚款/提现出账）
 *
 * TRANSFER         内部划转
 *   ├─ TRANSFER_OUT  划出（出账方）
 *   └─ TRANSFER_IN   划入（入账方）
 *
 * FEE              手续费
 *   ├─ FEE_DEDUCT    手续费扣除
 *   └─ FEE_REBATE    手续费返还
 * </pre>
 *
 * <h3>查询场景</h3>
 * <p>前端可先按主类型筛选（如只看"成交结算"），再按子类型细化，
 * 无需枚举所有子类型组合。
 */
public enum FundFlowBizType {

    /** 委托管理（下单冻结 / 撤单解冻） */
    ORDER("委托"),

    /** 成交结算（买卖双方资产划转） */
    TRADE("成交结算"),

    /** 充提（用户入金 / 出金） */
    DEPOSIT_WITHDRAW("充提"),

    /** 人工调账（系统加钱 / 减钱，用于充值入账、奖励发放、扣款补偿等） */
    ADJUSTMENT("调账"),

    /** 内部划转（同用户跨账户类型，如 SPOT→FUTURES；或用户间平台内转账） */
    TRANSFER("划转"),

    /** 手续费（扣除 / 返还） */
    FEE("手续费");

    /** 中文描述（用于对外展示） */
    private final String desc;

    FundFlowBizType(String desc) {
        this.desc = desc;
    }

    public String getDesc() {
        return desc;
    }
}
