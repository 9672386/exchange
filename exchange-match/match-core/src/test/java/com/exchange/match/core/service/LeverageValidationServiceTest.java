package com.exchange.match.core.service;

import com.exchange.match.core.model.Order;
import com.exchange.match.core.model.Position;
import com.exchange.match.core.model.SymbolRiskLimitConfig;
import com.exchange.match.core.model.RiskLevel;
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
 * 杠杆验证服务测试
 */
@ExtendWith(MockitoExtension.class)
public class LeverageValidationServiceTest {
    
    @Mock
    private MemoryManager memoryManager;
    
    @Mock
    private SymbolRiskLimitConfigManager symbolRiskLimitConfigManager;
    
    @Mock
    private CrossPositionManager crossPositionManager;
    
    @InjectMocks
    private LeverageValidationService leverageValidationService;
    
    private Order testOrder;
    private SymbolRiskLimitConfig testRiskConfig;
    
    @BeforeEach
    void setUp() {
        // 创建测试订单
        testOrder = new Order();
        testOrder.setOrderId("TEST_ORDER_001");
        testOrder.setUserId(1001L);
        testOrder.setSymbol("BTCUSDT");
        testOrder.setLeverage(new BigDecimal("10"));
        testOrder.setQuantity(new BigDecimal("1.0"));
        testOrder.setPrice(new BigDecimal("50000"));
        
        // 创建测试风险限额配置
        testRiskConfig = new SymbolRiskLimitConfig();
        testRiskConfig.setSymbol("BTCUSDT");
        
        // 设置逐仓模式最大杠杆为100
        testRiskConfig.getIsolatedModeConfig().setMaxLeverage(new BigDecimal("100"));
        
        // 设置全仓模式最大杠杆为125
        testRiskConfig.getCrossModeConfig().setMaxLeverage(new BigDecimal("125"));
    }
    
    @Test
    void testValidateOrderLeverage_Success() {
        // 模拟风险限额配置管理器返回测试配置
        when(symbolRiskLimitConfigManager.getRiskLimitConfig("BTCUSDT")).thenReturn(testRiskConfig);
        
        // 模拟获取现有持仓为空
        // 这里需要根据实际的getExistingPositions方法实现来模拟
        
        // 执行验证
        LeverageValidationService.LeverageValidationResult result = leverageValidationService.validateOrderLeverage(testOrder);
        
        // 验证结果
        assertTrue(result.isValid());
        assertEquals("TEST_ORDER_001", result.getOrderId());
        assertEquals(1001L, result.getUserId());
        assertEquals("BTCUSDT", result.getSymbol());
        assertEquals(new BigDecimal("10"), result.getLeverage());
    }
    
    @Test
    void testValidateOrderLeverage_LeverageTooLow() {
        // 设置杠杆为0.5（小于最小值1）
        testOrder.setLeverage(new BigDecimal("0.5"));
        
        // 模拟风险限额配置管理器返回测试配置
        when(symbolRiskLimitConfigManager.getRiskLimitConfig("BTCUSDT")).thenReturn(testRiskConfig);
        
        // 执行验证
        LeverageValidationService.LeverageValidationResult result = leverageValidationService.validateOrderLeverage(testOrder);
        
        // 验证结果
        assertFalse(result.isValid());
        assertEquals("LEVERAGE_TOO_LOW", result.getErrorCode());
        assertTrue(result.getErrorMessage().contains("杠杆倍数不能小于1"));
    }
    
    @Test
    void testValidateOrderLeverage_LeverageExceeded() {
        // 设置杠杆为150（超过最大允许值100）
        testOrder.setLeverage(new BigDecimal("150"));
        
        // 模拟风险限额配置管理器返回测试配置
        when(symbolRiskLimitConfigManager.getRiskLimitConfig("BTCUSDT")).thenReturn(testRiskConfig);
        
        // 执行验证
        LeverageValidationService.LeverageValidationResult result = leverageValidationService.validateOrderLeverage(testOrder);
        
        // 验证结果
        assertFalse(result.isValid());
        assertEquals("LEVERAGE_EXCEEDED", result.getErrorCode());
        assertTrue(result.getErrorMessage().contains("杠杆倍数超过最大允许值"));
    }
    
    @Test
    void testValidateOrderLeverage_GlobalLeverageMismatch() {
        // 创建现有持仓，杠杆为20
        Position existingPosition = new Position();
        existingPosition.setUserId(1001L);
        existingPosition.setSymbol("ETHUSDT"); // 不同标的
        existingPosition.setLeverage(new BigDecimal("20"));
        
        // 订单杠杆为10，与现有持仓杠杆不一致
        testOrder.setLeverage(new BigDecimal("10"));
        
        // 模拟风险限额配置管理器返回测试配置
        when(symbolRiskLimitConfigManager.getRiskLimitConfig("BTCUSDT")).thenReturn(testRiskConfig);
        
        // 模拟获取杠杆验证规则为统一杠杆
        when(symbolRiskLimitConfigManager.getLeverageValidationRule("BTCUSDT", any(RiskLevel.class)))
                .thenReturn(SymbolRiskLimitConfig.LeverageValidationRule.UNIFORM);
        
        // 执行验证
        LeverageValidationService.LeverageValidationResult result = leverageValidationService.validateOrderLeverage(testOrder);
        
        // 验证结果
        assertFalse(result.isValid());
        assertEquals("GLOBAL_LEVERAGE_MISMATCH", result.getErrorCode());
        assertTrue(result.getErrorMessage().contains("全局杠杆规则"));
    }
    
    @Test
    void testValidateOrderLeverage_LeverageDecreaseNotAllowed() {
        // 创建现有持仓，杠杆为20
        Position existingPosition = new Position();
        existingPosition.setUserId(1001L);
        existingPosition.setSymbol("BTCUSDT");
        existingPosition.setLeverage(new BigDecimal("20"));
        
        // 订单杠杆为10，小于现有持仓杠杆，在有仓位时不允许降低杠杆
        testOrder.setLeverage(new BigDecimal("10"));
        
        // 模拟风险限额配置管理器返回测试配置
        when(symbolRiskLimitConfigManager.getRiskLimitConfig("BTCUSDT")).thenReturn(testRiskConfig);
        
        // 模拟获取杠杆验证规则为灵活杠杆
        when(symbolRiskLimitConfigManager.getLeverageValidationRule("BTCUSDT", any(RiskLevel.class)))
                .thenReturn(SymbolRiskLimitConfig.LeverageValidationRule.FLEXIBLE);
        
        // 执行验证
        LeverageValidationService.LeverageValidationResult result = leverageValidationService.validateOrderLeverage(testOrder);
        
        // 验证结果
        assertFalse(result.isValid());
        assertEquals("LEVERAGE_DECREASE_NOT_ALLOWED", result.getErrorCode());
        assertTrue(result.getErrorMessage().contains("有仓位或委托时不允许降低杠杆"));
    }
    
    @Test
    void testValidateOrderLeverage_LeverageIncreaseAllowed() {
        // 创建现有持仓，杠杆为10
        Position existingPosition = new Position();
        existingPosition.setUserId(1001L);
        existingPosition.setSymbol("BTCUSDT");
        existingPosition.setLeverage(new BigDecimal("10"));
        
        // 订单杠杆为20，大于现有持仓杠杆，在有仓位时允许增加杠杆
        testOrder.setLeverage(new BigDecimal("20"));
        
        // 模拟风险限额配置管理器返回测试配置
        when(symbolRiskLimitConfigManager.getRiskLimitConfig("BTCUSDT")).thenReturn(testRiskConfig);
        
        // 模拟获取杠杆验证规则为灵活杠杆
        when(symbolRiskLimitConfigManager.getLeverageValidationRule("BTCUSDT", any(RiskLevel.class)))
                .thenReturn(SymbolRiskLimitConfig.LeverageValidationRule.FLEXIBLE);
        
        // 执行验证
        LeverageValidationService.LeverageValidationResult result = leverageValidationService.validateOrderLeverage(testOrder);
        
        // 验证结果
        assertTrue(result.isValid());
    }
    
    @Test
    void testValidateOrderLeverage_UniformLeverageMismatch() {
        // 创建现有持仓，杠杆为20
        Position existingPosition = new Position();
        existingPosition.setUserId(1001L);
        existingPosition.setSymbol("BTCUSDT");
        existingPosition.setLeverage(new BigDecimal("20"));
        
        // 订单杠杆为10，与现有持仓杠杆不一致
        testOrder.setLeverage(new BigDecimal("10"));
        
        // 模拟风险限额配置管理器返回测试配置
        when(symbolRiskLimitConfigManager.getRiskLimitConfig("BTCUSDT")).thenReturn(testRiskConfig);
        
        // 模拟获取杠杆验证规则为统一杠杆
        when(symbolRiskLimitConfigManager.getLeverageValidationRule("BTCUSDT", any(RiskLevel.class)))
                .thenReturn(SymbolRiskLimitConfig.LeverageValidationRule.UNIFORM);
        
        // 执行验证
        LeverageValidationService.LeverageValidationResult result = leverageValidationService.validateOrderLeverage(testOrder);
        
        // 验证结果
        assertFalse(result.isValid());
        assertEquals("LEVERAGE_MISMATCH", result.getErrorCode());
        assertTrue(result.getErrorMessage().contains("统一杠杆规则"));
    }
    
    @Test
    void testValidateOrderLeverage_FlexibleLeverageSuccess() {
        // 创建现有持仓，杠杆为20
        Position existingPosition = new Position();
        existingPosition.setUserId(1001L);
        existingPosition.setSymbol("BTCUSDT");
        existingPosition.setLeverage(new BigDecimal("20"));
        
        // 订单杠杆为10，与现有持仓杠杆不同，但灵活杠杆允许
        testOrder.setLeverage(new BigDecimal("10"));
        
        // 模拟风险限额配置管理器返回测试配置
        when(symbolRiskLimitConfigManager.getRiskLimitConfig("BTCUSDT")).thenReturn(testRiskConfig);
        
        // 模拟获取杠杆验证规则为灵活杠杆
        when(symbolRiskLimitConfigManager.getLeverageValidationRule("BTCUSDT", any(RiskLevel.class)))
                .thenReturn(SymbolRiskLimitConfig.LeverageValidationRule.FLEXIBLE);
        
        // 执行验证
        LeverageValidationService.LeverageValidationResult result = leverageValidationService.validateOrderLeverage(testOrder);
        
        // 验证结果
        assertTrue(result.isValid());
    }
    
    @Test
    void testValidateOrderLeverage_IncreasingLeverageSuccess() {
        // 创建现有持仓，杠杆为5
        Position existingPosition = new Position();
        existingPosition.setUserId(1001L);
        existingPosition.setSymbol("BTCUSDT");
        existingPosition.setLeverage(new BigDecimal("5"));
        
        // 订单杠杆为10，大于现有持仓杠杆，符合递增规则
        testOrder.setLeverage(new BigDecimal("10"));
        
        // 模拟风险限额配置管理器返回测试配置
        when(symbolRiskLimitConfigManager.getRiskLimitConfig("BTCUSDT")).thenReturn(testRiskConfig);
        
        // 模拟获取杠杆验证规则为递增杠杆
        when(symbolRiskLimitConfigManager.getLeverageValidationRule("BTCUSDT", any(RiskLevel.class)))
                .thenReturn(SymbolRiskLimitConfig.LeverageValidationRule.INCREASING);
        
        // 执行验证
        LeverageValidationService.LeverageValidationResult result = leverageValidationService.validateOrderLeverage(testOrder);
        
        // 验证结果
        assertTrue(result.isValid());
    }
    
    @Test
    void testValidateOrderLeverage_IncreasingLeverageFailure() {
        // 创建现有持仓，杠杆为15
        Position existingPosition = new Position();
        existingPosition.setUserId(1001L);
        existingPosition.setSymbol("BTCUSDT");
        existingPosition.setLeverage(new BigDecimal("15"));
        
        // 订单杠杆为10，小于现有持仓杠杆，不符合递增规则
        testOrder.setLeverage(new BigDecimal("10"));
        
        // 模拟风险限额配置管理器返回测试配置
        when(symbolRiskLimitConfigManager.getRiskLimitConfig("BTCUSDT")).thenReturn(testRiskConfig);
        
        // 模拟获取杠杆验证规则为递增杠杆
        when(symbolRiskLimitConfigManager.getLeverageValidationRule("BTCUSDT", any(RiskLevel.class)))
                .thenReturn(SymbolRiskLimitConfig.LeverageValidationRule.INCREASING);
        
        // 执行验证
        LeverageValidationService.LeverageValidationResult result = leverageValidationService.validateOrderLeverage(testOrder);
        
        // 验证结果
        assertFalse(result.isValid());
        assertEquals("LEVERAGE_NOT_INCREASING", result.getErrorCode());
        assertTrue(result.getErrorMessage().contains("递增杠杆规则"));
    }
    
    @Test
    void testValidateOrderLeverage_DecreasingLeverageSuccess() {
        // 创建现有持仓，杠杆为20
        Position existingPosition = new Position();
        existingPosition.setUserId(1001L);
        existingPosition.setSymbol("BTCUSDT");
        existingPosition.setLeverage(new BigDecimal("20"));
        
        // 订单杠杆为10，小于现有持仓杠杆，符合递减规则
        testOrder.setLeverage(new BigDecimal("10"));
        
        // 模拟风险限额配置管理器返回测试配置
        when(symbolRiskLimitConfigManager.getRiskLimitConfig("BTCUSDT")).thenReturn(testRiskConfig);
        
        // 模拟获取杠杆验证规则为递减杠杆
        when(symbolRiskLimitConfigManager.getLeverageValidationRule("BTCUSDT", any(RiskLevel.class)))
                .thenReturn(SymbolRiskLimitConfig.LeverageValidationRule.DECREASING);
        
        // 执行验证
        LeverageValidationService.LeverageValidationResult result = leverageValidationService.validateOrderLeverage(testOrder);
        
        // 验证结果
        assertTrue(result.isValid());
    }
    
    @Test
    void testValidateOrderLeverage_DecreasingLeverageFailure() {
        // 创建现有持仓，杠杆为5
        Position existingPosition = new Position();
        existingPosition.setUserId(1001L);
        existingPosition.setSymbol("BTCUSDT");
        existingPosition.setLeverage(new BigDecimal("5"));
        
        // 订单杠杆为10，大于现有持仓杠杆，不符合递减规则
        testOrder.setLeverage(new BigDecimal("10"));
        
        // 模拟风险限额配置管理器返回测试配置
        when(symbolRiskLimitConfigManager.getRiskLimitConfig("BTCUSDT")).thenReturn(testRiskConfig);
        
        // 模拟获取杠杆验证规则为递减杠杆
        when(symbolRiskLimitConfigManager.getLeverageValidationRule("BTCUSDT", any(RiskLevel.class)))
                .thenReturn(SymbolRiskLimitConfig.LeverageValidationRule.DECREASING);
        
        // 执行验证
        LeverageValidationService.LeverageValidationResult result = leverageValidationService.validateOrderLeverage(testOrder);
        
        // 验证结果
        assertFalse(result.isValid());
        assertEquals("LEVERAGE_NOT_DECREASING", result.getErrorCode());
        assertTrue(result.getErrorMessage().contains("递减杠杆规则"));
    }
} 