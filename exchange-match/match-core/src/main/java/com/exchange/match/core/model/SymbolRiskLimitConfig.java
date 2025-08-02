package com.exchange.match.core.model;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Symbol风险限额配置
 * 支持按Symbol配置不同的风险限额参数
 * 全仓和逐仓使用不同的风险限额配置
 */
@Data
public class SymbolRiskLimitConfig {
    
    /**
     * 配置ID
     */
    private String configId;
    
    /**
     * 交易对
     */
    private String symbol;
    
    /**
     * 是否启用
     */
    private Boolean enabled = true;
    
    /**
     * 逐仓模式风险限额配置
     */
    private IsolatedModeRiskLimitConfig isolatedModeConfig;
    
    /**
     * 全仓模式风险限额配置
     */
    private CrossModeRiskLimitConfig crossModeConfig;
    
    /**
     * 强平配置
     */
    private LiquidationRiskLimitConfig liquidationConfig;
    
    /**
     * 创建时间
     */
    private LocalDateTime createTime;
    
    /**
     * 更新时间
     */
    private LocalDateTime updateTime;
    
    public SymbolRiskLimitConfig() {
        this.createTime = LocalDateTime.now();
        this.updateTime = LocalDateTime.now();
        this.enabled = true;
        
        // 初始化默认配置
        initializeDefaultConfig();
    }
    
    /**
     * 初始化默认配置
     */
    private void initializeDefaultConfig() {
        this.isolatedModeConfig = new IsolatedModeRiskLimitConfig();
        this.crossModeConfig = new CrossModeRiskLimitConfig();
        this.liquidationConfig = new LiquidationRiskLimitConfig();
    }
    
    /**
     * 逐仓模式风险限额配置
     * 每个风险等级都有独立的配置
     */
    @Data
    public static class IsolatedModeRiskLimitConfig {
        /**
         * 最大杠杆倍数
         */
        private BigDecimal maxLeverage = new BigDecimal("100");
        
        /**
         * 各风险等级的独立配置
         */
        private Map<RiskLevel, RiskLevelConfig> riskLevelConfigs = new HashMap<>();
        
        /**
         * 是否启用分档平仓
         */
        private Boolean enableTieredLiquidation = true;
        
        /**
         * 最大分档步骤数
         */
        private Integer maxTieredSteps = 5;
        
        public IsolatedModeRiskLimitConfig() {
            // 初始化各风险等级的默认配置
            initializeDefaultRiskLevelConfigs();
        }
        
        private void initializeDefaultRiskLevelConfigs() {
            // NORMAL等级配置
            RiskLevelConfig normalConfig = new RiskLevelConfig();
            normalConfig.setThreshold(new BigDecimal("0.8"));
            normalConfig.setReductionRatio(BigDecimal.ZERO);
            normalConfig.setLiquidationRatio(new BigDecimal("0.1"));
            normalConfig.setMaxLeverage(new BigDecimal("100"));
            riskLevelConfigs.put(RiskLevel.NORMAL, normalConfig);
            
            // WARNING等级配置
            RiskLevelConfig warningConfig = new RiskLevelConfig();
            warningConfig.setThreshold(new BigDecimal("0.9"));
            warningConfig.setReductionRatio(new BigDecimal("0.1"));
            warningConfig.setLiquidationRatio(new BigDecimal("0.2"));
            warningConfig.setMaxLeverage(new BigDecimal("50"));
            riskLevelConfigs.put(RiskLevel.WARNING, warningConfig);
            
            // DANGER等级配置
            RiskLevelConfig dangerConfig = new RiskLevelConfig();
            dangerConfig.setThreshold(new BigDecimal("0.95"));
            dangerConfig.setReductionRatio(new BigDecimal("0.3"));
            dangerConfig.setLiquidationRatio(new BigDecimal("0.3"));
            dangerConfig.setMaxLeverage(new BigDecimal("25"));
            riskLevelConfigs.put(RiskLevel.DANGER, dangerConfig);
            
            // EMERGENCY等级配置
            RiskLevelConfig emergencyConfig = new RiskLevelConfig();
            emergencyConfig.setThreshold(new BigDecimal("0.98"));
            emergencyConfig.setReductionRatio(new BigDecimal("0.5"));
            emergencyConfig.setLiquidationRatio(new BigDecimal("0.4"));
            emergencyConfig.setMaxLeverage(new BigDecimal("10"));
            riskLevelConfigs.put(RiskLevel.EMERGENCY, emergencyConfig);
            
            // LIQUIDATION等级配置
            RiskLevelConfig liquidationConfig = new RiskLevelConfig();
            liquidationConfig.setThreshold(new BigDecimal("1.0"));
            liquidationConfig.setReductionRatio(new BigDecimal("1.0"));
            liquidationConfig.setLiquidationRatio(new BigDecimal("0.5"));
            liquidationConfig.setMaxLeverage(BigDecimal.ONE);
            riskLevelConfigs.put(RiskLevel.LIQUIDATION, liquidationConfig);
        }
        
        /**
         * 获取指定风险等级的配置
         */
        public RiskLevelConfig getRiskLevelConfig(RiskLevel riskLevel) {
            return riskLevelConfigs.getOrDefault(riskLevel, riskLevelConfigs.get(RiskLevel.NORMAL));
        }
        
        /**
         * 设置指定风险等级的配置
         */
        public void setRiskLevelConfig(RiskLevel riskLevel, RiskLevelConfig config) {
            riskLevelConfigs.put(riskLevel, config);
        }
        
        /**
         * 获取指定风险等级的阈值
         */
        public BigDecimal getThreshold(RiskLevel riskLevel) {
            return getRiskLevelConfig(riskLevel).getThreshold();
        }
        
        /**
         * 获取指定风险等级的减仓比例
         */
        public BigDecimal getReductionRatio(RiskLevel riskLevel) {
            return getRiskLevelConfig(riskLevel).getReductionRatio();
        }
        
        /**
         * 获取指定风险等级的平仓比例
         */
        public BigDecimal getLiquidationRatio(RiskLevel riskLevel) {
            return getRiskLevelConfig(riskLevel).getLiquidationRatio();
        }
        
        /**
         * 获取指定风险等级的最大杠杆
         */
        public BigDecimal getMaxLeverage(RiskLevel riskLevel) {
            return getRiskLevelConfig(riskLevel).getMaxLeverage();
        }
    }
    
    /**
     * 全仓模式风险限额配置
     * 每个风险等级都有独立的配置
     */
    @Data
    public static class CrossModeRiskLimitConfig {
        /**
         * 最大杠杆倍数
         */
        private BigDecimal maxLeverage = new BigDecimal("125");
        
        /**
         * 各风险等级的独立配置
         */
        private Map<RiskLevel, RiskLevelConfig> riskLevelConfigs = new HashMap<>();
        
        /**
         * 是否启用分档平仓
         */
        private Boolean enableTieredLiquidation = true;
        
        /**
         * 最大分档步骤数
         */
        private Integer maxTieredSteps = 3;
        
        /**
         * 强平优先级
         */
        private String liquidationPriority = "UNREALIZED_PNL";
        
        public CrossModeRiskLimitConfig() {
            // 初始化各风险等级的默认配置
            initializeDefaultRiskLevelConfigs();
        }
        
        private void initializeDefaultRiskLevelConfigs() {
            // NORMAL等级配置
            RiskLevelConfig normalConfig = new RiskLevelConfig();
            normalConfig.setThreshold(new BigDecimal("0.8"));
            normalConfig.setReductionRatio(BigDecimal.ZERO);
            normalConfig.setLiquidationRatio(new BigDecimal("0.05"));
            normalConfig.setMaxLeverage(new BigDecimal("125"));
            riskLevelConfigs.put(RiskLevel.NORMAL, normalConfig);
            
            // WARNING等级配置
            RiskLevelConfig warningConfig = new RiskLevelConfig();
            warningConfig.setThreshold(new BigDecimal("0.85"));
            warningConfig.setReductionRatio(new BigDecimal("0.15"));
            warningConfig.setLiquidationRatio(new BigDecimal("0.1"));
            warningConfig.setMaxLeverage(new BigDecimal("75"));
            riskLevelConfigs.put(RiskLevel.WARNING, warningConfig);
            
            // DANGER等级配置
            RiskLevelConfig dangerConfig = new RiskLevelConfig();
            dangerConfig.setThreshold(new BigDecimal("0.9"));
            dangerConfig.setReductionRatio(new BigDecimal("0.35"));
            dangerConfig.setLiquidationRatio(new BigDecimal("0.2"));
            dangerConfig.setMaxLeverage(new BigDecimal("50"));
            riskLevelConfigs.put(RiskLevel.DANGER, dangerConfig);
            
            // EMERGENCY等级配置
            RiskLevelConfig emergencyConfig = new RiskLevelConfig();
            emergencyConfig.setThreshold(new BigDecimal("0.95"));
            emergencyConfig.setReductionRatio(new BigDecimal("0.6"));
            emergencyConfig.setLiquidationRatio(new BigDecimal("0.3"));
            emergencyConfig.setMaxLeverage(new BigDecimal("25"));
            riskLevelConfigs.put(RiskLevel.EMERGENCY, emergencyConfig);
            
            // LIQUIDATION等级配置
            RiskLevelConfig liquidationConfig = new RiskLevelConfig();
            liquidationConfig.setThreshold(new BigDecimal("1.0"));
            liquidationConfig.setReductionRatio(new BigDecimal("1.0"));
            liquidationConfig.setLiquidationRatio(new BigDecimal("0.4"));
            liquidationConfig.setMaxLeverage(BigDecimal.ONE);
            riskLevelConfigs.put(RiskLevel.LIQUIDATION, liquidationConfig);
        }
        
        /**
         * 获取指定风险等级的配置
         */
        public RiskLevelConfig getRiskLevelConfig(RiskLevel riskLevel) {
            return riskLevelConfigs.getOrDefault(riskLevel, riskLevelConfigs.get(RiskLevel.NORMAL));
        }
        
        /**
         * 设置指定风险等级的配置
         */
        public void setRiskLevelConfig(RiskLevel riskLevel, RiskLevelConfig config) {
            riskLevelConfigs.put(riskLevel, config);
        }
        
        /**
         * 获取指定风险等级的阈值
         */
        public BigDecimal getThreshold(RiskLevel riskLevel) {
            return getRiskLevelConfig(riskLevel).getThreshold();
        }
        
        /**
         * 获取指定风险等级的减仓比例
         */
        public BigDecimal getReductionRatio(RiskLevel riskLevel) {
            return getRiskLevelConfig(riskLevel).getReductionRatio();
        }
        
        /**
         * 获取指定风险等级的平仓比例
         */
        public BigDecimal getLiquidationRatio(RiskLevel riskLevel) {
            return getRiskLevelConfig(riskLevel).getLiquidationRatio();
        }
        
        /**
         * 获取指定风险等级的最大杠杆
         */
        public BigDecimal getMaxLeverage(RiskLevel riskLevel) {
            return getRiskLevelConfig(riskLevel).getMaxLeverage();
        }
    }
    
    /**
     * 单个风险等级的配置
     */
    @Data
    public static class RiskLevelConfig {
        /**
         * 风险阈值
         */
        private BigDecimal threshold;
        
        /**
         * 减仓比例
         */
        private BigDecimal reductionRatio;
        
        /**
         * 平仓比例
         */
        private BigDecimal liquidationRatio;
        
        /**
         * 最大杠杆倍数
         */
        private BigDecimal maxLeverage;
        
        /**
         * 是否允许开仓
         */
        private Boolean allowOpenPosition = true;
        
        /**
         * 是否允许平仓
         */
        private Boolean allowClosePosition = true;
        
        /**
         * 是否强制减仓
         */
        private Boolean forceReduction = false;
        
        /**
         * 是否立即强平
         */
        private Boolean immediateLiquidation = false;
        
        /**
         * 是否允许杠杆交易
         */
        private Boolean allowLeverage = true;
        
        /**
         * 杠杆验证规则
         */
        private LeverageValidationRule leverageValidationRule = LeverageValidationRule.UNIFORM;
    }
    
    /**
     * 杠杆验证规则
     */
    public enum LeverageValidationRule {
        /**
         * 统一杠杆：同一用户在同一标的上只能使用相同的杠杆倍数
         */
        UNIFORM,
        
        /**
         * 灵活杠杆：同一用户在同一标的上可以使用不同的杠杆倍数
         */
        FLEXIBLE,
        
        /**
         * 递增杠杆：同一用户在同一标的上只能使用递增的杠杆倍数
         */
        INCREASING,
        
        /**
         * 递减杠杆：同一用户在同一标的上只能使用递减的杠杆倍数
         */
        DECREASING
    }
    
    /**
     * 强平配置
     */
    @Data
    public static class LiquidationRiskLimitConfig {
        /**
         * 是否启用自动强平
         */
        private Boolean enableAutoLiquidation = true;
        
        /**
         * 是否启用手动强平
         */
        private Boolean enableManualLiquidation = true;
        
        /**
         * 是否启用ADL（自动减仓）
         */
        private Boolean enableADL = true;
        
        /**
         * ADL策略
         */
        private String adlStrategy = "PROFIT_FIRST";
        
        /**
         * ADL减仓比例
         */
        private BigDecimal adlRatio = new BigDecimal("0.1");
        
        /**
         * 最大ADL次数
         */
        private Integer maxADLCount = 10;
        
        /**
         * 强平延迟（毫秒）
         */
        private Long liquidationDelay = 5000L;
        
        /**
         * 强平价格滑点（百分比）
         */
        private BigDecimal liquidationSlippage = new BigDecimal("0.02");
        
        /**
         * 最小强平数量
         */
        private BigDecimal minLiquidationQuantity = new BigDecimal("0.001");
        
        /**
         * 最大强平数量
         */
        private BigDecimal maxLiquidationQuantity = new BigDecimal("1000");
    }
} 