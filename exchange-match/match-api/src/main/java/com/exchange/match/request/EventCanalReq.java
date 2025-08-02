package com.exchange.match.request;

import lombok.Data;

import java.io.Serializable;

@Data
public class EventCanalReq implements Serializable {
    private String orderId;
    private String symbol;
    private long userId;
}
