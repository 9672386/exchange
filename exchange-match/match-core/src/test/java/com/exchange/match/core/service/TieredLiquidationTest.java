package com.exchange.match.core.service;

import com.exchange.match.core.model.*;
import com.exchange.match.core.memory.MemoryManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 分档平仓功能测试
 */
@ExtendWith(MockitoExtension.class)
public class TieredLiquidationTest {
    
    @Mock
    private MemoryManager memoryManager;
    
    @Mock
    private RiskRecalculationService riskRecalculationService;
    
    @InjectMocks
    private RiskManagementService riskManagementService;
    
    private LiquidationRequest liquidationRequest;
    private Position testPosition;
    
    @BeforeEach
    void setUp() {
        // 创建测试强平请求
        liquidationRequest = new LiquidationRequest();
        liquidationRequest.setLiquidationId("TEST_LIQ_001");
        liquidationRequest.setUserId(1001L);
        liquidationRequest.setSymbol("BTCUSDT");
        liquidationRequest.setRiskLevel(RiskLevel.LIQUIDATION);
        liquidationRequest.setIndexPrice(BigDecimal.valueOf(50000));
        liquidationRequest.setBalance(BigDecimal.valueOf(10000));
        liquidationRequest.setMargin(BigDecimal.valueOf(10000));
        liquidationRequest.setRiskRatio(BigDecimal.valueOf(0.99)); // 99%风险率
        liquidationRequest.setUnrealizedPnl(BigDecimal.valueOf(-8000));
        liquidationRequest.setRealizedPnl(BigDecimal.valueOf(1000));
        
        // 创建测试持仓
        testPosition = new Position();
        testPosition.setUserId(1001L);
        testPosition.setSymbol("BTCUSDT");
        testPosition.setSide(PositionSide.LONG);
        testPosition.setQuantity(BigDecimal.valueOf(1.0)); // 1 BTC
        testPosition.setAveragePrice(BigDecimal.valueOf(58000));
        testPosition.setUnrealizedPnl(BigDecimal.valueOf(-8000));
        testPosition.setCreateTime(LocalDateTime.now());
    }
    
    @Test
    void testTieredLiquidationFromLiquidationLevel() {
        // 模拟内存管理器返回持仓
        when(memoryManager.getPosition(1001L, "BTCUSDT")).thenReturn(testPosition);
        
        // 模拟风险重计算服务
        RiskRecalculationService.RiskRecalculationResult riskResult1 = createRiskResult(RiskLevel.EMERGENCY, true);
        RiskRecalculationService.RiskRecalculationResult riskResult2 = createRiskResult(RiskLevel.DANGER, true);
        RiskRecalculationService.RiskRecalculationResult riskResult3 = createRiskResult(RiskLevel.WARNING, false);
        
        when(riskRecalculationService.recalculateRisk(any(LiquidationRequest.class)))
                .thenReturn(riskResult1, riskResult2, riskResult3);
        
        // 执行分档平仓
        RiskManagementService.TieredLiquidationResult result = 
            riskManagementService.executeTieredLiquidation(1001L, "BTCUSDT", liquidationRequest);
        
        // 验证结果
        assertNotNull(result);
        assertEquals(1001L, result.getUserId());
        assertEquals("BTCUSDT", result.getSymbol());
        assertEquals(RiskLevel.WARNING, result.getFinalRiskLevel());
        assertEquals(3, result.getSteps().size());
        assertTrue(result.getDuration() > 0);
        
        // 验证步骤
        verifyTieredLiquidationSteps(result.getSteps());
        
        // 验证调用次数
        verify(riskRecalculationService, times(3)).recalculateRisk(any(LiquidationRequest.class));
    }
    
    @Test
    void testTieredLiquidationNoPosition() {
        // 模拟无持仓
        when(memoryManager.getPosition(1001L, "BTCUSDT")).thenReturn(null);
        
        // 执行分档平仓
        RiskManagementService.TieredLiquidationResult result = 
            riskManagementService.executeTieredLiquidation(1001L, "BTCUSDT", liquidationRequest);
        
        // 验证结果
        assertNotNull(result);
        assertEquals("用户无持仓", result.getErrorMessage());
        assertEquals(0, result.getSteps().size());
    }
    
    @Test
    void testTieredLiquidationZeroPosition() {
        // 创建零持仓
        Position zeroPosition = new Position();
        zeroPosition.setUserId(1001L);
        zeroPosition.setSymbol("BTCUSDT");
        zeroPosition.setQuantity(BigDecimal.ZERO);
        
        when(memoryManager.getPosition(1001L, "BTCUSDT")).thenReturn(zeroPosition);
        
        // 执行分档平仓
        RiskManagementService.TieredLiquidationResult result = 
            riskManagementService.executeTieredLiquidation(1001L, "BTCUSDT", liquidationRequest);
        
        // 验证结果
        assertNotNull(result);
        assertEquals("用户无持仓", result.getErrorMessage());
        assertEquals(0, result.getSteps().size());
    }
    
    @Test
    void testTieredLiquidationImmediateSafe() {
        // 模拟内存管理器返回持仓
        when(memoryManager.getPosition(1001L, "BTCUSDT")).thenReturn(testPosition);
        
        // 模拟第一次重计算就安全
        RiskRecalculationService.RiskRecalculationResult safeResult = createRiskResult(RiskLevel.NORMAL, false);
        when(riskRecalculationService.recalculateRisk(any(LiquidationRequest.class)))
                .thenReturn(safeResult);
        
        // 执行分档平仓
        RiskManagementService.TieredLiquidationResult result = 
            riskManagementService.executeTieredLiquidation(1001L, "BTCUSDT", liquidationRequest);
        
        // 验证结果
        assertNotNull(result);
        assertEquals(RiskLevel.NORMAL, result.getFinalRiskLevel());
        assertEquals(0, result.getSteps().size());
        
        // 验证只调用一次重计算
        verify(riskRecalculationService, times(1)).recalculateRisk(any(LiquidationRequest.class));
    }
    
    @Test
    void testTieredLiquidationWithNullRiskLevel() {
        // 设置风险等级为null
        liquidationRequest.setRiskLevel(null);
        
        // 模拟内存管理器返回持仓
        when(memoryManager.getPosition(1001L, "BTCUSDT")).thenReturn(testPosition);
        
        // 模拟风险重计算服务返回LIQUIDATION等级
        RiskRecalculationService.RiskRecalculationResult riskResult = createRiskResult(RiskLevel.LIQUIDATION, true);
        when(riskRecalculationService.recalculateRisk(any(LiquidationRequest.class)))
                .thenReturn(riskResult);
        
        // 执行分档平仓
        RiskManagementService.TieredLiquidationResult result = 
            riskManagementService.executeTieredLiquidation(1001L, "BTCUSDT", liquidationRequest);
        
        // 验证结果
        assertNotNull(result);
        assertEquals(RiskLevel.LIQUIDATION, result.getFinalRiskLevel());
        
        // 验证调用了风险重计算
        verify(riskRecalculationService, times(1)).recalculateRisk(any(LiquidationRequest.class));
    }
    
    @Test
    void testLiquidationRatioForTier() {
        // 测试不同档位的平仓比例
        assertEquals(BigDecimal.valueOf(0.5), getLiquidationRatioForTier(RiskLevel.LIQUIDATION));
        assertEquals(BigDecimal.valueOf(0.4), getLiquidationRatioForTier(RiskLevel.EMERGENCY));
        assertEquals(BigDecimal.valueOf(0.3), getLiquidationRatioForTier(RiskLevel.DANGER));
        assertEquals(BigDecimal.valueOf(0.2), getLiquidationRatioForTier(RiskLevel.WARNING));
        assertEquals(BigDecimal.valueOf(0.1), getLiquidationRatioForTier(RiskLevel.NORMAL));
    }
    
    @Test
    void testTargetRiskLevel() {
        // 测试目标风险等级
        assertEquals(RiskLevel.EMERGENCY, getTargetRiskLevel(RiskLevel.LIQUIDATION));
        assertEquals(RiskLevel.DANGER, getTargetRiskLevel(RiskLevel.EMERGENCY));
        assertEquals(RiskLevel.WARNING, getTargetRiskLevel(RiskLevel.DANGER));
        assertEquals(RiskLevel.NORMAL, getTargetRiskLevel(RiskLevel.WARNING));
        assertEquals(RiskLevel.NORMAL, getTargetRiskLevel(RiskLevel.NORMAL));
    }
    
    @Test
    void testNextLowerLevel() {
        // 测试下一个较低档位
        assertEquals(RiskLevel.EMERGENCY, getNextLowerLevel(RiskLevel.LIQUIDATION));
        assertEquals(RiskLevel.DANGER, getNextLowerLevel(RiskLevel.EMERGENCY));
        assertEquals(RiskLevel.WARNING, getNextLowerLevel(RiskLevel.DANGER));
        assertEquals(RiskLevel.NORMAL, getNextLowerLevel(RiskLevel.WARNING));
        assertEquals(RiskLevel.NORMAL, getNextLowerLevel(RiskLevel.NORMAL));
    }
    
    /**
     * 验证分档平仓步骤
     */
    private void verifyTieredLiquidationSteps(java.util.List<RiskManagementService.TieredLiquidationStep> steps) {
        assertEquals(3, steps.size());
        
        // 验证第一步：LIQUIDATION档
        RiskManagementService.TieredLiquidationStep step1 = steps.get(0);
        assertEquals(RiskLevel.LIQUIDATION, step1.getRiskLevel());
        assertTrue(step1.getSuccessQuantity().compareTo(BigDecimal.ZERO) > 0);
        assertTrue(step1.getDuration() > 0);
        
        // 验证第二步：EMERGENCY档
        RiskManagementService.TieredLiquidationStep step2 = steps.get(1);
        assertEquals(RiskLevel.EMERGENCY, step2.getRiskLevel());
        assertTrue(step2.getSuccessQuantity().compareTo(BigDecimal.ZERO) > 0);
        assertTrue(step2.getDuration() > 0);
        
        // 验证第三步：DANGER档
        RiskManagementService.TieredLiquidationStep step3 = steps.get(2);
        assertEquals(RiskLevel.DANGER, step3.getRiskLevel());
        assertTrue(step3.getSuccessQuantity().compareTo(BigDecimal.ZERO) > 0);
        assertTrue(step3.getDuration() > 0);
    }
    
    /**
     * 创建风险重计算结果
     */
    private RiskRecalculationService.RiskRecalculationResult createRiskResult(RiskLevel riskLevel, boolean needLiquidation) {
        return RiskRecalculationService.RiskRecalculationResult.builder()
                .userId(1001L)
                .symbol("BTCUSDT")
                .riskRatio(BigDecimal.valueOf(0.95))
                .riskLevel(riskLevel)
                .needRiskReduction(true)
                .needLiquidation(needLiquidation)
                .currentPrice(BigDecimal.valueOf(50000))
                .margin(BigDecimal.valueOf(10000))
                .unrealizedPnl(BigDecimal.valueOf(-8000))
                .position(testPosition)
                .build();
    }
    
    /**
     * 获取档位平仓比例（通过反射调用私有方法）
     */
    private BigDecimal getLiquidationRatioForTier(RiskLevel riskLevel) {
        try {
            java.lang.reflect.Method method = RiskManagementService.class.getDeclaredMethod("getLiquidationRatioForTier", RiskLevel.class);
            method.setAccessible(true);
            return (BigDecimal) method.invoke(riskManagementService, riskLevel);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
    
    /**
     * 获取目标风险等级（通过反射调用私有方法）
     */
    private RiskLevel getTargetRiskLevel(RiskLevel currentLevel) {
        try {
            java.lang.reflect.Method method = RiskManagementService.class.getDeclaredMethod("getTargetRiskLevel", RiskLevel.class);
            method.setAccessible(true);
            return (RiskLevel) method.invoke(riskManagementService, currentLevel);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
    
    /**
     * 获取下一个较低档位（通过反射调用私有方法）
     */
    private RiskLevel getNextLowerLevel(RiskLevel currentLevel) {
        try {
            java.lang.reflect.Method method = RiskManagementService.class.getDeclaredMethod("getNextLowerLevel", RiskLevel.class);
            method.setAccessible(true);
            return (RiskLevel) method.invoke(riskManagementService, currentLevel);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
} 