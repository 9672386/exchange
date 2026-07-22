package com.exchange.account.core.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.exchange.account.api.dto.AssetDTO;
import com.exchange.account.api.enums.AccountType;
import com.exchange.account.api.enums.AssetStatus;
import com.exchange.account.api.enums.FundFlowType;
import com.exchange.account.core.entity.UserAsset;
import com.exchange.account.core.repository.UserAssetRepository;
import com.exchange.account.core.service.AssetService;
import com.exchange.account.core.service.FundFlowService;
import com.exchange.common.error.CommonErrorCode;
import com.exchange.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 资产账户服务实现（数据库路径）。
 *
 * <h3>双路径说明</h3>
 * <ul>
 *   <li><b>Cluster 路径（主路径）</b>：余额状态由 {@link com.exchange.account.core.cluster.BalanceLedger}
 *       维护，本类的 {@link #upsertBalance} 由 Cluster 异步写入线程调用，将 Cluster 状态同步到
 *       {@code t_user_asset}，供 REST 查询使用。</li>
 *   <li><b>DB 直接路径（降级/Admin）</b>：{@link #freezeAsset}、{@link #unfreezeAsset}、
 *       {@link #settleTrade} 直接操作数据库，配合乐观锁防并发超卖，适用于测试或非 Cluster 场景。</li>
 * </ul>
 *
 * <h3>乐观锁重试</h3>
 * <p>{@code t_user_asset} 使用 {@code @Version} 乐观锁；高频下单场景建议在 Cluster 模式下
 * 禁用 DB 直接路径，由 BalanceLedger 单线程串行处理后异步落库。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AssetServiceImpl implements AssetService {

    private final UserAssetRepository userAssetRepository;
    private final FundFlowService     fundFlowService;

    // =========================================================================
    // 查询
    // =========================================================================

    @Override
    public AssetDTO getBalance(Long userId, String asset) {
        UserAsset ua = findByUserAndAsset(userId, asset);
        return ua != null ? toDTO(ua) : emptyDTO(userId, asset);
    }

    @Override
    public List<AssetDTO> getAllBalances(Long userId) {
        return userAssetRepository.selectList(
                        new LambdaQueryWrapper<UserAsset>()
                                .eq(UserAsset::getUserId, userId)
                                .orderByAsc(UserAsset::getAsset))
                .stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    // =========================================================================
    // 账户类型维度查询（读 t_user_asset 投影，不经过 Raft 日志）
    // =========================================================================

    @Override
    public AssetDTO getBalance(Long userId, AccountType accountType, String asset) {
        UserAsset ua = findByUserTypeAndAsset(userId, accountType, asset);
        if (ua != null) return toDTO(ua);
        // 未持有该资产：返回全零占位（与 Cluster 查询语义一致）
        AssetDTO dto = emptyDTO(userId, asset);
        dto.setAccountType(accountType);
        return dto;
    }

    @Override
    public List<AssetDTO> getAllBalances(Long userId, AccountType accountType) {
        return userAssetRepository.selectList(
                        new LambdaQueryWrapper<UserAsset>()
                                .eq(UserAsset::getUserId, userId)
                                .eq(UserAsset::getAccountType, accountType)
                                .orderByAsc(UserAsset::getAsset))
                .stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    @Override
    public List<AssetDTO> getAllBalancesByType(Long userId) {
        return userAssetRepository.selectList(
                        new LambdaQueryWrapper<UserAsset>()
                                .eq(UserAsset::getUserId, userId)
                                .orderByAsc(UserAsset::getAccountType)
                                .orderByAsc(UserAsset::getAsset))
                .stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    // =========================================================================
    // 冻结 / 解冻（DB 直接路径，带乐观锁重试）
    // =========================================================================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void freezeAsset(Long userId, String asset, BigDecimal amount, String orderId) {
        UserAsset ua = requireAsset(userId, asset);
        if (ua.getAvailableBalance().compareTo(amount) < 0) {
            throw new BusinessException(CommonErrorCode.PARAMETER_ERROR,
                    "余额不足: userId=" + userId + " asset=" + asset
                            + " available=" + ua.getAvailableBalance() + " required=" + amount);
        }
        UserAsset before = copySnapshot(ua);
        ua.setAvailableBalance(ua.getAvailableBalance().subtract(amount));
        ua.setFrozenBalance(ua.getFrozenBalance().add(amount));
        ua.setUpdateTime(LocalDateTime.now());
        userAssetRepository.updateById(ua);

        fundFlowService.record(userId, AccountType.SPOT, asset, FundFlowType.FREEZE,
                amount.negate(), before, orderId, null, "freeze for order " + orderId);
        log.info("[AssetService] FREEZE userId={} asset={} amount={} orderId={}", userId, asset, amount, orderId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void unfreezeAsset(Long userId, String asset, BigDecimal amount, String orderId) {
        UserAsset ua = requireAsset(userId, asset);
        if (ua.getFrozenBalance().compareTo(amount) < 0) {
            throw new BusinessException(CommonErrorCode.PARAMETER_ERROR,
                    "冻结余额不足: userId=" + userId + " asset=" + asset
                            + " frozen=" + ua.getFrozenBalance() + " required=" + amount);
        }
        UserAsset before = copySnapshot(ua);
        ua.setFrozenBalance(ua.getFrozenBalance().subtract(amount));
        ua.setAvailableBalance(ua.getAvailableBalance().add(amount));
        ua.setUpdateTime(LocalDateTime.now());
        userAssetRepository.updateById(ua);

        fundFlowService.record(userId, AccountType.SPOT, asset, FundFlowType.UNFREEZE,
                amount, before, orderId, null, "unfreeze for order " + orderId);
        log.info("[AssetService] UNFREEZE userId={} asset={} amount={} orderId={}", userId, asset, amount, orderId);
    }

    // =========================================================================
    // 成交结算（DB 直接路径，4 步原子操作）
    // =========================================================================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void settleTrade(Long buyerId, Long sellerId,
                            String baseAsset, String quoteAsset,
                            BigDecimal qty, BigDecimal quoteAmt,
                            BigDecimal buyFee, BigDecimal sellFee,
                            String tradeId) {
        // 买方：扣 quoteAsset 冻结，入 baseAsset 可用
        UserAsset buyerQuote = requireAsset(buyerId, quoteAsset);
        BigDecimal buyDeduct = quoteAmt.add(buyFee);
        if (buyerQuote.getFrozenBalance().compareTo(buyDeduct) < 0) {
            throw new BusinessException(CommonErrorCode.PARAMETER_ERROR,
                    "买方冻结余额不足: tradeId=" + tradeId);
        }
        buyerQuote.setFrozenBalance(buyerQuote.getFrozenBalance().subtract(buyDeduct));
        userAssetRepository.updateById(buyerQuote);
        fundFlowService.record(buyerId, AccountType.SPOT, quoteAsset, FundFlowType.TRADE_DEDUCT,
                buyDeduct.negate(), buyerQuote, tradeId, null, "buy deduct tradeId=" + tradeId);

        UserAsset buyerBase = getOrCreateAsset(buyerId, baseAsset);
        buyerBase.setAvailableBalance(buyerBase.getAvailableBalance().add(qty));
        buyerBase.setUpdateTime(LocalDateTime.now());
        saveOrUpdate(buyerBase);
        fundFlowService.record(buyerId, AccountType.SPOT, baseAsset, FundFlowType.TRADE_CREDIT,
                qty, buyerBase, tradeId, null, "buy credit tradeId=" + tradeId);

        // 卖方：扣 baseAsset 冻结，入 quoteAsset 可用
        UserAsset sellerBase = requireAsset(sellerId, baseAsset);
        if (sellerBase.getFrozenBalance().compareTo(qty) < 0) {
            throw new BusinessException(CommonErrorCode.PARAMETER_ERROR,
                    "卖方冻结余额不足: tradeId=" + tradeId);
        }
        sellerBase.setFrozenBalance(sellerBase.getFrozenBalance().subtract(qty));
        userAssetRepository.updateById(sellerBase);
        fundFlowService.record(sellerId, AccountType.SPOT, baseAsset, FundFlowType.TRADE_DEDUCT,
                qty.negate(), sellerBase, tradeId, null, "sell deduct tradeId=" + tradeId);

        BigDecimal sellCredit = quoteAmt.subtract(sellFee);
        UserAsset sellerQuote = getOrCreateAsset(sellerId, quoteAsset);
        sellerQuote.setAvailableBalance(sellerQuote.getAvailableBalance().add(sellCredit));
        sellerQuote.setUpdateTime(LocalDateTime.now());
        saveOrUpdate(sellerQuote);
        fundFlowService.record(sellerId, AccountType.SPOT, quoteAsset, FundFlowType.TRADE_CREDIT,
                sellCredit, sellerQuote, tradeId, null, "sell credit tradeId=" + tradeId);

        log.info("[AssetService] SETTLE tradeId={} buyer={} seller={} qty={} quoteAmt={}",
                tradeId, buyerId, sellerId, qty, quoteAmt);
    }

    // =========================================================================
    // Cluster → DB 余额同步（UPSERT）
    // =========================================================================

    /**
     * 将 BalanceLedger 的余额强制同步到 {@code t_user_asset}（UPSERT 语义）。
     *
     * <p>定位行的键为 {@code (userId, accountType, asset)}，
     * 与 {@code t_user_asset} 唯一索引一致，不同账户类型的余额互不覆盖。
     */
    @Override
    public void upsertBalance(Long userId, AccountType accountType, String asset,
                              BigDecimal available, BigDecimal frozen) {
        UserAsset ua = findByUserTypeAndAsset(userId, accountType, asset);
        if (ua == null) {
            ua = new UserAsset();
            ua.setUserId(userId);
            ua.setAccountType(accountType);
            ua.setAsset(asset);
            ua.setAvailableBalance(available);
            ua.setFrozenBalance(nvl(frozen));
            ua.setStatus(AssetStatus.ACTIVE);
            ua.setCreateTime(LocalDateTime.now());
            ua.setUpdateTime(LocalDateTime.now());
            userAssetRepository.insert(ua);
        } else {
            ua.setAvailableBalance(available);
            ua.setFrozenBalance(nvl(frozen));
            ua.setUpdateTime(LocalDateTime.now());
            userAssetRepository.updateById(ua);
        }
        log.debug("[AssetService] UPSERT balance userId={} accountType={} asset={} available={} frozen={}",
                userId, accountType, asset, available, frozen);
    }

    // =========================================================================
    // Private helpers
    // =========================================================================

    private UserAsset requireAsset(Long userId, String asset) {
        UserAsset ua = findByUserAndAsset(userId, asset);
        if (ua == null) {
            throw new BusinessException(CommonErrorCode.NOT_FOUND,
                    "账户不存在: userId=" + userId + " asset=" + asset);
        }
        return ua;
    }

    private UserAsset findByUserAndAsset(Long userId, String asset) {
        // DB 直接路径（降级/Admin）历史接口未带 accountType，固定视为 SPOT
        return findByUserTypeAndAsset(userId, AccountType.SPOT, asset);
    }

    private UserAsset findByUserTypeAndAsset(Long userId, AccountType accountType, String asset) {
        return userAssetRepository.selectOne(
                new LambdaQueryWrapper<UserAsset>()
                        .eq(UserAsset::getUserId, userId)
                        .eq(UserAsset::getAccountType, accountType)
                        .eq(UserAsset::getAsset, asset)
                        .last("LIMIT 1"));
    }

    private UserAsset getOrCreateAsset(Long userId, String asset) {
        UserAsset ua = findByUserAndAsset(userId, asset);
        if (ua == null) {
            ua = new UserAsset();
            ua.setUserId(userId);
            ua.setAccountType(AccountType.SPOT);   // DB 直接路径固定 SPOT
            ua.setAsset(asset);
            ua.setAvailableBalance(BigDecimal.ZERO);
            ua.setFrozenBalance(BigDecimal.ZERO);
            ua.setStatus(AssetStatus.ACTIVE);
            ua.setCreateTime(LocalDateTime.now());
            ua.setUpdateTime(LocalDateTime.now());
        }
        return ua;
    }

    private void saveOrUpdate(UserAsset ua) {
        if (ua.getId() == null) {
            userAssetRepository.insert(ua);
        } else {
            userAssetRepository.updateById(ua);
        }
    }

    /** 复制操作前快照（用于流水记录 balanceBefore 字段） */
    private UserAsset copySnapshot(UserAsset ua) {
        UserAsset copy = new UserAsset();
        copy.setAvailableBalance(ua.getAvailableBalance());
        copy.setFrozenBalance(ua.getFrozenBalance());
        return copy;
    }

    private AssetDTO toDTO(UserAsset ua) {
        AssetDTO dto = new AssetDTO();
        dto.setUserId(ua.getUserId());
        dto.setAccountType(ua.getAccountType());
        dto.setAsset(ua.getAsset());
        dto.setAvailableBalance(nvl(ua.getAvailableBalance()));
        dto.setFrozenBalance(nvl(ua.getFrozenBalance()));
        dto.setTotalBalance(nvl(ua.getAvailableBalance()).add(nvl(ua.getFrozenBalance())));
        dto.setStatus(ua.getStatus());
        dto.setUpdateTime(ua.getUpdateTime());
        return dto;
    }

    private AssetDTO emptyDTO(Long userId, String asset) {
        AssetDTO dto = new AssetDTO();
        dto.setUserId(userId);
        dto.setAsset(asset);
        dto.setAvailableBalance(BigDecimal.ZERO);
        dto.setFrozenBalance(BigDecimal.ZERO);
        dto.setTotalBalance(BigDecimal.ZERO);
        return dto;
    }

    private static BigDecimal nvl(BigDecimal v) {
        return v != null ? v : BigDecimal.ZERO;
    }
}
