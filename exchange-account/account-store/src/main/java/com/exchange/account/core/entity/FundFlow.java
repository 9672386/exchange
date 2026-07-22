package com.exchange.account.core.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.exchange.account.api.enums.AccountType;
import com.exchange.account.api.enums.FundFlowBizType;
import com.exchange.account.api.enums.FundFlowType;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 资金流水实体。
 *
 * <p>对应数据库表 {@code t_fund_flow}，仅追加写（append-only），不做物理删除。
 * 每笔资产变动（冻结、解冻、成交入账等）均记录一条流水，保证资金可审计。
 */
@Data
@TableName("t_fund_flow")
public class FundFlow {

    @TableId(type = IdType.ASSIGN_ID)
    private Long flowId;

    /**
     * 事件 ID（幂等键）。
     *
     * <p>Cluster 事件路径填 {@link com.exchange.account.api.dto.AssetStateChangeEvent#getEventId()}，
     * 同步 DB 直接路径为 null。
     *
     * <p><b>表结构要求：必须在 {@code t_fund_flow.event_id} 上建 UNIQUE 索引</b>
     * （NULL 值不参与唯一约束），这是 Archive 重放跨重启幂等的最终兜底。
     * 注意：不能用 (biz_no, flow_type) 做唯一键——同一 tradeId 买卖双方
     * 各产生一条 TRADE_DEDUCT，会误判冲突。
     */
    private String eventId;

    /** 用户 ID */
    private Long userId;

    /**
     * 账户类型。
     *
     * <p>普通操作（冻结/解冻/结算）填操作所在账户类型（如 SPOT）。
     * 内部划转场景（SPOT→FUTURES）出账方填出账账户类型，
     * 对应入账流水的 accountType 填入账账户类型。
     */
    private AccountType accountType;

    /** 资产代码 */
    private String asset;

    /**
     * 主类型（业务大类）。
     *
     * <p>由 {@link FundFlowType#getBizType()} 自动推导写入，
     * 用于报表分组和前端大类筛选，不需要额外传参。
     */
    private FundFlowBizType bizType;

    /** 子类型（具体操作） */
    private FundFlowType flowType;

    /**
     * 变动金额。
     * <ul>
     *   <li>正数：入账（DEPOSIT / TRADE_CREDIT / FEE_REBATE 等）</li>
     *   <li>负数：出账（WITHDRAW / TRADE_DEDUCT / FEE_DEDUCT 等）</li>
     * </ul>
     */
    private BigDecimal amount;

    /** 操作前可用余额快照 */
    private BigDecimal availableBalanceBefore;

    /** 操作后可用余额快照 */
    private BigDecimal availableBalanceAfter;

    /** 操作前冻结余额快照 */
    private BigDecimal frozenBalanceBefore;

    /** 操作后冻结余额快照 */
    private BigDecimal frozenBalanceAfter;

    /** 关联业务单号（orderId / tradeId / withdrawId 等），用于对账 */
    private String bizNo;

    /** 备注 */
    private String remark;

    /** 流水生成时间 */
    private LocalDateTime createTime;
}
