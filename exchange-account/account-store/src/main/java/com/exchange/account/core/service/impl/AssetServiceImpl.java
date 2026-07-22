package com.exchange.account.core.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.exchange.account.api.dto.AssetDTO;
import com.exchange.account.api.enums.AccountType;
import com.exchange.account.api.enums.AssetStatus;
import com.exchange.account.core.entity.UserAsset;
import com.exchange.account.core.repository.UserAssetRepository;
import com.exchange.account.core.service.AssetService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 资产账户投影服务实现（CQRS 读侧 + 事件落库）。
 *
 * <p>只做两件事：
 * <ul>
 *   <li><b>查询</b>：读 {@code t_user_asset} 投影,供 REST 返回余额（最终一致）。</li>
 *   <li><b>{@link #upsertBalance 落库}</b>：account-persist 消费 AssetStateChangeEvent 后调用,
 *       把内存账本余额同步到 {@code t_user_asset}。</li>
 * </ul>
 * <p>权威余额在 Cluster 内存账本,本类不做任何账本 mutation,也不对外提供冻结/结算接口。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AssetServiceImpl implements AssetService {

    private final UserAssetRepository userAssetRepository;

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
