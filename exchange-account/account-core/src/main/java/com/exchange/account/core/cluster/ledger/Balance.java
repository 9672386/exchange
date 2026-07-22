package com.exchange.account.core.cluster.ledger;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 单用户单资产余额（内存值对象）。
 *
 * <p>不落库 — 由 {@link BalanceLedger} 持有，状态通过 Aeron Cluster Snapshot 持久化。
 * 真实资金流水由 account-store 的 FundFlowService 异步写入 {@code t_fund_flow}。
 *
 * <p>所有算术操作返回 {@code this}，便于链式验证。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Balance {

    /** 可用余额（可参与交易） */
    private BigDecimal available = BigDecimal.ZERO;

    /** 冻结余额（委托占用，不可用于新委托） */
    private BigDecimal frozen    = BigDecimal.ZERO;

    /** 总余额 = available + frozen */
    public BigDecimal total() {
        return available.add(frozen);
    }

    /**
     * 冻结：available → frozen。
     *
     * @throws IllegalStateException 可用余额不足时
     */
    public void freeze(BigDecimal amount) {
        if (available.compareTo(amount) < 0) {
            throw new IllegalStateException(
                    "Insufficient available balance: available=" + available + ", requested=" + amount);
        }
        available = available.subtract(amount);
        frozen    = frozen.add(amount);
    }

    /**
     * 解冻：frozen → available。
     *
     * @throws IllegalStateException 冻结余额不足时
     */
    public void unfreeze(BigDecimal amount) {
        if (frozen.compareTo(amount) < 0) {
            throw new IllegalStateException(
                    "Insufficient frozen balance: frozen=" + frozen + ", requested=" + amount);
        }
        frozen    = frozen.subtract(amount);
        available = available.add(amount);
    }

    /**
     * 从冻结余额扣减（成交结算 — 扣减方）。
     *
     * @throws IllegalStateException 冻结余额不足时
     */
    public void deductFrozen(BigDecimal amount) {
        if (frozen.compareTo(amount) < 0) {
            throw new IllegalStateException(
                    "Insufficient frozen balance for deduct: frozen=" + frozen + ", deduct=" + amount);
        }
        frozen = frozen.subtract(amount);
    }

    /**
     * 增加可用余额（成交结算 — 入账方 / 充值 / 划入）。
     */
    public void credit(BigDecimal amount) {
        available = available.add(amount);
    }

    /**
     * 直接减少可用余额（提现扣款 / 风控扣罚 / 内部划出）。
     *
     * <p>与 {@link #deductFrozen} 不同：本操作不经过冻结流程，直接扣减 available。
     *
     * @throws IllegalStateException 可用余额不足时
     */
    public void debit(BigDecimal amount) {
        if (available.compareTo(amount) < 0) {
            throw new IllegalStateException(
                    "Insufficient available balance for debit: available=" + available + ", debit=" + amount);
        }
        available = available.subtract(amount);
    }

    /** 深拷贝，用于流水快照 */
    public Balance copy() {
        return new Balance(available, frozen);
    }
}
