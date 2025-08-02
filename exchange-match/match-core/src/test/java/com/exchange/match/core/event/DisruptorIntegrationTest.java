package com.exchange.match.core.event;

import com.exchange.match.core.event.service.EventPublishService;
import com.exchange.match.enums.EventType;
import com.exchange.match.request.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Disruptor整合测试
 */
@SpringBootTest
@ActiveProfiles("test")
public class DisruptorIntegrationTest {
    
    @Autowired
    private EventPublishService eventPublishService;
    
    @Test
    public void testNewOrderEvent() {
        // 创建新订单请求
        EventNewOrderReq newOrderReq = new EventNewOrderReq();
        newOrderReq.setOrderId("TEST_ORDER_001");
        newOrderReq.setUserId(12345L);
        
        // 发布事件
        assertDoesNotThrow(() -> {
            eventPublishService.publishNewOrderEvent(newOrderReq);
        });
        
        // 等待事件处理完成
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
    
    @Test
    public void testCanalEvent() {
        // 创建撤单请求
        EventCanalReq canalReq = new EventCanalReq();
        canalReq.setOrderId("TEST_ORDER_002");
        canalReq.setUserId(12345L);
        
        // 发布事件
        assertDoesNotThrow(() -> {
            eventPublishService.publishCanalEvent(canalReq);
        });
        
        // 等待事件处理完成
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
    
    @Test
    public void testClearEvent() {
        // 创建清理请求
        EventClearReq clearReq = new EventClearReq();
        clearReq.setSymbol("BTC/USDT");
        
        // 发布事件
        assertDoesNotThrow(() -> {
            eventPublishService.publishClearEvent(clearReq);
        });
        
        // 等待事件处理完成
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
    
    @Test
    public void testSnapshotEvent() {
        // 创建快照请求
        EventSnapshotReq snapshotReq = new EventSnapshotReq();
        
        // 发布事件
        assertDoesNotThrow(() -> {
            eventPublishService.publishSnapshotEvent(snapshotReq);
        });
        
        // 等待事件处理完成
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
    
    @Test
    public void testStopEvent() {
        // 创建停止请求
        EventStopReq stopReq = new EventStopReq();
        
        // 发布事件
        assertDoesNotThrow(() -> {
            eventPublishService.publishStopEvent(stopReq);
        });
        
        // 等待事件处理完成
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
    
    @Test
    public void testQueryOrderEvent() {
        // 创建查询订单请求
        EventQueryOrderReq queryOrderReq = new EventQueryOrderReq();
        queryOrderReq.setSymbol("BTC/USDT");
        queryOrderReq.setUserId(12345L);
        queryOrderReq.setOrderType(1);
        
        // 发布事件
        assertDoesNotThrow(() -> {
            eventPublishService.publishQueryOrderEvent(queryOrderReq);
        });
        
        // 等待事件处理完成
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
    
    @Test
    public void testQueryPositionEvent() {
        // 创建查询持仓请求
        EventQueryPositionReq queryPositionReq = new EventQueryPositionReq();
        queryPositionReq.setSymbol("BTC/USDT");
        queryPositionReq.setUserId(12345L);
        
        // 发布事件
        assertDoesNotThrow(() -> {
            eventPublishService.publishQueryPositionEvent(queryPositionReq);
        });
        
        // 等待事件处理完成
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
} 