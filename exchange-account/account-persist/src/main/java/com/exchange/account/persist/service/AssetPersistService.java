package com.exchange.account.persist.service;

import com.exchange.account.api.dto.AssetStateChangeEvent;
import com.exchange.account.core.service.AssetService;
import com.exchange.account.core.service.FundFlowService;
import com.exchange.account.persist.repository.ArchivePositionMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 资产事件持久化服务（单事务原子写入）。
 *
 * <h3>为什么必须单事务</h3>
 * <p>旧实现中「写流水 → UPSERT 余额 → 更新消费位点」是三次独立提交，
 * 任意中间点崩溃都会产生不一致窗口：
 * <ul>
 *   <li>流水已写、位点未推进 → 重启后重放同一事件 → 依赖幂等兜底（曾是虚设）</li>
 *   <li>流水已写、余额未 UPSERT → t_fund_flow 与 t_user_asset 短暂矛盾</li>
 * </ul>
 * 现在三步在同一个数据库事务中提交：事件要么完整落库且位点推进，要么整体回滚。
 * 崩溃后重启从旧位点重放，{@code event_id} 幂等检查保证不重复插入。
 *
 * <h3>幂等</h3>
 * <p>{@link FundFlowService#record} 内部以 {@code eventId} 做去重
 * （select-exists + DB UNIQUE 索引双保险，重复事件安全跳过而非抛异常）。
 * 流水被幂等跳过时余额 UPSERT 仍执行——事件携带的是操作后绝对余额快照，
 * 重复应用是幂等的（同值覆盖），不会造成错账。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AssetPersistService {

    private final FundFlowService       fundFlowService;
    private final AssetService          assetService;
    private final ArchivePositionMapper positionMapper;

    /**
     * 原子持久化一条资产状态变更事件 + 推进消费位点。
     *
     * @param event       资产状态变更事件
     * @param recordingId Archive recording ID
     * @param channel     录制 channel（写入位点表）
     * @param streamId    录制 stream ID（写入位点表）
     * @param position    本事件对应的 Archive byte position
     */
    @Transactional(rollbackFor = Exception.class)
    public void persistEvent(AssetStateChangeEvent event,
                             long recordingId, String channel, int streamId, long position) {
        // 1. 写资金流水（eventId 幂等，重复事件返回 false 并跳过）
        boolean inserted = fundFlowService.record(
                event.getUserId(),
                event.getAccountType(),
                event.getAsset(),
                event.getFlowType(),
                event.getAmount(),
                null,
                event.getBizNo(),
                event.getEventId(),
                event.getRemark());

        // 2. UPSERT 余额快照（绝对值覆盖，天然幂等）
        assetService.upsertBalance(
                event.getUserId(),
                event.getAccountType(),
                event.getAsset(),
                event.getAvailable(),
                event.getFrozen());

        // 3. 推进消费位点（与 1、2 同事务提交）
        positionMapper.upsertPosition(recordingId, channel, streamId, position);

        if (!inserted) {
            log.debug("[AssetPersist] eventId={} was duplicate, balance/position still applied",
                    event.getEventId());
        }
    }
}
