package com.exchange.order.api.dto;

import com.exchange.order.api.enums.OrderSide;
import com.exchange.order.api.enums.OrderStatus;
import com.exchange.order.api.enums.OrderType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDateTime;

/** 订单查询请求（分页） */
@Data
@Schema(description = "订单查询请求")
public class OrderQueryReq {

    @NotNull
    @Schema(description = "用户 ID")
    private Long userId;

    @Schema(description = "交易对过滤")
    private String symbol;

    @Schema(description = "订单状态过滤")
    private OrderStatus status;

    @Schema(description = "买卖方向过滤")
    private OrderSide side;

    @Schema(description = "订单类型过滤")
    private OrderType orderType;

    @Schema(description = "查询起始时间")
    private LocalDateTime startTime;

    @Schema(description = "查询结束时间")
    private LocalDateTime endTime;

    @Min(1)
    @Schema(description = "页码，从 1 开始", defaultValue = "1")
    private int page = 1;

    @Min(1)
    @Max(500)
    @Schema(description = "每页条数", defaultValue = "20")
    private int pageSize = 20;
}
