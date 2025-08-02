package com.exchange.match.request;

import lombok.Data;

import java.io.Serializable;

@Data
public class EventNewOrderReq implements Serializable {
    private String orderId;
    private long userId;
}
