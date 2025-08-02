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
}
