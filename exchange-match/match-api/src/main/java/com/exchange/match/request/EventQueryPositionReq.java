package com.exchange.match.request;

import lombok.Data;

import java.io.Serializable;

@Data
public class EventQueryPositionReq implements Serializable {
    private String symbol;
    private long userId;
    private int orderType;
}
