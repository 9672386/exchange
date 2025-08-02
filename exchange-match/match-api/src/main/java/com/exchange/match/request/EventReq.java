package com.exchange.match.request;

import lombok.Data;

import java.io.Serializable;

@Data
public class EventReq<T> implements Serializable {
    private EventReq eventType;
    private T event;
    private String reqId;
    private long timestamp;
}
