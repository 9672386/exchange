package com.exchange.order.api.enums;

/** 订单有效期策略 */
public enum TimeInForce {

    /** Good Till Cancelled：持续挂单直到成交或主动撤单 */
    GTC,

    /** Immediate Or Cancel：立即成交可成交部分，剩余自动撤销 */
    IOC,

    /** Fill Or Kill：必须立即全部成交，否则完全撤销 */
    FOK;
}
