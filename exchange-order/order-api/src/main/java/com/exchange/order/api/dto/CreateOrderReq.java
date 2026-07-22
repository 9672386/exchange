package com.exchange.order.api.dto;

import com.exchange.order.api.enums.OrderSide;
import com.exchange.order.api.enums.OrderType;
import com.exchange.order.api.enums.TimeInForce;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

/** 下单请求 */
@Data
@Schema(description = "下单请求")
public class CreateOrderReq {

    @NotNull
    @Schema(description = "用户 ID")
    private Long userId;

    @NotBlank
    @Schema(description = "交易对，例如 BTC_USDT", example = "BTC_USDT")
    private String symbol;

    @NotNull
    @Schema(description = "买卖方向")
    private OrderSide side;

    @NotNull
    @Schema(description = "订单类型")
    private OrderType orderType;

    @Schema(description = "有效期策略，默认 GTC")
    private TimeInForce timeInForce = TimeInForce.GTC;

    @DecimalMin(value = "0", inclusive = false)
    @Schema(description = "委托数量")
    private BigDecimal quantity;

    @Schema(description = "委托价格（市价单可为 null）")
    private BigDecimal price;

    @Schema(description = "止损触发价（止损单专用）")
    private BigDecimal stopPrice;

    @Schema(description = "客户端自定义订单 ID（幂等去重）")
    private String clientOrderId;
}
