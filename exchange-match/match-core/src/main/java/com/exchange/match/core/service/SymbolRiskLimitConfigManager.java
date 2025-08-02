package com.exchange.match.core.service;

import com.exchange.match.core.model.SymbolRiskLimitConfig;
import com.exchange.match.core.model.RiskLevel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Symbol风险限额配置管理器
 * 负责管理不同Symbol的风险限额配置
 */
@Slf4j
@Service
public class SymbolRiskLimitConfigManager {
    
    /**
     * Symbol风险限额配置缓存
     * key: symbol, value: 风险限额配置
     */
    private final Map<String, SymbolRiskLimitConfig> configCache = new ConcurrentHashMap<>();
    
    /**
     * 获取Symbol的风险限额配置
     * 如果不存在则创建默认配置
     * 
     * @param symbol 交易对
     * @return 风险限额配置
     */
    public SymbolRiskLimitConfig getRiskLimitConfig(String symbol) {
        return configCache.computeIfAbsent(symbol, this::createDefaultConfig);
    }
    
    /**
     * 设置Symbol的风险限额配置
     * 
     * @param symbol 交易对
     * @param config 风险限额配置
     */
    public void setRiskLimitConfig(String symbol, SymbolRiskLimitConfig config) {
        configCache.put(symbol, config);
        log.info("更新Symbol风险限额配置: {}, 配置: {}", symbol, config);
    }
    
    /**
     * 删除Symbol的风险限额配置
     * 
     * @param symbol 交易对
     */
    public void removeRiskLimitConfig(String symbol) {
        configCache.remove(symbol);
        log.info("删除Symbol风险限额配置: {}", symbol);
    }
    
    /**
     * 检查Symbol是否有自定义风险限额配置
     * 
     * @param symbol 交易对
     * @return 是否有自定义配置
     */
    public boolean hasCustomConfig(String symbol) {
        return configCache.containsKey(symbol);
    }
    
    /**
     * 获取所有Symbol的风险限额配置
     * 
     * @return 所有配置
     */
    public Map<String, SymbolRiskLimitConfig> getAllConfigs() {
        return new ConcurrentHashMap<>(configCache);
    }
    
    /**
     * 获取风险等级阈值（逐仓模式）
     * 
     * @param symbol 交易对
     * @param riskLevel 风险等级
     * @return 风险阈值
     */
    public BigDecimal getIsolatedRiskThreshold(String symbol, RiskLevel riskLevel) {
        SymbolRiskLimitConfig config = getRiskLimitConfig(symbol);
        return config.getIsolatedModeConfig().getThreshold(riskLevel);
    }
    
    /**
     * 获取风险等级阈值（全仓模式）
     * 
     * @param symbol 交易对
     * @param riskLevel 风险等级
     * @return 风险阈值
     */
    public BigDecimal getCrossRiskThreshold(String symbol, RiskLevel riskLevel) {
        SymbolRiskLimitConfig config = getRiskLimitConfig(symbol);
        return config.getCrossModeConfig().getThreshold(riskLevel);
    }
    
    /**
     * 获取减仓比例（逐仓模式）
     * 
     * @param symbol 交易对
     * @param riskLevel 风险等级
     * @return 减仓比例
     */
    public BigDecimal getIsolatedReductionRatio(String symbol, RiskLevel riskLevel) {
        SymbolRiskLimitConfig config = getRiskLimitConfig(symbol);
        return config.getIsolatedModeConfig().getReductionRatio(riskLevel);
    }
    
    /**
     * 获取减仓比例（全仓模式）
     * 
     * @param symbol 交易对
     * @param riskLevel 风险等级
     * @return 减仓比例
     */
    public BigDecimal getCrossReductionRatio(String symbol, RiskLevel riskLevel) {
        SymbolRiskLimitConfig config = getRiskLimitConfig(symbol);
        return config.getCrossModeConfig().getReductionRatio(riskLevel);
    }
    
    /**
     * 获取档位平仓比例（逐仓模式）
     * 
     * @param symbol 交易对
     * @param riskLevel 风险等级
     * @return 平仓比例
     */
    public BigDecimal getIsolatedTieredLiquidationRatio(String symbol, RiskLevel riskLevel) {
        SymbolRiskLimitConfig config = getRiskLimitConfig(symbol);
        return config.getIsolatedModeConfig().getLiquidationRatio(riskLevel);
    }
    
    /**
     * 获取档位平仓比例（全仓模式）
     * 
     * @param symbol 交易对
     * @param riskLevel 风险等级
     * @return 平仓比例
     */
    public BigDecimal getCrossTieredLiquidationRatio(String symbol, RiskLevel riskLevel) {
        SymbolRiskLimitConfig config = getRiskLimitConfig(symbol);
        return config.getCrossModeConfig().getLiquidationRatio(riskLevel);
    }
    
    /**
     * 检查是否启用分档平仓（逐仓模式）
     * 
     * @param symbol 交易对
     * @return 是否启用
     */
    public boolean isIsolatedTieredLiquidationEnabled(String symbol) {
        SymbolRiskLimitConfig config = getRiskLimitConfig(symbol);
        return config.getIsolatedModeConfig().getEnableTieredLiquidation();
    }
    
    /**
     * 检查是否启用分档平仓（全仓模式）
     * 
     * @param symbol 交易对
     * @return 是否启用
     */
    public boolean isCrossTieredLiquidationEnabled(String symbol) {
        SymbolRiskLimitConfig config = getRiskLimitConfig(symbol);
        return config.getCrossModeConfig().getEnableTieredLiquidation();
    }
    
    /**
     * 获取最大分档步骤数（逐仓模式）
     * 
     * @param symbol 交易对
     * @return 最大步骤数
     */
    public int getIsolatedMaxTieredSteps(String symbol) {
        SymbolRiskLimitConfig config = getRiskLimitConfig(symbol);
        return config.getIsolatedModeConfig().getMaxTieredSteps();
    }
    
    /**
     * 获取最大分档步骤数（全仓模式）
     * 
     * @param symbol 交易对
     * @return 最大步骤数
     */
    public int getCrossMaxTieredSteps(String symbol) {
        SymbolRiskLimitConfig config = getRiskLimitConfig(symbol);
        return config.getCrossModeConfig().getMaxTieredSteps();
    }
    
    /**
     * 创建默认配置
     * 
     * @param symbol 交易对
     * @return 默认配置
     */
    private SymbolRiskLimitConfig createDefaultConfig(String symbol) {
        SymbolRiskLimitConfig config = new SymbolRiskLimitConfig();
        config.setSymbol(symbol);
        config.setConfigId("DEFAULT_" + symbol);
        
        // 根据Symbol特性设置不同的默认配置
        customizeDefaultConfig(symbol, config);
        
        log.info("为Symbol创建默认风险限额配置: {}", symbol);
        return config;
    }
    
    /**
     * 根据Symbol特性定制默认配置
     * 
     * @param symbol 交易对
     * @param config 配置对象
     */
    private void customizeDefaultConfig(String symbol, SymbolRiskLimitConfig config) {
        // 根据Symbol类型设置不同的默认配置
        if (symbol.endsWith("USDT")) {
            // USDT交易对：相对保守的配置
            customizeUSDTConfig(config);
        } else if (symbol.endsWith("BTC")) {
            // BTC交易对：相对激进的配置
            customizeBTCConfig(config);
        } else if (symbol.endsWith("ETH")) {
            // ETH交易对：中等配置
            customizeETHConfig(config);
        } else {
            // 其他交易对：使用默认配置
            customizeDefaultSymbolConfig(config);
        }
    }
    
    /**
     * 定制USDT交易对配置
     */
    private void customizeUSDTConfig(SymbolRiskLimitConfig config) {
        // USDT交易对通常波动较小，使用相对保守的配置
        
        // 逐仓模式配置
        SymbolRiskLimitConfig.IsolatedModeRiskLimitConfig isolatedConfig = config.getIsolatedModeConfig();
        isolatedConfig.setMaxLeverage(new BigDecimal("50"));
        
        // 设置逐仓模式各风险等级配置
        SymbolRiskLimitConfig.RiskLevelConfig isolatedWarning = isolatedConfig.getRiskLevelConfig(RiskLevel.WARNING);
        isolatedWarning.setThreshold(new BigDecimal("0.85"));
        isolatedWarning.setReductionRatio(new BigDecimal("0.05"));
        isolatedWarning.setLiquidationRatio(new BigDecimal("0.15"));
        isolatedWarning.setMaxLeverage(new BigDecimal("50"));
        
        SymbolRiskLimitConfig.RiskLevelConfig isolatedDanger = isolatedConfig.getRiskLevelConfig(RiskLevel.DANGER);
        isolatedDanger.setThreshold(new BigDecimal("0.92"));
        isolatedDanger.setReductionRatio(new BigDecimal("0.25"));
        isolatedDanger.setLiquidationRatio(new BigDecimal("0.25"));
        isolatedDanger.setMaxLeverage(new BigDecimal("25"));
        
        SymbolRiskLimitConfig.RiskLevelConfig isolatedEmergency = isolatedConfig.getRiskLevelConfig(RiskLevel.EMERGENCY);
        isolatedEmergency.setThreshold(new BigDecimal("0.96"));
        isolatedEmergency.setReductionRatio(new BigDecimal("0.4"));
        isolatedEmergency.setLiquidationRatio(new BigDecimal("0.35"));
        isolatedEmergency.setMaxLeverage(new BigDecimal("10"));
        
        // 全仓模式配置
        SymbolRiskLimitConfig.CrossModeRiskLimitConfig crossConfig = config.getCrossModeConfig();
        crossConfig.setMaxLeverage(new BigDecimal("75"));
        
        // 设置全仓模式各风险等级配置
        SymbolRiskLimitConfig.RiskLevelConfig crossWarning = crossConfig.getRiskLevelConfig(RiskLevel.WARNING);
        crossWarning.setThreshold(new BigDecimal("0.82"));
        crossWarning.setReductionRatio(new BigDecimal("0.1"));
        crossWarning.setLiquidationRatio(new BigDecimal("0.08"));
        crossWarning.setMaxLeverage(new BigDecimal("75"));
        
        SymbolRiskLimitConfig.RiskLevelConfig crossDanger = crossConfig.getRiskLevelConfig(RiskLevel.DANGER);
        crossDanger.setThreshold(new BigDecimal("0.88"));
        crossDanger.setReductionRatio(new BigDecimal("0.25"));
        crossDanger.setLiquidationRatio(new BigDecimal("0.15"));
        crossDanger.setMaxLeverage(new BigDecimal("50"));
        
        SymbolRiskLimitConfig.RiskLevelConfig crossEmergency = crossConfig.getRiskLevelConfig(RiskLevel.EMERGENCY);
        crossEmergency.setThreshold(new BigDecimal("0.92"));
        crossEmergency.setReductionRatio(new BigDecimal("0.5"));
        crossEmergency.setLiquidationRatio(new BigDecimal("0.25"));
        crossEmergency.setMaxLeverage(new BigDecimal("25"));
    }
    
    /**
     * 定制BTC交易对配置
     */
    private void customizeBTCConfig(SymbolRiskLimitConfig config) {
        // BTC交易对波动较大，使用相对激进的配置
        
        // 逐仓模式配置
        SymbolRiskLimitConfig.IsolatedModeRiskLimitConfig isolatedConfig = config.getIsolatedModeConfig();
        isolatedConfig.setMaxLeverage(new BigDecimal("25"));
        
        // 设置逐仓模式各风险等级配置
        SymbolRiskLimitConfig.RiskLevelConfig isolatedWarning = isolatedConfig.getRiskLevelConfig(RiskLevel.WARNING);
        isolatedWarning.setThreshold(new BigDecimal("0.75"));
        isolatedWarning.setReductionRatio(new BigDecimal("0.15"));
        isolatedWarning.setLiquidationRatio(new BigDecimal("0.25"));
        isolatedWarning.setMaxLeverage(new BigDecimal("25"));
        
        SymbolRiskLimitConfig.RiskLevelConfig isolatedDanger = isolatedConfig.getRiskLevelConfig(RiskLevel.DANGER);
        isolatedDanger.setThreshold(new BigDecimal("0.85"));
        isolatedDanger.setReductionRatio(new BigDecimal("0.35"));
        isolatedDanger.setLiquidationRatio(new BigDecimal("0.4"));
        isolatedDanger.setMaxLeverage(new BigDecimal("15"));
        
        SymbolRiskLimitConfig.RiskLevelConfig isolatedEmergency = isolatedConfig.getRiskLevelConfig(RiskLevel.EMERGENCY);
        isolatedEmergency.setThreshold(new BigDecimal("0.92"));
        isolatedEmergency.setReductionRatio(new BigDecimal("0.6"));
        isolatedEmergency.setLiquidationRatio(new BigDecimal("0.5"));
        isolatedEmergency.setMaxLeverage(new BigDecimal("5"));
        
        // 全仓模式配置
        SymbolRiskLimitConfig.CrossModeRiskLimitConfig crossConfig = config.getCrossModeConfig();
        crossConfig.setMaxLeverage(new BigDecimal("50"));
        
        // 设置全仓模式各风险等级配置
        SymbolRiskLimitConfig.RiskLevelConfig crossWarning = crossConfig.getRiskLevelConfig(RiskLevel.WARNING);
        crossWarning.setThreshold(new BigDecimal("0.72"));
        crossWarning.setReductionRatio(new BigDecimal("0.2"));
        crossWarning.setLiquidationRatio(new BigDecimal("0.12"));
        crossWarning.setMaxLeverage(new BigDecimal("50"));
        
        SymbolRiskLimitConfig.RiskLevelConfig crossDanger = crossConfig.getRiskLevelConfig(RiskLevel.DANGER);
        crossDanger.setThreshold(new BigDecimal("0.82"));
        crossDanger.setReductionRatio(new BigDecimal("0.4"));
        crossDanger.setLiquidationRatio(new BigDecimal("0.25"));
        crossDanger.setMaxLeverage(new BigDecimal("30"));
        
        SymbolRiskLimitConfig.RiskLevelConfig crossEmergency = crossConfig.getRiskLevelConfig(RiskLevel.EMERGENCY);
        crossEmergency.setThreshold(new BigDecimal("0.88"));
        crossEmergency.setReductionRatio(new BigDecimal("0.7"));
        crossEmergency.setLiquidationRatio(new BigDecimal("0.35"));
        crossEmergency.setMaxLeverage(new BigDecimal("15"));
    }
    
    /**
     * 定制ETH交易对配置
     */
    private void customizeETHConfig(SymbolRiskLimitConfig config) {
        // ETH交易对使用中等配置
        
        // 逐仓模式配置
        SymbolRiskLimitConfig.IsolatedModeRiskLimitConfig isolatedConfig = config.getIsolatedModeConfig();
        isolatedConfig.setMaxLeverage(new BigDecimal("75"));
        
        // 设置逐仓模式各风险等级配置
        SymbolRiskLimitConfig.RiskLevelConfig isolatedWarning = isolatedConfig.getRiskLevelConfig(RiskLevel.WARNING);
        isolatedWarning.setThreshold(new BigDecimal("0.8"));
        isolatedWarning.setReductionRatio(new BigDecimal("0.1"));
        isolatedWarning.setLiquidationRatio(new BigDecimal("0.18"));
        isolatedWarning.setMaxLeverage(new BigDecimal("75"));
        
        SymbolRiskLimitConfig.RiskLevelConfig isolatedDanger = isolatedConfig.getRiskLevelConfig(RiskLevel.DANGER);
        isolatedDanger.setThreshold(new BigDecimal("0.88"));
        isolatedDanger.setReductionRatio(new BigDecimal("0.3"));
        isolatedDanger.setLiquidationRatio(new BigDecimal("0.3"));
        isolatedDanger.setMaxLeverage(new BigDecimal("40"));
        
        SymbolRiskLimitConfig.RiskLevelConfig isolatedEmergency = isolatedConfig.getRiskLevelConfig(RiskLevel.EMERGENCY);
        isolatedEmergency.setThreshold(new BigDecimal("0.94"));
        isolatedEmergency.setReductionRatio(new BigDecimal("0.5"));
        isolatedEmergency.setLiquidationRatio(new BigDecimal("0.4"));
        isolatedEmergency.setMaxLeverage(new BigDecimal("15"));
        
        // 全仓模式配置
        SymbolRiskLimitConfig.CrossModeRiskLimitConfig crossConfig = config.getCrossModeConfig();
        crossConfig.setMaxLeverage(new BigDecimal("100"));
        
        // 设置全仓模式各风险等级配置
        SymbolRiskLimitConfig.RiskLevelConfig crossWarning = crossConfig.getRiskLevelConfig(RiskLevel.WARNING);
        crossWarning.setThreshold(new BigDecimal("0.78"));
        crossWarning.setReductionRatio(new BigDecimal("0.12"));
        crossWarning.setLiquidationRatio(new BigDecimal("0.09"));
        crossWarning.setMaxLeverage(new BigDecimal("100"));
        
        SymbolRiskLimitConfig.RiskLevelConfig crossDanger = crossConfig.getRiskLevelConfig(RiskLevel.DANGER);
        crossDanger.setThreshold(new BigDecimal("0.85"));
        crossDanger.setReductionRatio(new BigDecimal("0.3"));
        crossDanger.setLiquidationRatio(new BigDecimal("0.18"));
        crossDanger.setMaxLeverage(new BigDecimal("60"));
        
        SymbolRiskLimitConfig.RiskLevelConfig crossEmergency = crossConfig.getRiskLevelConfig(RiskLevel.EMERGENCY);
        crossEmergency.setThreshold(new BigDecimal("0.9"));
        crossEmergency.setReductionRatio(new BigDecimal("0.55"));
        crossEmergency.setLiquidationRatio(new BigDecimal("0.28"));
        crossEmergency.setMaxLeverage(new BigDecimal("30"));
    }
    
    /**
     * 定制默认Symbol配置
     */
    private void customizeDefaultSymbolConfig(SymbolRiskLimitConfig config) {
        // 使用系统默认配置，不做特殊定制
        log.debug("使用默认风险限额配置");
    }
    
    /**
     * 批量更新配置
     * 
     * @param configs 配置映射
     */
    public void batchUpdateConfigs(Map<String, SymbolRiskLimitConfig> configs) {
        configCache.putAll(configs);
        log.info("批量更新Symbol风险限额配置，共{}个配置", configs.size());
    }
    
    /**
     * 清空所有配置
     */
    public void clearAllConfigs() {
        configCache.clear();
        log.info("清空所有Symbol风险限额配置");
    }
    
    /**
     * 获取配置统计信息
     * 
     * @return 统计信息
     */
    public String getConfigStats() {
        return String.format("Symbol风险限额配置统计: 总配置数=%d, 自定义配置数=%d", 
                configCache.size(), 
                configCache.size());
    }
    
    /**
     * 获取杠杆验证规则
     * 
     * @param symbol 交易对
     * @param riskLevel 风险等级
     * @return 杠杆验证规则
     */
    public SymbolRiskLimitConfig.LeverageValidationRule getLeverageValidationRule(String symbol, RiskLevel riskLevel) {
        SymbolRiskLimitConfig config = getRiskLimitConfig(symbol);
        
        // 根据风险等级获取验证规则
        if (riskLevel == RiskLevel.NORMAL) {
            return config.getIsolatedModeConfig().getRiskLevelConfig(riskLevel).getLeverageValidationRule();
        } else {
            // 高风险等级使用更严格的规则
            return SymbolRiskLimitConfig.LeverageValidationRule.UNIFORM;
        }
    }
    
    /**
     * 检查是否允许杠杆交易
     * 
     * @param symbol 交易对
     * @param riskLevel 风险等级
     * @return 是否允许
     */
    public boolean isLeverageTradingAllowed(String symbol, RiskLevel riskLevel) {
        SymbolRiskLimitConfig config = getRiskLimitConfig(symbol);
        return config.getIsolatedModeConfig().getRiskLevelConfig(riskLevel).getAllowLeverage();
    }
    
    /**
     * 获取指定风险等级的最大杠杆
     * 
     * @param symbol 交易对
     * @param riskLevel 风险等级
     * @param isCrossMode 是否为全仓模式（已废弃，保留参数兼容性）
     * @return 最大杠杆
     */
    public BigDecimal getMaxLeverage(String symbol, RiskLevel riskLevel, boolean isCrossMode) {
        SymbolRiskLimitConfig config = getRiskLimitConfig(symbol);
        
        // 同一个标的全仓逐仓使用同一个风险限额配置
        // 使用逐仓模式的配置作为统一配置，因为逐仓模式通常更保守
        return config.getIsolatedModeConfig().getMaxLeverage(riskLevel);
    }
    
    /**
     * 验证杠杆是否符合规则
     * 
     * @param symbol 交易对
     * @param userId 用户ID
     * @param newLeverage 新杠杆
     * @param existingLeverage 现有杠杆
     * @param riskLevel 风险等级
     * @return 验证结果
     */
    public LeverageValidationResult validateLeverage(String symbol, Long userId, BigDecimal newLeverage, 
                                                   BigDecimal existingLeverage, RiskLevel riskLevel) {
        LeverageValidationResult result = new LeverageValidationResult();
        result.setValid(true);
        
        // 获取杠杆验证规则
        SymbolRiskLimitConfig.LeverageValidationRule rule = getLeverageValidationRule(symbol, riskLevel);
        
        // 获取最大允许杠杆
        BigDecimal maxLeverage = getMaxLeverage(symbol, riskLevel, false); // 默认逐仓模式
        
        // 1. 检查杠杆是否超过最大值
        if (newLeverage.compareTo(maxLeverage) > 0) {
            result.setValid(false);
            result.setErrorCode("LEVERAGE_EXCEEDED");
            result.setErrorMessage("杠杆倍数超过最大允许值: " + maxLeverage);
            return result;
        }
        
        // 2. 检查杠杆是否小于最小值
        if (newLeverage.compareTo(BigDecimal.ONE) < 0) {
            result.setValid(false);
            result.setErrorCode("LEVERAGE_TOO_LOW");
            result.setErrorMessage("杠杆倍数不能小于1");
            return result;
        }
        
        // 3. 根据验证规则检查
        switch (rule) {
            case UNIFORM:
                if (existingLeverage != null && existingLeverage.compareTo(newLeverage) != 0) {
                    result.setValid(false);
                    result.setErrorCode("LEVERAGE_MISMATCH");
                    result.setErrorMessage("统一杠杆规则：必须与现有杠杆保持一致");
                }
                break;
            case INCREASING:
                if (existingLeverage != null && newLeverage.compareTo(existingLeverage) < 0) {
                    result.setValid(false);
                    result.setErrorCode("LEVERAGE_NOT_INCREASING");
                    result.setErrorMessage("递增杠杆规则：新杠杆必须大于等于现有杠杆");
                }
                break;
            case DECREASING:
                if (existingLeverage != null && newLeverage.compareTo(existingLeverage) > 0) {
                    result.setValid(false);
                    result.setErrorCode("LEVERAGE_NOT_DECREASING");
                    result.setErrorMessage("递减杠杆规则：新杠杆必须小于等于现有杠杆");
                }
                break;
            case FLEXIBLE:
                // 灵活杠杆：允许任意杠杆，无需特殊验证
                break;
        }
        
        return result;
    }
    
    /**
     * 杠杆验证结果
     */
    @lombok.Data
    public static class LeverageValidationResult {
        private boolean valid;
        private String errorCode;
        private String errorMessage;
        private String symbol;
        private Long userId;
        private BigDecimal leverage;
    }
} 