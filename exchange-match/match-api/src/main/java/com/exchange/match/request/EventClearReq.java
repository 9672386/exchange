package com.exchange.match.request;

import lombok.Data;

import java.io.Serializable;

@Data
public class EventClearReq implements Serializable {
    private String symbol;
}
