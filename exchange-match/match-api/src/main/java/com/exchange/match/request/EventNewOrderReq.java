package com.exchange.match.request;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

@Data
public class EventNewOrderReq implements Serializable {
    private String orderId;
    private long userId;
    private String symbol;
    private String side; // 使用字符串，在handler中转换
    private String orderType; // 使用字符串，在handler中转换
    private BigDecimal price;
    private BigDecimal quantity;
    private String positionAction; // 使用字符串，在handler中转换
    private String clientOrderId;
    private String remark;

    /**
     * 下单冻结的资产代码(限价买=quote,限价卖=base)。由下单编排(网关)按币种算好随单传入,
     * 供撮合在撤单/完成时对残余冻结发解冻。可空(未接编排时降级)。
     */
    private String lockedAsset;

    /**
     * 下单冻结的总额(lockedAsset 真实值,BigDecimal)。撮合按 lockedAsset scale 转定点 long。
     */
    private BigDecimal lockedAmount;
}

