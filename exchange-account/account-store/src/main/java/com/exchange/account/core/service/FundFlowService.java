package com.exchange.account.core.service;

import com.exchange.account.api.dto.FundFlowDTO;
import com.exchange.account.api.dto.FundFlowQueryReq;
import com.exchange.account.api.enums.AccountType;
import com.exchange.account.api.enums.FundFlowType;
import com.exchange.account.core.entity.UserAsset;

import java.math.BigDecimal;
import java.util.List;

/**
 * 资金流水服务接口。
 *
 * <p>负责流水的记录与查询；流水由 {@link AssetService} 在资产变动时内部调用，
 * 外部通过此接口查询对账。
 */
public interface FundFlowService {

    /**
     * 记录一条资金流水。
     *
     * <h3>幂等</h3>
     * <p>{@code eventId} 非 null 时以其为幂等键：若 {@code t_fund_flow.event_id}
     * 已存在同值记录则跳过插入（安全重放）。{@code eventId} 为 null 时不做去重
     * （同步 DB 直接路径,调用方自身在事务内保证不重复）。
     *
     * @param userId         用户 ID
     * @param accountType    账户类型（SPOT / FUTURES / FUNDING ...）
     * @param asset          资产代码
     * @param flowType       流水类型
     * @param amount         变动金额（有符号）
     * @param assetSnapshot  操作后的资产账户快照（用于记录变动后余额,可为 null）
     * @param bizNo          关联业务单号
     * @param eventId        事件 ID（幂等键,可为 null）
     * @param remark         备注
     * @return {@code true} = 实际插入；{@code false} = eventId 已存在,幂等跳过
     */
    boolean record(Long userId, AccountType accountType, String asset, FundFlowType flowType,
                   BigDecimal amount, UserAsset assetSnapshot,
                   String bizNo, String eventId, String remark);

    /**
     * 分页查询资金流水。
     */
    List<FundFlowDTO> queryFlows(FundFlowQueryReq req);
}
