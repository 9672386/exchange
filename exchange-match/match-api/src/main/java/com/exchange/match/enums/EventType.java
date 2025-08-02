package com.exchange.match.enums;

import lombok.AllArgsConstructor;

@AllArgsConstructor
public enum EventType {
    STOP("停止",true),
    NEW_ORDER("委托",true),
    CANAL("撤单",true),
    CANAL_ALL("撤所有订单",true),
    SNAPSHOT("快照",true),
    CLEAR("清理所有",true),
    CLEAR_SYMBOL("清理指定symbol",true),
    QUERY_ORDER("查询订单",false),
    QUERY_POSITION("停止",false),
    LIQUIDATION("强平",true),
    ;

    private String type;

    private Boolean write;
}
