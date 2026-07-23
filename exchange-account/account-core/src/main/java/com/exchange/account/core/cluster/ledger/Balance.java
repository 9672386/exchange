package com.exchange.account.core.cluster.ledger;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 单用户单资产余额（内存值对象，定点 long）。
 *
 * <p><b>定点表示</b>:{@code available}/{@code frozen} 是该资产 scale 下的原始整数
 * ({@code raw = round(realValue × 10^scale)})。Balance 本身不持有 scale——
 * 同一资产的两个 raw 必定同 scale,故加减比较是纯 long 运算;scale 由
 * {@link BalanceLedger} 在边界处(BigDecimal↔long)应用,见
 * {@code com.exchange.common.math.AssetScaleRegistry}。
 *
 * <p>不落库 — 由 {@link BalanceLedger} 持有,状态通过 Aeron Cluster Snapshot 持久化。
 * 真实资金流水由 account-store 的 FundFlowService 异步写入 {@code t_fund_flow}。
 *
 * <p><b>溢出策略 fail-closed</b>:所有加减用 {@link Math#addExact}/{@link Math#subtractExact},
 * 溢出抛异常而非静默回绕——资金宁可拒绝也不可错账。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Balance {

    /** 可用余额(可参与交易),该资产 scale 下的 raw。 */
    private long available = 0L;

    /** 冻结余额(委托占用,不可用于新委托),该资产 scale 下的 raw。 */
    private long frozen = 0L;

    /** 总余额 = available + frozen。 */
    public long total() {
        return Math.addExact(available, frozen);
    }

    /**
     * 冻结:available → frozen。
     *
     * @throws IllegalStateException 可用余额不足时
     */
    public void freeze(long amount) {
        if (available < amount) {
            throw new IllegalStateException(
                    "Insufficient available balance: available=" + available + ", requested=" + amount);
        }
        available = Math.subtractExact(available, amount);
        frozen    = Math.addExact(frozen, amount);
    }

    /**
     * 解冻:frozen → available。
     *
     * @throws IllegalStateException 冻结余额不足时
     */
    public void unfreeze(long amount) {
        if (frozen < amount) {
            throw new IllegalStateException(
                    "Insufficient frozen balance: frozen=" + frozen + ", requested=" + amount);
        }
        frozen    = Math.subtractExact(frozen, amount);
        available = Math.addExact(available, amount);
    }

    /**
     * 从冻结余额扣减(成交结算 — 扣减方)。
     *
     * @throws IllegalStateException 冻结余额不足时
     */
    public void deductFrozen(long amount) {
        if (frozen < amount) {
            throw new IllegalStateException(
                    "Insufficient frozen balance for deduct: frozen=" + frozen + ", deduct=" + amount);
        }
        frozen = Math.subtractExact(frozen, amount);
    }

    /**
     * 增加可用余额(成交结算 — 入账方 / 充值 / 划入)。
     */
    public void credit(long amount) {
        available = Math.addExact(available, amount);
    }

    /**
     * 直接减少可用余额(提现扣款 / 风控扣罚 / 内部划出)。
     *
     * <p>与 {@link #deductFrozen} 不同:本操作不经过冻结流程,直接扣减 available。
     *
     * @throws IllegalStateException 可用余额不足时
     */
    public void debit(long amount) {
        if (available < amount) {
            throw new IllegalStateException(
                    "Insufficient available balance for debit: available=" + available + ", debit=" + amount);
        }
        available = Math.subtractExact(available, amount);
    }

    /** 深拷贝,用于流水快照。 */
    public Balance copy() {
        return new Balance(available, frozen);
    }
}
