package com.exchange.account.core.service;

import com.exchange.account.api.dto.AssetDTO;
import com.exchange.account.api.enums.AccountType;

import java.math.BigDecimal;
import java.util.List;

/**
 * 资产账户服务接口。
 *
 * <p>管理用户可用余额与冻结余额，所有操作需保证原子性并写入对应资金流水。
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
     * 冻结资产（下单时调用）。
     *
     * <p>将 {@code amount} 从可用余额转移到冻结余额，同时写 {@link com.exchange.account.api.enums.FundFlowType#FREEZE} 流水。
     *
     * @param userId  用户 ID
     * @param asset   资产代码
     * @param amount  冻结金额
     * @param orderId 关联订单 ID（幂等键）
     * @throws com.exchange.common.exception.BusinessException 余额不足时抛出
     */
    void freezeAsset(Long userId, String asset, BigDecimal amount, String orderId);

    /**
     * 解冻资产（撤单时调用）。
     *
     * <p>将 {@code amount} 从冻结余额归还到可用余额，同时写 {@link com.exchange.account.api.enums.FundFlowType#UNFREEZE} 流水。
     */
    void unfreezeAsset(Long userId, String asset, BigDecimal amount, String orderId);

    /**
     * 成交结算：扣减冻结余额（买方或卖方），同时入账对手方资产。
     *
     * <p>由 {@link com.exchange.account.core.consumer.TradeSettlementConsumer} 在消费
     * {@code match-results} Kafka 主题后调用；整个结算操作在同一数据库事务内完成。
     *
     * @param buyerId   买方用户 ID
     * @param sellerId  卖方用户 ID
     * @param baseAsset 基础资产（如 BTC）
     * @param quoteAsset 计价资产（如 USDT）
     * @param qty       成交数量（基础资产）
     * @param quoteAmt  成交金额（计价资产）
     * @param buyFee    买方手续费（计价资产计收）
     * @param sellFee   卖方手续费（基础资产计收）
     * @param tradeId   成交 ID（幂等键）
     */
    void settleTrade(Long buyerId, Long sellerId,
                     String baseAsset, String quoteAsset,
                     BigDecimal qty, BigDecimal quoteAmt,
                     BigDecimal buyFee, BigDecimal sellFee,
                     String tradeId);

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
