package com.exchange.account.api.dto;

import com.exchange.account.api.enums.AccountType;
import com.exchange.account.api.enums.AssetStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 资产账户视图（对外返回） */
@Data
@Schema(description = "资产账户信息")
public class AssetDTO {

    @Schema(description = "用户 ID")
    private Long userId;

    @Schema(description = "账户类型，如 SPOT / FUTURES")
    private AccountType accountType;

    @Schema(description = "资产代码，如 BTC、USDT")
    private String asset;

    @Schema(description = "总余额 = 可用余额 + 冻结余额")
    private BigDecimal totalBalance;

    @Schema(description = "可用余额（可下单部分）")
    private BigDecimal availableBalance;

    @Schema(description = "冻结余额（委托占用部分）")
    private BigDecimal frozenBalance;

    @Schema(description = "账户状态")
    private AssetStatus status;

    @Schema(description = "更新时间")
    private LocalDateTime updateTime;
}
