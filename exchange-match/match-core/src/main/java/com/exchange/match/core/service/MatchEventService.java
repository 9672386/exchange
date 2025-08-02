package com.exchange.match.core.service;

import com.exchange.match.request.*;

/**
 * 撮合事件服务接口
 */
public interface MatchEventService {
    
    /**
     * 提交新订单
     */
    String submitNewOrder(EventNewOrderReq newOrderReq);
    
    /**
     * 撤销订单
     */
    String cancelOrder(EventCanalReq canalReq);
    
    /**
     * 清理指定symbol的所有订单
     */
    String clearOrders(EventClearReq clearReq);
    
    /**
     * 生成快照
     */
    String generateSnapshot(EventSnapshotReq snapshotReq);
    
    /**
     * 停止撮合引擎
     */
    String stopEngine(EventStopReq stopReq);
    
    /**
     * 查询订单
     */
    String queryOrder(EventQueryOrderReq queryOrderReq);
    
    /**
     * 查询持仓
     */
    String queryPosition(EventQueryPositionReq queryPositionReq);
} 