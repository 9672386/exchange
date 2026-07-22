package com.exchange.account.api.dto;

import com.exchange.account.api.enums.AccountType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 用户内部划转请求（同用户跨账户类型）。
 *
 * <p>典型场景：
 * <ul>
 *   <li>充值后资金从 FUNDING → SPOT（用户主动划转，才能参与现货交易）</li>
 *   <li>从 SPOT → FUTURES（用户主动划转合约保证金）</li>
 *   <li>从 FUTURES → FUNDING（合约盈亏取出）</li>
 * </ul>
 *
 * <p>操作在单次 Raft 日志写入中原子完成：出账和入账不存在中间状态。
 *
 * <p>{@code bizNo} 作为幂等键（Snowflake 格式），30 min 内重复提交被 Cluster 安全忽略。
 * {@code fromAccountType} 和 {@code toAccountType} 不能相同。
 */
@Data
@Schema(description = "用户内部划转请求（同用户跨账户类型，如 FUNDING→SPOT）")
public class InternalTransferReq {

    @NotNull
    @Schema(description = "用户 ID")
    private Long userId;

    @NotNull
    @Schema(description = "出账账户类型（划出方）")
    private AccountType fromAccountType;

    @NotNull
    @Schema(description = "入账账户类型（划入方），不能与 fromAccountType 相同")
    private AccountType toAccountType;

    @NotBlank
    @Schema(description = "资产代码，如 USDT / BTC")
    private String asset;

    @NotNull
    @DecimalMin(value = "0", inclusive = false)
    @Schema(description = "划转金额（必须 > 0）")
    private BigDecimal amount;

    @NotBlank
    @Schema(description = "划转单号（幂等键，Snowflake 格式）")
    private String bizNo;

    @Schema(description = "备注（可选）")
    private String remark;
}
