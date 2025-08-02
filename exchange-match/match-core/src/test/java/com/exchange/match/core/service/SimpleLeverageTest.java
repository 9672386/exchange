package com.exchange.match.core.service;

import com.exchange.match.core.model.SymbolRiskLimitConfig;
import com.exchange.match.core.model.RiskLevel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;

/**
 * 简单的杠杆测试
 * 验证同一个标的全仓逐仓使用同一个风险限额配置
 */
public class SimpleLeverageTest {
    
    private SymbolRiskLimitConfigManager symbolRiskLimitConfigManager;
    private SymbolRiskLimitConfig testConfig;
    
    @BeforeEach
    void setUp() {
        symbolRiskLimitConfigManager = new SymbolRiskLimitConfigManager();
        
        // 创建测试配置
        testConfig = new SymbolRiskLimitConfig();
        testConfig.setSymbol("BTCUSDT");
        
        // 设置逐仓模式各风险等级的最大杠杆
        testConfig.getIsolatedModeConfig().getRiskLevelConfig(RiskLevel.NORMAL).setMaxLeverage(new BigDecimal("100"));
        testConfig.getIsolatedModeConfig().getRiskLevelConfig(RiskLevel.WARNING).setMaxLeverage(new BigDecimal("50"));
        testConfig.getIsolatedModeConfig().getRiskLevelConfig(RiskLevel.DANGER).setMaxLeverage(new BigDecimal("25"));
        testConfig.getIsolatedModeConfig().getRiskLevelConfig(RiskLevel.EMERGENCY).setMaxLeverage(new BigDecimal("10"));
        testConfig.getIsolatedModeConfig().getRiskLevelConfig(RiskLevel.LIQUIDATION).setMaxLeverage(BigDecimal.ONE);
        
        // 设置全仓模式各风险等级的最大杠杆（与逐仓不同）
        testConfig.getCrossModeConfig().getRiskLevelConfig(RiskLevel.NORMAL).setMaxLeverage(new BigDecimal("125"));
        testConfig.getCrossModeConfig().getRiskLevelConfig(RiskLevel.WARNING).setMaxLeverage(new BigDecimal("75"));
        testConfig.getCrossModeConfig().getRiskLevelConfig(RiskLevel.DANGER).setMaxLeverage(new BigDecimal("50"));
        testConfig.getCrossModeConfig().getRiskLevelConfig(RiskLevel.EMERGENCY).setMaxLeverage(new BigDecimal("25"));
        testConfig.getCrossModeConfig().getRiskLevelConfig(RiskLevel.LIQUIDATION).setMaxLeverage(BigDecimal.ONE);
        
        // 设置配置
        symbolRiskLimitConfigManager.setRiskLimitConfig("BTCUSDT", testConfig);
    }
    
    @Test
    void testUnifiedLeverageConfig() {
        // 验证同一个标的全仓逐仓使用同一个风险限额配置
        // 现在应该都使用逐仓模式的配置
        
        // 测试NORMAL风险等级
        BigDecimal isolatedNormal = symbolRiskLimitConfigManager.getMaxLeverage("BTCUSDT", RiskLevel.NORMAL, false);
        BigDecimal crossNormal = symbolRiskLimitConfigManager.getMaxLeverage("BTCUSDT", RiskLevel.NORMAL, true);
        assertEquals(isolatedNormal, crossNormal, "NORMAL风险等级的全仓和逐仓杠杆应该相同");
        assertEquals(new BigDecimal("100"), isolatedNormal, "NORMAL风险等级应该返回100倍杠杆");
        
        // 测试WARNING风险等级
        BigDecimal isolatedWarning = symbolRiskLimitConfigManager.getMaxLeverage("BTCUSDT", RiskLevel.WARNING, false);
        BigDecimal crossWarning = symbolRiskLimitConfigManager.getMaxLeverage("BTCUSDT", RiskLevel.WARNING, true);
        assertEquals(isolatedWarning, crossWarning, "WARNING风险等级的全仓和逐仓杠杆应该相同");
        assertEquals(new BigDecimal("50"), isolatedWarning, "WARNING风险等级应该返回50倍杠杆");
        
        // 测试DANGER风险等级
        BigDecimal isolatedDanger = symbolRiskLimitConfigManager.getMaxLeverage("BTCUSDT", RiskLevel.DANGER, false);
        BigDecimal crossDanger = symbolRiskLimitConfigManager.getMaxLeverage("BTCUSDT", RiskLevel.DANGER, true);
        assertEquals(isolatedDanger, crossDanger, "DANGER风险等级的全仓和逐仓杠杆应该相同");
        assertEquals(new BigDecimal("25"), isolatedDanger, "DANGER风险等级应该返回25倍杠杆");
        
        // 测试EMERGENCY风险等级
        BigDecimal isolatedEmergency = symbolRiskLimitConfigManager.getMaxLeverage("BTCUSDT", RiskLevel.EMERGENCY, false);
        BigDecimal crossEmergency = symbolRiskLimitConfigManager.getMaxLeverage("BTCUSDT", RiskLevel.EMERGENCY, true);
        assertEquals(isolatedEmergency, crossEmergency, "EMERGENCY风险等级的全仓和逐仓杠杆应该相同");
        assertEquals(new BigDecimal("10"), isolatedEmergency, "EMERGENCY风险等级应该返回10倍杠杆");
        
        // 测试LIQUIDATION风险等级
        BigDecimal isolatedLiquidation = symbolRiskLimitConfigManager.getMaxLeverage("BTCUSDT", RiskLevel.LIQUIDATION, false);
        BigDecimal crossLiquidation = symbolRiskLimitConfigManager.getMaxLeverage("BTCUSDT", RiskLevel.LIQUIDATION, true);
        assertEquals(isolatedLiquidation, crossLiquidation, "LIQUIDATION风险等级的全仓和逐仓杠杆应该相同");
        assertEquals(BigDecimal.ONE, isolatedLiquidation, "LIQUIDATION风险等级应该返回1倍杠杆");
    }
    
    @Test
    void testLeverageAdjustmentService() {
        // 测试通过SymbolRiskLimitConfigManager获取最大杠杆
        BigDecimal maxLeverage = symbolRiskLimitConfigManager.getMaxLeverage("BTCUSDT", RiskLevel.NORMAL, false);
        assertEquals(new BigDecimal("100"), maxLeverage, "应该返回逐仓模式的NORMAL风险等级杠杆");
        
        // 全仓模式也应该返回相同的值
        BigDecimal maxLeverageCross = symbolRiskLimitConfigManager.getMaxLeverage("BTCUSDT", RiskLevel.NORMAL, true);
        assertEquals(maxLeverage, maxLeverageCross, "全仓和逐仓模式应该返回相同的杠杆");
    }
} 