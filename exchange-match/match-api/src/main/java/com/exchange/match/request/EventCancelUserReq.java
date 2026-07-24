package com.exchange.match.request;

import lombok.Data;

import java.io.Serializable;

/**
 * 用户订单全撤请求(进 Raft 的状态机写操作)。
 * 确定性:撮合按 orderId 升序逐笔撤 + 解冻。{@code cancelId} 作幂等/审计键。
 */
@Data
public class EventCancelUserReq implements Serializable {
    private String cancelId;
    private long userId;
}
