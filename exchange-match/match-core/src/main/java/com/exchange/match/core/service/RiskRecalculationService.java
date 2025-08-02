package com.exchange.match.core.service;

import com.exchange.match.core.model.*;
import com.exchange.match.core.memory.MemoryManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 风险重新计算服务
 * 在撮合引擎中根据风控服务传递的数据和标的风险限额重新计算风险率
 */
@Slf4j
@Service
public class RiskRecalculationService {
    
    @Autowired
    private MemoryManager memoryManager;
    
    @Autowired
    private CrossPositionManager crossPositionManager;
    
    /**
     * 重新计算用户风险率
     * 
     * @param liquidationRequest 强平请求（包含风控服务传递的数据）
     * @return 重新计算后的风险信息
     */
    public RiskRecalculationResult recalculateRisk(LiquidationRequest liquidationRequest) {
        Long userId = liquidationRequest.getUserId();
        String symbol = liquidationRequest.getSymbol();
        
        log.info("重新计算用户风险率，用户: {}, 交易对: {}", userId, symbol);
        
        // 获取当前持仓
        Position position = memoryManager.getPosition(userId, symbol);
        if (position == null || position.getQuantity().compareTo(BigDecimal.ZERO) <= 0) {
            log.info("用户{}在{}上无持仓，风险率为0", userId, symbol);
            return createZeroRiskResult();
        }
        
        // 根据仓位模式采用不同的风险计算方式
        BigDecimal riskRatio;
        if (position.isIsolatedMode()) {
            riskRatio = calculateIsolatedRiskRatio(position, liquidationRequest);
        } else {
            riskRatio = calculateCrossRiskRatio(position, liquidationRequest);
        }
        
        // 获取风险等级
        RiskLevel riskLevel = RiskLevel.getRiskLevel(riskRatio);
        
        // 检查是否需要风险降档
        boolean needRiskReduction = riskLevel.needForceReduction();
        
        // 检查是否需要强平
        boolean needLiquidation = riskLevel.needImmediateLiquidation();
        
        // 计算风险限额
        RiskLimitInfo riskLimitInfo = calculateRiskLimits(position, liquidationRequest.getIndexPrice());
        
        log.info("风险重新计算完成，用户: {}, 交易对: {}, 仓位模式: {}, 风险率: {}, 风险等级: {}, 需要降档: {}, 需要强平: {}", 
                userId, symbol, position.getPositionMode().getName(), riskRatio, riskLevel.getName(), needRiskReduction, needLiquidation);
        
        return RiskRecalculationResult.builder()
                .userId(userId)
                .symbol(symbol)
                .riskRatio(riskRatio)
                .riskLevel(riskLevel)
                .needRiskReduction(needRiskReduction)
                .needLiquidation(needLiquidation)
                .currentPrice(liquidationRequest.getIndexPrice())
                .margin(liquidationRequest.getMargin())
                .unrealizedPnl(liquidationRequest.getUnrealizedPnl())
                .position(position)
                .riskLimitInfo(riskLimitInfo)
                .positionMode(position.getPositionMode())
                .build();
    }
    
    /**
     * 计算逐仓风险率
     */
    private BigDecimal calculateIsolatedRiskRatio(Position position, LiquidationRequest liquidationRequest) {
        // 获取标的配置
        Symbol symbolConfig = memoryManager.getSymbol(position.getSymbol());
        if (symbolConfig == null) {
            log.warn("标的{}配置不存在，使用默认配置", position.getSymbol());
            symbolConfig = createDefaultSymbolConfig(position.getSymbol());
        }
        
        // 使用风控服务传递的保证金，如果没有则使用默认值
        BigDecimal margin = liquidationRequest.getMargin();
        if (margin == null || margin.compareTo(BigDecimal.ZERO) <= 0) {
            margin = liquidationRequest.getBalance(); // 使用余额作为保证金
        }
        
        // 重新计算未实现盈亏
        BigDecimal unrealizedPnl = calculateUnrealizedPnl(position, liquidationRequest.getIndexPrice());
        
        // 使用标的配置的风险阈值
        BigDecimal riskThreshold = symbolConfig.getRiskLimitConfig().getIsolatedModeConfig().getThreshold(RiskLevel.NORMAL);
        
        // 逐仓风险率计算
        BigDecimal isolatedRiskRatio = position.getIsolatedRiskRatio();
        
        log.info("逐仓风险率计算，用户: {}, 交易对: {}, 保证金: {}, 未实现盈亏: {}, 风险率: {}, 风险阈值: {}", 
                position.getUserId(), position.getSymbol(), margin, unrealizedPnl, isolatedRiskRatio, riskThreshold);
        
        return isolatedRiskRatio;
    }
    
    /**
     * 计算全仓风险率
     */
    private BigDecimal calculateCrossRiskRatio(Position position, LiquidationRequest liquidationRequest) {
        // 获取标的配置
        Symbol symbolConfig = memoryManager.getSymbol(position.getSymbol());
        if (symbolConfig == null) {
            log.warn("标的{}配置不存在，使用默认配置", position.getSymbol());
            symbolConfig = createDefaultSymbolConfig(position.getSymbol());
        }
        
        // 使用风控服务传递的保证金，如果没有则使用默认值
        BigDecimal totalMargin = liquidationRequest.getMargin();
        if (totalMargin == null || totalMargin.compareTo(BigDecimal.ZERO) <= 0) {
            totalMargin = liquidationRequest.getBalance(); // 使用余额作为保证金
        }
        
        // 重新计算未实现盈亏
        BigDecimal unrealizedPnl = calculateUnrealizedPnl(position, liquidationRequest.getIndexPrice());
        
        // 使用标的配置的风险阈值
        BigDecimal riskThreshold = symbolConfig.getRiskLimitConfig().getCrossModeConfig().getThreshold(RiskLevel.NORMAL);
        
        // 全仓风险率计算
        BigDecimal crossRiskRatio = position.getCrossRiskRatio(totalMargin, unrealizedPnl);
        
        log.info("全仓风险率计算，用户: {}, 交易对: {}, 总保证金: {}, 未实现盈亏: {}, 风险率: {}, 风险阈值: {}", 
                position.getUserId(), position.getSymbol(), totalMargin, unrealizedPnl, crossRiskRatio, riskThreshold);
        
        return crossRiskRatio;
    }
    
    /**
     * 计算未实现盈亏
     */
    private BigDecimal calculateUnrealizedPnl(Position position, BigDecimal currentPrice) {
        if (position.getAveragePrice() == null || position.getAveragePrice().compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }
        
        if (position.getSide() == PositionSide.LONG) {
            // 多仓：当前价格 - 开仓价格
            return position.getQuantity().multiply(currentPrice.subtract(position.getAveragePrice()));
        } else {
            // 空仓：开仓价格 - 当前价格
            return position.getQuantity().multiply(position.getAveragePrice().subtract(currentPrice));
        }
    }
    
    /**
     * 计算风险率（兼容旧版本）
     */
    private BigDecimal calculateRiskRatio(BigDecimal margin, BigDecimal unrealizedPnl) {
        if (margin.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.valueOf(1.0); // 100%风险率
        }
        
        // 风险率 = (保证金 - 未实现盈亏) / 保证金
        BigDecimal availableMargin = margin.subtract(unrealizedPnl);
        BigDecimal riskRatio = availableMargin.divide(margin, 4, RoundingMode.HALF_UP);
        return BigDecimal.ONE.subtract(riskRatio);
    }
    
    /**
     * 计算风险限额
     */
    private RiskLimitInfo calculateRiskLimits(Position position, BigDecimal currentPrice) {
        BigDecimal positionValue = position.getQuantity().multiply(currentPrice);
        
        // 获取标的配置
        Symbol symbolConfig = memoryManager.getSymbol(position.getSymbol());
        if (symbolConfig == null) {
            log.warn("标的{}配置不存在，使用默认配置", position.getSymbol());
            symbolConfig = createDefaultSymbolConfig(position.getSymbol());
        }
        
        // 使用标的配置计算风险限额
        BigDecimal maxLeverage = symbolConfig.getMaxLeverage();
        
        // 同一个标的全仓逐仓使用同一个风险限额配置
        // 使用逐仓模式的配置作为统一配置，因为逐仓模式通常更保守
        BigDecimal maxLeverageForMode = symbolConfig.getIsolatedModeRiskLimitConfig().getMaxLeverage();
        
        return RiskLimitInfo.builder()
                .maxLeverage(maxLeverageForMode)
                .currentPositionValue(positionValue)
                .currentLeverage(calculateLeverage(positionValue, position.getMargin()))
                .build();
    }
    
    /**
     * 计算杠杆率
     */
    private BigDecimal calculateLeverage(BigDecimal positionValue, BigDecimal margin) {
        if (margin == null || margin.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }
        return positionValue.divide(margin, 2, RoundingMode.HALF_UP);
    }
    
    /**
     * 获取当前价格（简化实现）
     */
    private BigDecimal getCurrentPrice(String symbol) {
        // 这里应该从行情服务获取当前价格
        // 简化实现，返回固定价格
        return BigDecimal.valueOf(50000); // 示例价格
    }
    
    /**
     * 创建默认标的配置
     */
    private Symbol createDefaultSymbolConfig(String symbol) {
        Symbol config = new Symbol();
        config.setSymbol(symbol);
        // 使用默认值，不设置不存在的字段
        return config;
    }
    
    /**
     * 创建零风险结果
     */
    private RiskRecalculationResult createZeroRiskResult() {
        return RiskRecalculationResult.builder()
                .riskRatio(BigDecimal.ZERO)
                .riskLevel(RiskLevel.NORMAL)
                .needRiskReduction(false)
                .needLiquidation(false)
                .build();
    }
    
    /**
     * 风险重新计算结果
     */
    @lombok.Data
    @lombok.Builder
    public static class RiskRecalculationResult {
        private Long userId;
        private String symbol;
        private BigDecimal riskRatio;
        private RiskLevel riskLevel;
        private boolean needRiskReduction;
        private boolean needLiquidation;
        private BigDecimal currentPrice;
        private BigDecimal margin;
        private BigDecimal unrealizedPnl;
        private Position position;
        private RiskLimitInfo riskLimitInfo;
        private PositionMode positionMode; // 新增：仓位模式
    }
    
    /**
     * 风险限额信息
     */
    @lombok.Data
    @lombok.Builder
    public static class RiskLimitInfo {
        private BigDecimal maxPositionValue;
        private BigDecimal maxLeverage;
        private BigDecimal currentPositionValue;
        private BigDecimal currentLeverage;
    }
} 