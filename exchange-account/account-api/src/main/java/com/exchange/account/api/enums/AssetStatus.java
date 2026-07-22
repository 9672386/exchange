package com.exchange.account.api.enums;

/** 资产账户状态 */
public enum AssetStatus {

    /** 正常 */
    ACTIVE,

    /** 冻结（风控介入，不允许操作） */
    FROZEN,

    /** 注销 */
    CLOSED;
}
