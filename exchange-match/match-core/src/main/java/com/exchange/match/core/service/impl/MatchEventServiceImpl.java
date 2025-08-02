package com.exchange.match.core.service.impl;

import com.exchange.match.core.event.service.EventPublishService;
import com.exchange.match.core.service.MatchEventService;
import com.exchange.match.request.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * 撮合事件服务实现类
 */
@Slf4j
@Service
public class MatchEventServiceImpl implements MatchEventService {
    
    @Autowired
    private EventPublishService eventPublishService;
    
    @Override
    public String submitNewOrder(EventNewOrderReq newOrderReq) {
        try {
            log.info("提交新订单: orderId={}, userId={}", 
                    newOrderReq.getOrderId(), newOrderReq.getUserId());
            
            eventPublishService.publishNewOrderEvent(newOrderReq);
            
            return "新订单提交成功: " + newOrderReq.getOrderId();
        } catch (Exception e) {
            log.error("提交新订单失败", e);
            throw new RuntimeException("提交新订单失败", e);
        }
    }
    
    @Override
    public String cancelOrder(EventCanalReq canalReq) {
        try {
            log.info("撤销订单: orderId={}, userId={}", 
                    canalReq.getOrderId(), canalReq.getUserId());
            
            eventPublishService.publishCanalEvent(canalReq);
            
            return "订单撤销成功: " + canalReq.getOrderId();
        } catch (Exception e) {
            log.error("撤销订单失败", e);
            throw new RuntimeException("撤销订单失败", e);
        }
    }
    
    @Override
    public String clearOrders(EventClearReq clearReq) {
        try {
            log.info("清理订单: symbol={}", clearReq.getSymbol());
            
            eventPublishService.publishClearEvent(clearReq);
            
            return "订单清理成功: " + clearReq.getSymbol();
        } catch (Exception e) {
            log.error("清理订单失败", e);
            throw new RuntimeException("清理订单失败", e);
        }
    }
    
    @Override
    public String generateSnapshot(EventSnapshotReq snapshotReq) {
        try {
            log.info("生成快照");
            
            eventPublishService.publishSnapshotEvent(snapshotReq);
            
            return "快照生成成功";
        } catch (Exception e) {
            log.error("生成快照失败", e);
            throw new RuntimeException("生成快照失败", e);
        }
    }
    
    @Override
    public String stopEngine(EventStopReq stopReq) {
        try {
            log.info("停止撮合引擎");
            
            eventPublishService.publishStopEvent(stopReq);
            
            return "撮合引擎停止成功";
        } catch (Exception e) {
            log.error("停止撮合引擎失败", e);
            throw new RuntimeException("停止撮合引擎失败", e);
        }
    }
    
    @Override
    public String queryOrder(EventQueryOrderReq queryOrderReq) {
        try {
            log.info("查询订单: symbol={}, userId={}, orderType={}", 
                    queryOrderReq.getSymbol(), queryOrderReq.getUserId(), queryOrderReq.getOrderType());
            
            eventPublishService.publishQueryOrderEvent(queryOrderReq);
            
            return "订单查询成功: " + queryOrderReq.getSymbol();
        } catch (Exception e) {
            log.error("查询订单失败", e);
            throw new RuntimeException("查询订单失败", e);
        }
    }
    
    @Override
    public String queryPosition(EventQueryPositionReq queryPositionReq) {
        try {
            log.info("查询持仓: symbol={}, userId={}", 
                    queryPositionReq.getSymbol(), queryPositionReq.getUserId());
            
            eventPublishService.publishQueryPositionEvent(queryPositionReq);
            
            return "持仓查询成功: " + queryPositionReq.getSymbol();
        } catch (Exception e) {
            log.error("查询持仓失败", e);
            throw new RuntimeException("查询持仓失败", e);
        }
    }
} 