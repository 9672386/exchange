package com.exchange.order.core.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.exchange.order.api.enums.OrderSide;
import com.exchange.order.api.enums.OrderStatus;
import com.exchange.order.api.enums.OrderType;
import com.exchange.order.api.enums.TimeInForce;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 订单持久化实体。
 *
 * <p>对应数据库表 {@code t_order_record}。
 * 撮合引擎不直接写库，由 {@link com.exchange.order.core.consumer.OrderStateConsumer}
 * 消费 Kafka {@code state-changes} 主题后写入。
 */
@Data
@TableName("t_order_record")
public class OrderRecord {

    /** 系统订单 ID（Snowflake / 数据库自增） */
    @TableId(type = IdType.ASSIGN_ID)
    private Long orderId;

    /** 客户端自定义幂等 ID */
    private String clientOrderId;

    /** 用户 ID */
    private Long userId;

    /** 交易对，如 BTC_USDT */
    private String symbol;

    /** 买卖方向 */
    private OrderSide side;

    /** 订单类型 */
    private OrderType orderType;

    /** 有效期策略 */
    private TimeInForce timeInForce;

    /** 当前状态 */
    private OrderStatus status;

    /** 委托数量 */
    private BigDecimal quantity;

    /** 委托价格（市价单为 null） */
    private BigDecimal price;

    /** 止损触发价 */
    private BigDecimal stopPrice;

    /** 已成交数量（累计） */
    private BigDecimal executedQty;

    /** 累计成交金额 */
    private BigDecimal cummulativeQuoteQty;

    /** 成交均价（冗余，方便查询） */
    private BigDecimal avgPrice;

    /** 拒绝/撤单原因 */
    private String rejectReason;

    /** 下单时间 */
    private LocalDateTime createTime;

    /** 最后状态变更时间 */
    private LocalDateTime updateTime;
}
