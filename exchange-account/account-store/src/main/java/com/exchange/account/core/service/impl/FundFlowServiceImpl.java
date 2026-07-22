package com.exchange.account.core.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.exchange.account.api.dto.FundFlowDTO;
import com.exchange.account.api.dto.FundFlowQueryReq;
import com.exchange.account.api.enums.AccountType;
import com.exchange.account.api.enums.FundFlowType;
import com.exchange.account.core.entity.FundFlow;
import com.exchange.account.core.entity.UserAsset;
import com.exchange.account.core.repository.FundFlowRepository;
import com.exchange.account.core.service.FundFlowService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 资金流水服务实现。
 *
 * <p>仅负责 {@code t_fund_flow} 表的 append-only 写入和分页查询；
 * 不做余额操作（余额由 {@link AssetServiceImpl} 或 {@link com.exchange.account.core.cluster.AssetClusteredService} 维护）。
 *
 * <h3>调用方</h3>
 * <ul>
 *   <li>Cluster 路径：{@code AssetClusteredService#dbWriteExecutor} 异步写入（assetSnapshot 为 null）。</li>
 *   <li>非 Cluster 路径：{@code AssetServiceImpl} 在同步操作后写入（assetSnapshot 为真实快照）。</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FundFlowServiceImpl implements FundFlowService {

    private final FundFlowRepository fundFlowRepository;

    /**
     * 记录一条资金流水。
     *
     * <p>{@code assetSnapshot} 为操作完成后的账户快照（可为 null，此时余额字段不写）。
     * 若 assetSnapshot 非 null，从其中读取"变动后"余额，并按 flowType 反推"变动前"余额近似值。
     *
     * <h3>eventId 幂等</h3>
     * <p>eventId 非 null 时先查 {@code event_id} 是否已存在，存在则跳过（Archive 重放安全）。
     * 单消费者进程内 select-then-insert 无竞争；DB 层 {@code event_id} UNIQUE 索引兜底，
     * 若并发插入触发 DuplicateKey 同样按幂等跳过处理，不向上抛出。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean record(Long userId, AccountType accountType, String asset, FundFlowType flowType,
                          BigDecimal amount, UserAsset assetSnapshot,
                          String bizNo, String eventId, String remark) {
        // 幂等检查（Archive 重放 / 崩溃恢复时重复事件安全跳过）
        if (eventId != null && fundFlowRepository.selectCount(
                new LambdaQueryWrapper<FundFlow>().eq(FundFlow::getEventId, eventId)) > 0) {
            log.debug("[FundFlowService] Duplicate eventId={} skipped", eventId);
            return false;
        }

        FundFlow flow = new FundFlow();
        flow.setEventId(eventId);
        flow.setUserId(userId);
        flow.setAccountType(accountType);
        flow.setAsset(asset);
        flow.setBizType(flowType.getBizType());   // 主类型自动推导
        flow.setFlowType(flowType);
        flow.setAmount(amount);
        flow.setBizNo(bizNo);
        flow.setRemark(remark);
        flow.setCreateTime(LocalDateTime.now());

        if (assetSnapshot != null) {
            BigDecimal availableAfter = nvl(assetSnapshot.getAvailableBalance());
            BigDecimal frozenAfter    = nvl(assetSnapshot.getFrozenBalance());
            flow.setAvailableBalanceAfter(availableAfter);
            flow.setFrozenBalanceAfter(frozenAfter);

            // 反推变动前余额（近似值，用于审计展示；精确值需在操作前采集快照）
            switch (flowType) {
                case FREEZE -> {
                    // available 减少 amount，frozen 增加 amount
                    flow.setAvailableBalanceBefore(availableAfter.add(amount.abs()));
                    flow.setFrozenBalanceBefore(frozenAfter.subtract(amount.abs()));
                }
                case UNFREEZE -> {
                    // available 增加 amount，frozen 减少 amount
                    flow.setAvailableBalanceBefore(availableAfter.subtract(amount.abs()));
                    flow.setFrozenBalanceBefore(frozenAfter.add(amount.abs()));
                }
                case TRADE_CREDIT, DEPOSIT, FEE_REBATE -> {
                    // available 增加（amount 为正）
                    flow.setAvailableBalanceBefore(availableAfter.subtract(amount));
                    flow.setFrozenBalanceBefore(frozenAfter);
                }
                case TRADE_DEDUCT, WITHDRAW, FEE_DEDUCT -> {
                    // frozen 减少（amount 为负）
                    flow.setAvailableBalanceBefore(availableAfter);
                    flow.setFrozenBalanceBefore(frozenAfter.subtract(amount)); // amount < 0, 所以 subtract(negative) = add
                }
                default -> {
                    flow.setAvailableBalanceBefore(availableAfter.subtract(amount));
                    flow.setFrozenBalanceBefore(frozenAfter);
                }
            }
        }

        try {
            fundFlowRepository.insert(flow);
        } catch (org.springframework.dao.DuplicateKeyException e) {
            // event_id UNIQUE 索引兜底：并发/重放下已存在 → 幂等跳过，不能抛出
            // （抛出会让 Archive 消费者对同一事件无限重试，形成毒消息死循环）
            log.warn("[FundFlowService] DuplicateKey on eventId={}, treated as idempotent skip", eventId);
            return false;
        }
        log.debug("[FundFlowService] Recorded — userId={} accountType={} asset={} type={} amount={} bizNo={}",
                userId, accountType, asset, flowType, amount, bizNo);
        return true;
    }

    /**
     * 分页查询资金流水（按 createTime 倒序）。
     */
    @Override
    public List<FundFlowDTO> queryFlows(FundFlowQueryReq req) {
        LambdaQueryWrapper<FundFlow> query = new LambdaQueryWrapper<FundFlow>()
                .eq(FundFlow::getUserId,                              req.getUserId())
                .eq(req.getAsset()    != null, FundFlow::getAsset,   req.getAsset())
                .eq(req.getBizType()  != null, FundFlow::getBizType, req.getBizType())   // 主类型过滤
                .eq(req.getFlowType() != null, FundFlow::getFlowType,req.getFlowType())  // 子类型过滤
                .ge(req.getStartTime()!= null, FundFlow::getCreateTime, req.getStartTime())
                .le(req.getEndTime()  != null, FundFlow::getCreateTime, req.getEndTime())
                .orderByDesc(FundFlow::getCreateTime);

        Page<FundFlow> page = new Page<>(req.getPage(), req.getPageSize());
        return fundFlowRepository.selectPage(page, query)
                .getRecords()
                .stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private FundFlowDTO toDTO(FundFlow f) {
        FundFlowDTO dto = new FundFlowDTO();
        dto.setFlowId(f.getFlowId());
        dto.setUserId(f.getUserId());
        dto.setAsset(f.getAsset());
        dto.setBizType(f.getBizType());
        dto.setBizTypeDesc(f.getBizType() != null ? f.getBizType().getDesc() : null);
        dto.setFlowType(f.getFlowType());
        dto.setFlowTypeDesc(f.getFlowType() != null ? f.getFlowType().getDesc() : null);
        dto.setAmount(f.getAmount());
        dto.setBalanceBefore(f.getAvailableBalanceBefore());
        dto.setBalanceAfter(f.getAvailableBalanceAfter());
        dto.setBizNo(f.getBizNo());
        dto.setRemark(f.getRemark());
        dto.setCreateTime(f.getCreateTime());
        return dto;
    }

    private static BigDecimal nvl(BigDecimal v) {
        return v != null ? v : BigDecimal.ZERO;
    }
}
