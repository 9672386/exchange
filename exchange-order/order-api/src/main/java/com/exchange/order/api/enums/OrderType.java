package com.exchange.order.api.enums;

/** 订单类型 */
public enum OrderType {

    /** 限价单：指定价格挂单 */
    LIMIT,

    /** 市价单：以当前最优价立即成交 */
    MARKET,

    /** 限价止损单 */
    STOP_LIMIT,

    /** 市价止损单 */
    STOP_MARKET;
}
