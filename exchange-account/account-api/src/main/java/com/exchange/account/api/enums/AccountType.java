package com.exchange.account.api.enums;

/**
 * 账户类型枚举。
 *
 * <p>每个用户下的资产按账户类型完全隔离：
 * 同一用户的 SPOT-USDT 与 FUTURES-USDT 是两个独立的余额，
 * 互不影响，内部划转需通过专用接口（Transfer）完成。
 *
 * <h3>账户类型说明</h3>
 * <ul>
 *   <li>{@link #SPOT}    — 现货账户，用于现货交易下单/结算</li>
 *   <li>{@link #FUTURES} — 合约账户（U本位），用于永续/交割合约</li>
 *   <li>{@link #FUNDING} — 资金账户（主钱包），充值/提现的入口，划转至其他账户</li>
 *   <li>{@link #WEALTH}  — 理财账户，存放申购理财产品的资产</li>
 *   <li>{@link #OPTIONS} — 期权账户，用于期权合约交易</li>
 * </ul>
 */
public enum AccountType {

    /** 现货账户 */
    SPOT("现货账户"),

    /** 合约账户（U本位） */
    FUTURES("合约账户"),

    /** 资金账户（主钱包） */
    FUNDING("资金账户"),

    /** 理财账户 */
    WEALTH("理财账户"),

    /** 期权账户 */
    OPTIONS("期权账户");

    private final String description;

    AccountType(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
