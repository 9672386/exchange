package com.exchange.match.core.model;

import com.exchange.match.enums.OrderSide;
import com.exchange.match.enums.OrderType;
import com.exchange.match.enums.PositionAction;
import com.exchange.common.math.FixedPoint;

import lombok.Data;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;

/**
 * 订单模型（撮合内部状态,定点 long）。
 *
 * <p><b>定点表示</b>(见 {@code docs/fixed-point-migration-design.md} §8):
 * <ul>
 *   <li>{@code price} —— priceScale(= {@code Symbol.pricePrecision})下的 raw</li>
 *   <li>{@code quantity/filledQuantity/remainingQuantity} —— baseScale(= {@code Symbol.quantityPrecision})下的 raw</li>
 * </ul>
 * Order 本身不持有 scale;同一 symbol 下所有 Order 的价/量必定同 scale,故比较加减是纯 long 运算。
 * scale 由入场/撮合/引擎在边界处按 Symbol 应用(BigDecimal↔long)。
 *
 * <p>{@code leverage} 仍用 BigDecimal(冷字段,合约保证金计算用,不在撮合热路径)。
 */
@Data
public class Order {

    /**
     * 订单ID
     */
    private String orderId;

    /**
     * 用户ID
     */
    private Long userId;

    /**
     * 交易对
     */
    private String symbol;

    /**
     * 订单方向（买/卖）
     */
    private OrderSide side;

    /**
     * 订单类型
     */
    private OrderType type;

    /**
     * 开平仓动作
     */
    private PositionAction positionAction;

    /**
     * 价格（priceScale 下的定点 raw）
     */
    private long price;

    /**
     * 数量（baseScale 下的定点 raw）
     */
    private long quantity;

    /**
     * 已成交数量（baseScale 下的定点 raw）
     */
    private long filledQuantity;

    /**
     * 剩余数量（baseScale 下的定点 raw）
     */
    private long remainingQuantity;

    /**
     * 订单状态
     */
    private OrderStatus status;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;

    /**
     * 成交时间
     */
    private LocalDateTime fillTime;

    /**
     * 客户端订单ID
     */
    private String clientOrderId;

    /**
     * 杠杆倍数（冷字段,保持 BigDecimal）
     */
    private BigDecimal leverage;

    /**
     * 备注
     */
    private String remark;

    public Order() {
        this.createTime = LocalDateTime.now();
        this.updateTime = LocalDateTime.now();
        this.filledQuantity = 0L;
        this.remainingQuantity = 0L;
        this.positionAction = null; // 默认为null，根据交易类型设置
        this.leverage = BigDecimal.ONE; // 默认杠杆为1倍
    }

    /**
     * 检查订单是否可以成交
     */
    public boolean canMatch(long matchPrice) {
        if (status != OrderStatus.ACTIVE) {
            return false;
        }

        if (remainingQuantity <= 0) {
            return false;
        }

        if (side == OrderSide.BUY) {
            return matchPrice <= price;
        } else {
            return matchPrice >= price;
        }
    }

    /**
     * 更新成交数量
     */
    public void updateFilledQuantity(long fillQuantity) {
        this.filledQuantity = Math.addExact(this.filledQuantity, fillQuantity);
        this.remainingQuantity = Math.subtractExact(this.quantity, this.filledQuantity);
        this.updateTime = LocalDateTime.now();

        if (this.remainingQuantity <= 0) {
            this.status = OrderStatus.FILLED;
            this.fillTime = LocalDateTime.now();
        }
    }

    /**
     * 取消订单
     */
    public void cancel() {
        this.status = OrderStatus.CANCELLED;
        this.updateTime = LocalDateTime.now();
    }

    /**
     * 获取成交率（0..1，scale=4 的 BigDecimal 视图;filled 与 quantity 同 baseScale，比值 scale 抵消）。
     */
    public BigDecimal getFillRate() {
        if (quantity == 0) {
            return BigDecimal.ZERO;
        }
        long rate4 = FixedPoint.mulDiv(filledQuantity, 10_000L, quantity, RoundingMode.HALF_UP);
        return FixedPoint.toBigDecimal(rate4, 4);
    }

    /**
     * 检查是否完全成交
     */
    public boolean isFullyFilled() {
        return remainingQuantity == 0;
    }

    /**
     * 检查是否部分成交
     */
    public boolean isPartiallyFilled() {
        return filledQuantity > 0 && remainingQuantity > 0;
    }
}
