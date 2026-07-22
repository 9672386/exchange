package com.exchange.account.api.dto;

import com.exchange.account.api.enums.AccountType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

/** 资产冻结/解冻请求 */
@Data
@Schema(description = "资产冻结/解冻请求")
public class FreezeReq {

    @NotNull
    @Schema(description = "用户 ID")
    private Long userId;

    @NotNull
    @Schema(description = "账户类型，如 SPOT / FUTURES")
    private AccountType accountType;

    @NotBlank
    @Schema(description = "资产代码，如 USDT")
    private String asset;

    @NotNull
    @DecimalMin(value = "0", inclusive = false)
    @Schema(description = "操作金额")
    private BigDecimal amount;

    @NotBlank
    @Schema(description = "关联订单 ID（用于幂等校验）")
    private String orderId;
}
