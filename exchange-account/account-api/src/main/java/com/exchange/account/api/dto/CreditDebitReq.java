package com.exchange.account.api.dto;

import com.exchange.account.api.enums.AccountType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 加钱 / 减钱请求（CREDIT / DEBIT）。
 *
 * <p>CREDIT：充值到账、奖励发放、活动补偿等，直接增加 available。
 * <p>DEBIT：提现扣款、风控扣罚等，直接减少 available（不经过冻结流程）。
 *
 * <p>{@code bizNo} 作为幂等键，通常填外部单号（depositId / withdrawId / rewardId）。
 * 同一 bizNo 重复请求将被 Cluster 幂等忽略，不会重复操作资产。
 */
@Data
@Schema(description = "加钱/减钱请求")
public class CreditDebitReq {

    @NotNull
    @Schema(description = "用户 ID")
    private Long userId;

    @NotNull
    @Schema(description = "账户类型，如 SPOT / FUTURES / FUNDING。充提默认 FUNDING")
    private AccountType accountType;

    @NotBlank
    @Schema(description = "资产代码，如 USDT / BTC")
    private String asset;

    @NotNull
    @DecimalMin(value = "0", inclusive = false)
    @Schema(description = "操作金额（必须 > 0）")
    private BigDecimal amount;

    @NotBlank
    @Schema(description = "业务单号（幂等键），如 depositId / withdrawId / rewardId")
    private String bizNo;

    @Schema(description = "备注（可选，用于流水记录）")
    private String remark;
}
