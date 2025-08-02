package com.exchange.match.core.service;

import com.exchange.match.core.model.SymbolRiskLimitConfig;
import com.exchange.match.core.model.RiskLevel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 杠杆计算测试
 * 撮合系统使用固定的风险等级配置，确保系统稳定可控
 */
@ExtendWith(MockitoExtension.class)
public class DynamicLeverageCalculationTest {
    
    @Mock
    private SymbolRiskLimitConfigManager symbolRiskLimitConfigManager;
    
    @InjectMocks
    private LeverageValidationService leverageValidationService;
    
    private SymbolRiskLimitConfig testConfig;
    
    @BeforeEach
    void setUp() {
        // 创建测试配置
        testConfig = new SymbolRiskLimitConfig();
        testConfig.setSymbol("BTCUSDT");
        
        // 设置逐仓模式各风险等级的最大杠杆
        testConfig.getIsolatedModeConfig().getRiskLevelConfig(RiskLevel.NORMAL).setMaxLeverage(new BigDecimal("100"));
        testConfig.getIsolatedModeConfig().getRiskLevelConfig(RiskLevel.WARNING).setMaxLeverage(new BigDecimal("50"));
        testConfig.getIsolatedModeConfig().getRiskLevelConfig(RiskLevel.DANGER).setMaxLeverage(new BigDecimal("25"));
        testConfig.getIsolatedModeConfig().getRiskLevelConfig(RiskLevel.EMERGENCY).setMaxLeverage(new BigDecimal("10"));
        testConfig.getIsolatedModeConfig().getRiskLevelConfig(RiskLevel.LIQUIDATION).setMaxLeverage(BigDecimal.ONE);
        
        // 设置全仓模式各风险等级的最大杠杆
        testConfig.getCrossModeConfig().getRiskLevelConfig(RiskLevel.NORMAL).setMaxLeverage(new BigDecimal("125"));
        testConfig.getCrossModeConfig().getRiskLevelConfig(RiskLevel.WARNING).setMaxLeverage(new BigDecimal("75"));
        testConfig.getCrossModeConfig().getRiskLevelConfig(RiskLevel.DANGER).setMaxLeverage(new BigDecimal("50"));
        testConfig.getCrossModeConfig().getRiskLevelConfig(RiskLevel.EMERGENCY).setMaxLeverage(new BigDecimal("25"));
        testConfig.getCrossModeConfig().getRiskLevelConfig(RiskLevel.LIQUIDATION).setMaxLeverage(BigDecimal.ONE);
    }
    
    @Test
    void testGetMaxLeverage_IsolatedMode_Normal() {
        // 模拟获取配置
        when(symbolRiskLimitConfigManager.getRiskLimitConfig("BTCUSDT")).thenReturn(testConfig);
        
        // 测试逐仓模式NORMAL风险等级的最大杠杆
        BigDecimal maxLeverage = symbolRiskLimitConfigManager.getMaxLeverage("BTCUSDT", RiskLevel.NORMAL, false);
        
        // 验证结果
        assertEquals(new BigDecimal("100"), maxLeverage);
    }
    
    @Test
    void testGetMaxLeverage_IsolatedMode_Warning() {
        // 模拟获取配置
        when(symbolRiskLimitConfigManager.getRiskLimitConfig("BTCUSDT")).thenReturn(testConfig);
        
        // 测试逐仓模式WARNING风险等级的最大杠杆
        BigDecimal maxLeverage = symbolRiskLimitConfigManager.getMaxLeverage("BTCUSDT", RiskLevel.WARNING, false);
        
        // 验证结果
        assertEquals(new BigDecimal("50"), maxLeverage);
    }
    
    @Test
    void testGetMaxLeverage_IsolatedMode_Danger() {
        // 模拟获取配置
        when(symbolRiskLimitConfigManager.getRiskLimitConfig("BTCUSDT")).thenReturn(testConfig);
        
        // 测试逐仓模式DANGER风险等级的最大杠杆
        BigDecimal maxLeverage = symbolRiskLimitConfigManager.getMaxLeverage("BTCUSDT", RiskLevel.DANGER, false);
        
        // 验证结果
        assertEquals(new BigDecimal("25"), maxLeverage);
    }
    
    @Test
    void testGetMaxLeverage_IsolatedMode_Emergency() {
        // 模拟获取配置
        when(symbolRiskLimitConfigManager.getRiskLimitConfig("BTCUSDT")).thenReturn(testConfig);
        
        // 测试逐仓模式EMERGENCY风险等级的最大杠杆
        BigDecimal maxLeverage = symbolRiskLimitConfigManager.getMaxLeverage("BTCUSDT", RiskLevel.EMERGENCY, false);
        
        // 验证结果
        assertEquals(new BigDecimal("10"), maxLeverage);
    }
    
    @Test
    void testGetMaxLeverage_IsolatedMode_Liquidation() {
        // 模拟获取配置
        when(symbolRiskLimitConfigManager.getRiskLimitConfig("BTCUSDT")).thenReturn(testConfig);
        
        // 测试逐仓模式LIQUIDATION风险等级的最大杠杆
        BigDecimal maxLeverage = symbolRiskLimitConfigManager.getMaxLeverage("BTCUSDT", RiskLevel.LIQUIDATION, false);
        
        // 验证结果
        assertEquals(BigDecimal.ONE, maxLeverage);
    }
    
    @Test
    void testGetMaxLeverage_CrossMode_Normal() {
        // 模拟获取配置
        when(symbolRiskLimitConfigManager.getRiskLimitConfig("BTCUSDT")).thenReturn(testConfig);
        
        // 测试全仓模式NORMAL风险等级的最大杠杆（现在与逐仓模式相同）
        BigDecimal maxLeverage = symbolRiskLimitConfigManager.getMaxLeverage("BTCUSDT", RiskLevel.NORMAL, true);
        
        // 验证结果：现在全仓和逐仓使用相同的配置
        assertEquals(new BigDecimal("100"), maxLeverage);
    }
    
    @Test
    void testGetMaxLeverage_CrossMode_Warning() {
        // 模拟获取配置
        when(symbolRiskLimitConfigManager.getRiskLimitConfig("BTCUSDT")).thenReturn(testConfig);
        
        // 测试全仓模式WARNING风险等级的最大杠杆（现在与逐仓模式相同）
        BigDecimal maxLeverage = symbolRiskLimitConfigManager.getMaxLeverage("BTCUSDT", RiskLevel.WARNING, true);
        
        // 验证结果：现在全仓和逐仓使用相同的配置
        assertEquals(new BigDecimal("50"), maxLeverage);
    }
    
    @Test
    void testGetMaxLeverage_CrossMode_Danger() {
        // 模拟获取配置
        when(symbolRiskLimitConfigManager.getRiskLimitConfig("BTCUSDT")).thenReturn(testConfig);
        
        // 测试全仓模式DANGER风险等级的最大杠杆（现在与逐仓模式相同）
        BigDecimal maxLeverage = symbolRiskLimitConfigManager.getMaxLeverage("BTCUSDT", RiskLevel.DANGER, true);
        
        // 验证结果：现在全仓和逐仓使用相同的配置
        assertEquals(new BigDecimal("25"), maxLeverage);
    }
    
    @Test
    void testGetMaxLeverage_CrossMode_Emergency() {
        // 模拟获取配置
        when(symbolRiskLimitConfigManager.getRiskLimitConfig("BTCUSDT")).thenReturn(testConfig);
        
        // 测试全仓模式EMERGENCY风险等级的最大杠杆（现在与逐仓模式相同）
        BigDecimal maxLeverage = symbolRiskLimitConfigManager.getMaxLeverage("BTCUSDT", RiskLevel.EMERGENCY, true);
        
        // 验证结果：现在全仓和逐仓使用相同的配置
        assertEquals(new BigDecimal("10"), maxLeverage);
    }
    
    @Test
    void testGetMaxLeverage_CrossMode_Liquidation() {
        // 模拟获取配置
        when(symbolRiskLimitConfigManager.getRiskLimitConfig("BTCUSDT")).thenReturn(testConfig);
        
        // 测试全仓模式LIQUIDATION风险等级的最大杠杆（现在与逐仓模式相同）
        BigDecimal maxLeverage = symbolRiskLimitConfigManager.getMaxLeverage("BTCUSDT", RiskLevel.LIQUIDATION, true);
        
        // 验证结果：现在全仓和逐仓使用相同的配置
        assertEquals(BigDecimal.ONE, maxLeverage);
    }
    
    @Test
    void testLeverageValidationWithDynamicMaxLeverage() {
        // 模拟获取配置
        when(symbolRiskLimitConfigManager.getRiskLimitConfig("BTCUSDT")).thenReturn(testConfig);
        
        // 测试不同风险等级下的杠杆验证
        // NORMAL风险等级，尝试使用101倍杠杆（超过100倍限制）
        SymbolRiskLimitConfigManager.LeverageValidationResult result1 = 
            symbolRiskLimitConfigManager.validateLeverage("BTCUSDT", 1001L, new BigDecimal("101"), 
                                                       new BigDecimal("50"), RiskLevel.NORMAL);
        
        // 验证结果
        assertFalse(result1.isValid());
        assertEquals("LEVERAGE_EXCEEDED", result1.getErrorCode());
        
        // WARNING风险等级，尝试使用51倍杠杆（超过50倍限制）
        SymbolRiskLimitConfigManager.LeverageValidationResult result2 = 
            symbolRiskLimitConfigManager.validateLeverage("BTCUSDT", 1001L, new BigDecimal("51"), 
                                                       new BigDecimal("25"), RiskLevel.WARNING);
        
        // 验证结果
        assertFalse(result2.isValid());
        assertEquals("LEVERAGE_EXCEEDED", result2.getErrorCode());
        
        // DANGER风险等级，使用20倍杠杆（在25倍限制内）
        SymbolRiskLimitConfigManager.LeverageValidationResult result3 = 
            symbolRiskLimitConfigManager.validateLeverage("BTCUSDT", 1001L, new BigDecimal("20"), 
                                                       new BigDecimal("15"), RiskLevel.DANGER);
        
        // 验证结果
        assertTrue(result3.isValid());
    }
    
    @Test
    void testLeverageValidationWithFixedRiskLevel() {
        // 模拟获取配置
        when(symbolRiskLimitConfigManager.getRiskLimitConfig("BTCUSDT")).thenReturn(testConfig);
        
        // 测试撮合系统使用固定的NORMAL风险等级
        // 验证系统始终使用NORMAL风险等级的杠杆限制
        SymbolRiskLimitConfigManager.LeverageValidationResult result1 = 
            symbolRiskLimitConfigManager.validateLeverage("BTCUSDT", 1001L, new BigDecimal("101"), 
                                                       new BigDecimal("50"), RiskLevel.NORMAL);
        
        // 验证结果
        assertFalse(result1.isValid());
        assertEquals("LEVERAGE_EXCEEDED", result1.getErrorCode());
        
        // 验证系统不会根据其他风险等级进行动态调整
        SymbolRiskLimitConfigManager.LeverageValidationResult result2 = 
            symbolRiskLimitConfigManager.validateLeverage("BTCUSDT", 1001L, new BigDecimal("50"), 
                                                       new BigDecimal("25"), RiskLevel.NORMAL);
        
        // 验证结果
        assertTrue(result2.isValid());
    }
} 