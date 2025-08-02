package com.exchange.match.core.service;

import com.exchange.match.core.model.Order;
import com.exchange.match.core.model.Position;
import com.exchange.match.core.memory.MemoryManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 杠杆调整服务测试
 */
@ExtendWith(MockitoExtension.class)
public class LeverageAdjustmentServiceTest {
    
    @Mock
    private MemoryManager memoryManager;
    
    @Mock
    private CrossPositionManager crossPositionManager;
    
    @InjectMocks
    private LeverageAdjustmentService leverageAdjustmentService;
    
    private Long testUserId;
    private BigDecimal testNewLeverage;
    
    @BeforeEach
    void setUp() {
        testUserId = 1001L;
        testNewLeverage = new BigDecimal("20");
    }
    
    @Test
    void testAdjustUserLeverage_Success() {
        // 模拟获取用户持仓和委托为空
        // 这里需要根据实际的getAllUserPositions和getActiveUserOrders方法实现来模拟
        
        // 执行调整
        LeverageAdjustmentService.LeverageAdjustmentResult result = leverageAdjustmentService.adjustUserLeverage(testUserId, testNewLeverage);
        
        // 验证结果
        assertTrue(result.isSuccess());
        assertEquals(testUserId, result.getUserId());
        assertEquals(testNewLeverage, result.getNewLeverage());
        assertEquals(0, result.getPositionsCount());
        assertEquals(0, result.getOrdersCount());
    }
    
    @Test
    void testAdjustUserLeverage_LeverageTooLow() {
        // 设置杠杆为0.5（小于最小值1）
        BigDecimal lowLeverage = new BigDecimal("0.5");
        
        // 执行调整
        LeverageAdjustmentService.LeverageAdjustmentResult result = leverageAdjustmentService.adjustUserLeverage(testUserId, lowLeverage);
        
        // 验证结果
        assertFalse(result.isSuccess());
        assertEquals("LEVERAGE_TOO_LOW", result.getErrorCode());
        assertTrue(result.getErrorMessage().contains("杠杆倍数不能小于1"));
    }
    
    @Test
    void testAdjustUserLeverage_LeverageExceeded() {
        // 设置杠杆为150（超过最大允许值100）
        BigDecimal highLeverage = new BigDecimal("150");
        
        // 执行调整
        LeverageAdjustmentService.LeverageAdjustmentResult result = leverageAdjustmentService.adjustUserLeverage(testUserId, highLeverage);
        
        // 验证结果
        assertFalse(result.isSuccess());
        assertEquals("LEVERAGE_EXCEEDED", result.getErrorCode());
        assertTrue(result.getErrorMessage().contains("杠杆倍数超过最大允许值"));
    }
    
    @Test
    void testAdjustUserLeverage_LeverageDecreaseNotAllowed() {
        // 创建现有持仓，杠杆为20
        Position existingPosition = new Position();
        existingPosition.setUserId(testUserId);
        existingPosition.setSymbol("BTCUSDT");
        existingPosition.setLeverage(new BigDecimal("20"));
        
        // 尝试降低杠杆到10
        BigDecimal lowerLeverage = new BigDecimal("10");
        
        // 执行调整
        LeverageAdjustmentService.LeverageAdjustmentResult result = leverageAdjustmentService.adjustUserLeverage(testUserId, lowerLeverage);
        
        // 验证结果
        assertFalse(result.isSuccess());
        assertEquals("LEVERAGE_DECREASE_NOT_ALLOWED", result.getErrorCode());
        assertTrue(result.getErrorMessage().contains("有仓位或委托时不允许降低杠杆"));
    }
    
    @Test
    void testAdjustUserLeverage_LeverageIncreaseAllowed() {
        // 创建现有持仓，杠杆为10
        Position existingPosition = new Position();
        existingPosition.setUserId(testUserId);
        existingPosition.setSymbol("BTCUSDT");
        existingPosition.setLeverage(new BigDecimal("10"));
        
        // 尝试增加杠杆到30
        BigDecimal higherLeverage = new BigDecimal("30");
        
        // 执行调整
        LeverageAdjustmentService.LeverageAdjustmentResult result = leverageAdjustmentService.adjustUserLeverage(testUserId, higherLeverage);
        
        // 验证结果
        assertTrue(result.isSuccess());
        assertEquals(testUserId, result.getUserId());
        assertEquals(higherLeverage, result.getNewLeverage());
    }
    
    @Test
    void testAdjustUserLeverage_WithPositionsAndOrders() {
        // 创建现有持仓
        Position position1 = new Position();
        position1.setUserId(testUserId);
        position1.setSymbol("BTCUSDT");
        position1.setLeverage(new BigDecimal("10"));
        position1.setQuantity(new BigDecimal("1.0"));
        position1.setAveragePrice(new BigDecimal("50000"));
        
        Position position2 = new Position();
        position2.setUserId(testUserId);
        position2.setSymbol("ETHUSDT");
        position2.setLeverage(new BigDecimal("15"));
        position2.setQuantity(new BigDecimal("10.0"));
        position2.setAveragePrice(new BigDecimal("3000"));
        
        // 创建活跃委托
        Order order1 = new Order();
        order1.setOrderId("ORDER_001");
        order1.setUserId(testUserId);
        order1.setSymbol("BTCUSDT");
        order1.setLeverage(new BigDecimal("10"));
        
        Order order2 = new Order();
        order2.setOrderId("ORDER_002");
        order2.setUserId(testUserId);
        order2.setSymbol("ETHUSDT");
        order2.setLeverage(new BigDecimal("15"));
        
        // 尝试调整杠杆到25（大于现有最大杠杆15）
        BigDecimal newLeverage = new BigDecimal("25");
        
        // 执行调整
        LeverageAdjustmentService.LeverageAdjustmentResult result = leverageAdjustmentService.adjustUserLeverage(testUserId, newLeverage);
        
        // 验证结果
        assertTrue(result.isSuccess());
        assertEquals(testUserId, result.getUserId());
        assertEquals(newLeverage, result.getNewLeverage());
    }
    
    @Test
    void testAdjustUserLeverage_WithMultipleLeverages() {
        // 创建不同杠杆的持仓
        Position position1 = new Position();
        position1.setUserId(testUserId);
        position1.setSymbol("BTCUSDT");
        position1.setLeverage(new BigDecimal("10"));
        
        Position position2 = new Position();
        position2.setUserId(testUserId);
        position2.setSymbol("ETHUSDT");
        position2.setLeverage(new BigDecimal("20"));
        
        // 尝试调整杠杆到25（大于现有最大杠杆20）
        BigDecimal newLeverage = new BigDecimal("25");
        
        // 执行调整
        LeverageAdjustmentService.LeverageAdjustmentResult result = leverageAdjustmentService.adjustUserLeverage(testUserId, newLeverage);
        
        // 验证结果
        assertTrue(result.isSuccess());
        assertEquals(testUserId, result.getUserId());
        assertEquals(newLeverage, result.getNewLeverage());
    }
    
    @Test
    void testAdjustUserLeverage_NoPositionsOrOrders() {
        // 没有持仓和委托时，可以自由调整杠杆
        
        // 尝试调整杠杆到5
        BigDecimal newLeverage = new BigDecimal("5");
        
        // 执行调整
        LeverageAdjustmentService.LeverageAdjustmentResult result = leverageAdjustmentService.adjustUserLeverage(testUserId, newLeverage);
        
        // 验证结果
        assertTrue(result.isSuccess());
        assertEquals(testUserId, result.getUserId());
        assertEquals(newLeverage, result.getNewLeverage());
    }
    
    @Test
    void testAdjustUserLeverage_ExceptionHandling() {
        // 模拟异常情况
        when(memoryManager.getSymbol(anyString())).thenThrow(new RuntimeException("Database error"));
        
        // 执行调整
        LeverageAdjustmentService.LeverageAdjustmentResult result = leverageAdjustmentService.adjustUserLeverage(testUserId, testNewLeverage);
        
        // 验证结果
        assertFalse(result.isSuccess());
        assertEquals("LEVERAGE_ADJUSTMENT_ERROR", result.getErrorCode());
        assertTrue(result.getErrorMessage().contains("调整杠杆失败"));
    }
} 