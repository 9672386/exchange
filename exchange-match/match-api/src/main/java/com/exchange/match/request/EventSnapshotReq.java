package com.exchange.match.request;

import lombok.Data;

import java.io.Serializable;

@Data
public class EventSnapshotReq implements Serializable {
    
    /**
     * 交易对（可选，为null时生成所有交易对的快照）
     */
    private String symbol;
}
