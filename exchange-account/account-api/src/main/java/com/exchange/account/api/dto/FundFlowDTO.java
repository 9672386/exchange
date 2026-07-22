package com.exchange.account.api.dto;

import com.exchange.account.api.enums.FundFlowBizType;
import com.exchange.account.api.enums.FundFlowType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 资金流水视图（对外返回） */
@Data
@Schema(description = "资金流水")
public class FundFlowDTO {

    @Schema(description = "流水 ID")
    private Long flowId;

    @Schema(description = "用户 ID")
    private Long userId;

    @Schema(description = "资产代码")
    private String asset;

    @Schema(description = "主类型（委托 / 成交结算 / 充提 / 手续费）")
    private FundFlowBizType bizType;

    @Schema(description = "主类型描述")
    private String bizTypeDesc;

    @Schema(description = "子类型（具体操作）")
    private FundFlowType flowType;

    @Schema(description = "子类型描述")
    private String flowTypeDesc;

    @Schema(description = "变动金额（正数为入账，负数为出账）")
    private BigDecimal amount;

    @Schema(description = "变动前余额")
    private BigDecimal balanceBefore;

    @Schema(description = "变动后余额")
    private BigDecimal balanceAfter;

    @Schema(description = "关联业务单号（orderId / tradeId / withdrawId 等）")
    private String bizNo;

    @Schema(description = "备注")
    private String remark;

    @Schema(description = "发生时间")
    private LocalDateTime createTime;
}
