package com.exchange.account.core.service;

import com.exchange.account.api.dto.AssetDTO;
import com.exchange.account.api.enums.AccountType;

import java.math.BigDecimal;
import java.util.List;

/**
 * 资产账户投影服务接口（CQRS 读侧 + 事件落库）。
 *
 * <p>权威余额状态在 Cluster 内存账本（account-core），本接口只负责
 * {@code t_user_asset} 投影的<b>查询</b>与 {@link #upsertBalance 事件落库}。
 * 资金变动（冻结/结算/加减/划转）全部走内存状态机,本接口不做任何账本 mutation。
 */
public interface AssetService {

    /**
     * 查询用户单个资产余额（SPOT，兼容旧签名）。
     */
    AssetDTO getBalance(Long userId, String asset);

    /**
     * 查询用户所有资产余额列表（所有账户类型）。
     */
    List<AssetDTO> getAllBalances(Long userId);

    // =========================================================================
    // 账户类型维度查询（读 t_user_asset 投影，不经过 Raft 日志）
    // =========================================================================

    /**
     * 查询指定账户类型下单个资产余额。
     *
     * <p><b>数据来源：{@code t_user_asset} 投影</b>，由 account-persist 消费事件异步写入。
     * 相对 Cluster 内存账本有持久化延迟（毫秒级），是 CQRS 读侧的最终一致视图。
     * 需要强一致的场景（如刚下单后确认冻结）应以 mutation 的同步响应为准。
     */
    AssetDTO getBalance(Long userId, AccountType accountType, String asset);

    /**
     * 查询指定账户类型下所有资产余额。
     */
    List<AssetDTO> getAllBalances(Long userId, AccountType accountType);

    /**
     * 查询用户所有账户类型下所有资产余额（汇总视图）。
     */
    List<AssetDTO> getAllBalancesByType(Long userId);

    /**
     * 将内存账本余额同步到数据库（UPSERT 语义）。
     *
     * <p>由 account-persist 消费 {@code AssetStateChangeEvent} 后调用，
     * 确保 {@code t_user_asset} 与 Cluster 内存账本保持最终一致，让 REST 查询能返回实时余额。
     *
     * <p>定位行的键为 {@code (userId, accountType, asset)}——同一用户的
     * SPOT-USDT 与 FUTURES-USDT 是独立的两行，绝不能互相覆盖。
     *
     * @param userId      用户 ID
     * @param accountType 账户类型
     * @param asset       资产代码
     * @param available   可用余额（来自 BalanceLedger）
     * @param frozen      冻结余额（来自 BalanceLedger）
     */
    void upsertBalance(Long userId, com.exchange.account.api.enums.AccountType accountType,
                       String asset, BigDecimal available, BigDecimal frozen);
}
