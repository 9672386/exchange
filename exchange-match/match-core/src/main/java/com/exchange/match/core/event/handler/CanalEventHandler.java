package com.exchange.match.core.event.handler;

import com.exchange.match.core.event.EventHandler;
import com.exchange.match.core.event.MatchEvent;
import com.exchange.match.core.memory.MemoryManager;
import com.exchange.match.core.model.*;
import com.exchange.match.core.service.BatchKafkaService;
import com.exchange.match.core.service.CommandIdGenerator;
import com.exchange.match.core.model.StateChangeEvent;
import com.exchange.match.enums.EventType;
import com.exchange.match.request.EventCanalReq;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

/**
 * 撤单事件处理器
 * 整合撤单逻辑到Disruptor事件中
 */
@Slf4j
@Component
public class CanalEventHandler implements EventHandler {
    
    @Autowired
    private MemoryManager memoryManager;
    
    @Autowired
    private BatchKafkaService batchKafkaService;
    
    @Override
    public void handle(MatchEvent event) {
        try {
            EventCanalReq canalReq = event.getCanalReq();
            log.info("处理撤单事件: orderId={}, userId={}, symbol={}", 
                    canalReq.getOrderId(), canalReq.getUserId(), canalReq.getSymbol());
            
            // 执行撤单逻辑
            MatchResponse response = processCancelOrder(canalReq);
            
            // 只有成功撤单才推送状态变动事件
            if (response.getStatus() == MatchStatus.SUCCESS) {
                // 生成命令ID
                long commandId = CommandIdGenerator.nextId();
                
                // 创建状态变动事件
                StateChangeEvent stateChangeEvent = StateChangeEvent.createSuccess(
                    commandId, 
                    EventType.CANAL, 
                    canalReq, 
                    response
                );
                
                // 推送状态变动事件到Kafka
                batchKafkaService.pushStateChangeEvent(stateChangeEvent);
                
                log.info("推送撤单状态变动事件: commandId={}, orderId={}", 
                        commandId, canalReq.getOrderId());
            } else {
                log.info("撤单失败，不推送状态变动事件: orderId={}, status={}, reason={}", 
                        canalReq.getOrderId(), response.getStatus(), response.getErrorMessage());
            }
            
            // 设置处理结果
            event.setResult(response);
            
            log.info("撤单处理完成: orderId={}, status={}, cancelQuantity={}", 
                    canalReq.getOrderId(), response.getStatus(), 
                    response.getCancelInfo() != null ? response.getCancelInfo().getCancelQuantity() : BigDecimal.ZERO);
            
        } catch (Exception e) {
            log.error("处理撤单事件失败", e);
            event.setException(e);
        }
    }
    
    /**
     * 处理撤单逻辑
     */
    private MatchResponse processCancelOrder(EventCanalReq canalReq) {
        MatchResponse response = new MatchResponse();
        response.setOrderId(canalReq.getOrderId());
        response.setUserId(canalReq.getUserId());
        response.setSymbol(canalReq.getSymbol());
        
        try {
            // 查找订单薄
            OrderBook orderBook = findOrderBookByOrderId(canalReq.getOrderId());
            if (orderBook == null) {
                response.setStatus(MatchStatus.REJECTED);
                response.setErrorMessage("订单不存在: " + canalReq.getOrderId());
                response.setRejectInfo(createRejectInfo(
                    MatchResponse.RejectInfo.RejectType.ORDER_NOT_FOUND,
                    "订单不存在: " + canalReq.getOrderId()
                ));
                return response;
            }
            
            // 查找订单
            Order order = orderBook.getOrder(canalReq.getOrderId());
            if (order == null) {
                response.setStatus(MatchStatus.REJECTED);
                response.setErrorMessage("订单不存在: " + canalReq.getOrderId());
                response.setRejectInfo(createRejectInfo(
                    MatchResponse.RejectInfo.RejectType.ORDER_NOT_FOUND,
                    "订单不存在: " + canalReq.getOrderId()
                ));
                return response;
            }
            
            // 验证用户权限
            if (!order.getUserId().equals(canalReq.getUserId())) {
                response.setStatus(MatchStatus.REJECTED);
                response.setErrorMessage("无权限撤单: " + canalReq.getOrderId());
                response.setRejectInfo(createRejectInfo(
                    MatchResponse.RejectInfo.RejectType.INSUFFICIENT_PERMISSION,
                    "无权限撤单: " + canalReq.getOrderId()
                ));
                return response;
            }
            
            // 检查订单状态
            if (order.getStatus() != OrderStatus.ACTIVE) {
                response.setStatus(MatchStatus.REJECTED);
                response.setErrorMessage("订单状态不允许撤单: " + order.getStatus());
                response.setRejectInfo(createRejectInfo(
                    MatchResponse.RejectInfo.RejectType.INVALID_ORDER_STATUS,
                    "订单状态不允许撤单: " + order.getStatus()
                ));
                return response;
            }
            
            // 记录撤单前状态
            MatchStatus previousStatus = convertOrderStatusToMatchStatus(order.getStatus());
            BigDecimal cancelQuantity = order.getRemainingQuantity();
            
            // 执行撤单
            order.cancel();
            orderBook.removeOrder(canalReq.getOrderId());
            
            // 更新响应信息
            response.setStatus(MatchStatus.CANCELLED);
            response.setMatchQuantity(order.getFilledQuantity());
            response.setRemainingQuantity(BigDecimal.ZERO);
            
            // 设置撤单信息
            MatchResponse.CancelInfo cancelInfo = new MatchResponse.CancelInfo();
            cancelInfo.setCancelUserId(canalReq.getUserId());
            cancelInfo.setCancelReason("用户主动撤单");
            cancelInfo.setCancelQuantity(cancelQuantity);
            cancelInfo.setPreviousStatus(previousStatus);
            response.setCancelInfo(cancelInfo);
            
        } catch (Exception e) {
            log.error("处理撤单失败: orderId={}", canalReq.getOrderId(), e);
            response.setStatus(MatchStatus.REJECTED);
            response.setErrorMessage("系统错误: " + e.getMessage());
            response.setRejectInfo(createRejectInfo(
                MatchResponse.RejectInfo.RejectType.SYSTEM_ERROR,
                "系统错误: " + e.getMessage()
            ));
        }
        
        return response;
    }
    
    /**
     * 根据订单ID查找订单薄
     */
    private OrderBook findOrderBookByOrderId(String orderId) {
        for (OrderBook orderBook : memoryManager.getAllOrderBooks().values()) {
            if (orderBook.getOrder(orderId) != null) {
                return orderBook;
            }
        }
        return null;
    }
    
    /**
     * 创建拒绝信息
     */
    private MatchResponse.RejectInfo createRejectInfo(MatchResponse.RejectInfo.RejectType rejectType, String reason) {
        MatchResponse.RejectInfo rejectInfo = new MatchResponse.RejectInfo();
        rejectInfo.setRejectType(rejectType);
        rejectInfo.setRejectCode(rejectType.name());
        rejectInfo.setRejectReason(reason);
        return rejectInfo;
    }
    
    /**
     * 转换订单状态为撮合状态
     */
    private MatchStatus convertOrderStatusToMatchStatus(OrderStatus orderStatus) {
        switch (orderStatus) {
            case ACTIVE:
                return MatchStatus.PENDING;
            case FILLED:
                return MatchStatus.SUCCESS;
            case CANCELLED:
                return MatchStatus.CANCELLED;
            case PARTIALLY_FILLED:
                return MatchStatus.PARTIALLY_FILLED;
            default:
                return MatchStatus.REJECTED;
        }
    }
    
    @Override
    public EventType getSupportedEventType() {
        return EventType.CANAL;
    }
} 