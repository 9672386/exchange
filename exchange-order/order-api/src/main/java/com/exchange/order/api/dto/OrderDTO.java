package com.exchange.order.api.dto;

import com.exchange.order.api.enums.OrderSide;
import com.exchange.order.api.enums.OrderStatus;
import com.exchange.order.api.enums.OrderType;
import com.exchange.order.api.enums.TimeInForce;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 订单视图对象（对外返回） */
@Data
@Schema(description = "订单信息")
public class OrderDTO {

    @Schema(description = "系统订单 ID")
    private Long orderId;

    @Schema(description = "客户端自定义订单 ID")
    private String clientOrderId;

    @Schema(description = "用户 ID")
    private Long userId;

    @Schema(description = "交易对")
    private String symbol;

    @Schema(description = "买卖方向")
    private OrderSide side;

    @Schema(description = "订单类型")
    private OrderType orderType;

    @Schema(description = "有效期策略")
    private TimeInForce timeInForce;

    @Schema(description = "订单状态")
    private OrderStatus status;

    @Schema(description = "委托数量")
    private BigDecimal quantity;

    @Schema(description = "委托价格")
    private BigDecimal price;

    @Schema(description = "止损触发价")
    private BigDecimal stopPrice;

    @Schema(description = "已成交数量")
    private BigDecimal executedQty;

    @Schema(description = "已成交均价")
    private BigDecimal avgPrice;

    @Schema(description = "累计成交金额")
    private BigDecimal cummulativeQuoteQty;

    @Schema(description = "下单时间")
    private LocalDateTime createTime;

    @Schema(description = "最后更新时间")
    private LocalDateTime updateTime;
}
