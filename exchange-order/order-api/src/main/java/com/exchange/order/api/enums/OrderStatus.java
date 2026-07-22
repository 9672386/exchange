package com.exchange.order.api.enums;

/**
 * 订单状态枚举。
 *
 * <p>状态流转：
 * <pre>
 *   PENDING_NEW → NEW → PARTIALLY_FILLED → FILLED
 *                   ↘ CANCELLED
 *                   ↘ REJECTED
 * </pre>
 */
public enum OrderStatus {

    /** 已提交，等待系统确认（前置风控未完成） */
    PENDING_NEW,

    /** 已被撮合引擎接受，挂单中 */
    NEW,

    /** 部分成交 */
    PARTIALLY_FILLED,

    /** 全部成交 */
    FILLED,

    /** 已撤单 */
    CANCELLED,

    /** 被系统拒绝（余额不足、参数非法等） */
    REJECTED;
}
