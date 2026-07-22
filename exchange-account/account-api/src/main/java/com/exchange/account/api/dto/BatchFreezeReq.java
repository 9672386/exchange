package com.exchange.account.api.dto;

import com.exchange.account.api.enums.AccountType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 批量冻结请求（单用户，多委托）。
 *
 * <h3>原子语义</h3>
 * <p>所有子项属于同一用户，整体原子：任一资产可用余额不足则整批拒绝，账本不做任何修改。
 * 适用于网格策略、批量挂单等同时提交多笔委托的场景。
 *
 * <h3>幂等</h3>
 * <p>已处理的 {@code orderId} 自动跳过，重试安全。
 */
@Data
@Schema(description = "批量冻结请求（单用户，原子）")
public class BatchFreezeReq {

    @NotNull
    @Schema(description = "用户 ID")
    private Long userId;

    @NotNull
    @Schema(description = "账户类型，如 SPOT / FUTURES（所有子项使用相同账户类型）")
    private AccountType accountType;

    @Valid
    @NotEmpty
    @Schema(description = "冻结子项列表")
    private List<Item> items;

    /** 单个冻结子项（对应一笔委托）。 */
    @Data
    @Schema(description = "冻结子项")
    public static class Item {

        @NotBlank
        @Schema(description = "关联订单 ID（幂等键）")
        private String orderId;

        @NotBlank
        @Schema(description = "资产代码，如 USDT")
        private String asset;

        @NotNull
        @DecimalMin(value = "0", inclusive = false)
        @Schema(description = "冻结金额")
        private BigDecimal amount;
    }
}
