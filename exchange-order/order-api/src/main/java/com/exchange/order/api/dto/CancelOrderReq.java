package com.exchange.order.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/** 撤单请求 */
@Data
@Schema(description = "撤单请求")
public class CancelOrderReq {

    @NotNull
    @Schema(description = "用户 ID")
    private Long userId;

    @Schema(description = "系统订单 ID（与 clientOrderId 二选一）")
    private Long orderId;

    @Schema(description = "客户端自定义订单 ID（与 orderId 二选一）")
    private String clientOrderId;

    @NotNull
    @Schema(description = "交易对")
    private String symbol;
}
