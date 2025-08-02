package com.exchange.match.core.service;

import com.exchange.match.core.model.Order;
import com.exchange.match.core.model.Position;
import com.exchange.match.core.model.SymbolRiskLimitConfig;
import com.exchange.match.core.model.RiskLevel;
import com.exchange.match.core.memory.MemoryManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 杠杆验证服务
 * 专门处理杠杆相关的验证逻辑
 */
@Slf4j
@Service
public class LeverageValidationService {
    
    @Autowired
    private MemoryManager memoryManager;
    
    @Autowired
    private SymbolRiskLimitConfigManager symbolRiskLimitConfigManager;
    
    @Autowired
    private CrossPositionManager crossPositionManager;
    
    /**
     * 验证订单杠杆
     * 
     * @param order 订单
     * @return 验证结果
     */
    public LeverageValidationResult validateOrderLeverage(Order order) {
        LeverageValidationResult result = new LeverageValidationResult();
        result.setValid(true);
        result.setOrderId(order.getOrderId());
        result.setUserId(order.getUserId());
        result.setSymbol(order.getSymbol());
        result.setLeverage(order.getLeverage());
        
        try {
            // 1. 基础杠杆验证
            LeverageValidationResult basicResult = validateBasicLeverage(order);
            if (!basicResult.isValid()) {
                return basicResult;
            }
            
            // 2. 全局杠杆验证（新增）
            LeverageValidationResult globalResult = validateGlobalLeverage(order);
            if (!globalResult.isValid()) {
                return globalResult;
            }
            
            // 3. 获取用户现有持仓
            List<Position> existingPositions = getExistingPositions(order.getUserId(), order.getSymbol());
            
            // 4. 根据杠杆规则验证
            LeverageValidationResult ruleResult = validateLeverageRule(order, existingPositions);
            if (!ruleResult.isValid()) {
                return ruleResult;
            }
            
            // 5. 验证风险限额
            LeverageValidationResult riskResult = validateRiskLimit(order, existingPositions);
            if (!riskResult.isValid()) {
                return riskResult;
            }
            
            log.debug("杠杆验证通过: orderId={}, symbol={}, leverage={}", 
                     order.getOrderId(), order.getSymbol(), order.getLeverage());
            
        } catch (Exception e) {
            log.error("杠杆验证异常: orderId={}, error={}", order.getOrderId(), e.getMessage());
            result.setValid(false);
            result.setErrorCode("LEVERAGE_VALIDATION_ERROR");
            result.setErrorMessage("杠杆验证异常: " + e.getMessage());
        }
        
        return result;
    }
    
    /**
     * 全局杠杆验证
     * 检查用户在整个合约交易中是否只使用一个杠杆
     * 如果有仓位或委托，只允许增加杠杆，不允许降低
     * 
     * @param order 订单
     * @return 验证结果
     */
    private LeverageValidationResult validateGlobalLeverage(Order order) {
        LeverageValidationResult result = new LeverageValidationResult();
        result.setValid(true);
        
        // 获取用户在所有标的上的持仓和委托
        GlobalLeverageInfo globalInfo = getGlobalLeverageInfo(order.getUserId());
        
        // 1. 检查是否已有其他杠杆
        if (globalInfo.isHasExistingLeverage() && !globalInfo.isSameLeverage(order.getLeverage())) {
            result.setValid(false);
            result.setErrorCode("GLOBAL_LEVERAGE_MISMATCH");
            result.setErrorMessage("全局杠杆规则：用户已有杠杆" + globalInfo.getExistingLeverage() + 
                               "，新订单杠杆为" + order.getLeverage() + "，必须保持一致");
            return result;
        }
        
        // 2. 检查是否有仓位或委托时的杠杆调整规则
        if (globalInfo.isHasPositionsOrOrders()) {
            // 有仓位或委托时，只允许增加杠杆
            if (order.getLeverage().compareTo(globalInfo.getExistingLeverage()) < 0) {
                result.setValid(false);
                result.setErrorCode("LEVERAGE_DECREASE_NOT_ALLOWED");
                result.setErrorMessage("有仓位或委托时不允许降低杠杆：当前杠杆" + globalInfo.getExistingLeverage() + 
                                   "，新订单杠杆" + order.getLeverage() + "，只允许增加杠杆");
                return result;
            }
        }
        
        return result;
    }
    
    /**
     * 获取用户全局杠杆信息
     * 
     * @param userId 用户ID
     * @return 全局杠杆信息
     */
    private GlobalLeverageInfo getGlobalLeverageInfo(Long userId) {
        GlobalLeverageInfo info = new GlobalLeverageInfo();
        info.setUserId(userId);
        
        // 获取用户所有持仓
        List<Position> allPositions = getAllUserPositions(userId);
        info.setPositions(allPositions);
        
        // 获取用户所有活跃委托
        List<Order> activeOrders = getActiveUserOrders(userId);
        info.setActiveOrders(activeOrders);
        
        // 分析杠杆信息
        analyzeGlobalLeverage(info);
        
        return info;
    }
    
    /**
     * 分析全局杠杆信息
     * 
     * @param info 全局杠杆信息
     */
    private void analyzeGlobalLeverage(GlobalLeverageInfo info) {
        // 收集所有杠杆
        List<BigDecimal> allLeverages = new java.util.ArrayList<>();
        
        // 从持仓中收集杠杆
        for (Position position : info.getPositions()) {
            if (position.getLeverage() != null && position.getLeverage().compareTo(BigDecimal.ONE) > 0) {
                allLeverages.add(position.getLeverage());
            }
        }
        
        // 从活跃委托中收集杠杆
        for (Order order : info.getActiveOrders()) {
            if (order.getLeverage() != null && order.getLeverage().compareTo(BigDecimal.ONE) > 0) {
                allLeverages.add(order.getLeverage());
            }
        }
        
        // 分析杠杆信息
        if (!allLeverages.isEmpty()) {
            // 检查是否所有杠杆都相同
            BigDecimal firstLeverage = allLeverages.get(0);
            boolean allSame = allLeverages.stream().allMatch(leverage -> leverage.compareTo(firstLeverage) == 0);
            
            if (allSame) {
                info.setExistingLeverage(firstLeverage);
                info.setHasExistingLeverage(true);
            } else {
                // 有不同杠杆，取最大杠杆作为当前杠杆
                BigDecimal maxLeverage = allLeverages.stream().max(BigDecimal::compareTo).orElse(BigDecimal.ONE);
                info.setExistingLeverage(maxLeverage);
                info.setHasExistingLeverage(true);
                info.setHasMultipleLeverages(true);
            }
        } else {
            info.setExistingLeverage(BigDecimal.ONE);
            info.setHasExistingLeverage(false);
        }
        
        // 检查是否有仓位或委托
        info.setHasPositionsOrOrders(!info.getPositions().isEmpty() || !info.getActiveOrders().isEmpty());
    }
    
    /**
     * 获取用户所有持仓
     * 
     * @param userId 用户ID
     * @return 持仓列表
     */
    private List<Position> getAllUserPositions(Long userId) {
        // 这里需要从内存管理器或数据库获取用户所有持仓
        // 暂时返回空列表，实际实现需要根据具体的数据存储方式
        return java.util.Collections.emptyList();
    }
    
    /**
     * 获取用户所有活跃委托
     * 
     * @param userId 用户ID
     * @return 活跃委托列表
     */
    private List<Order> getActiveUserOrders(Long userId) {
        // 这里需要从内存管理器或数据库获取用户所有活跃委托
        // 暂时返回空列表，实际实现需要根据具体的数据存储方式
        return java.util.Collections.emptyList();
    }
    
    /**
     * 基础杠杆验证
     * 
     * @param order 订单
     * @return 验证结果
     */
    private LeverageValidationResult validateBasicLeverage(Order order) {
        LeverageValidationResult result = new LeverageValidationResult();
        result.setValid(true);
        
        // 1. 检查杠杆是否小于最小值
        if (order.getLeverage().compareTo(BigDecimal.ONE) < 0) {
            result.setValid(false);
            result.setErrorCode("LEVERAGE_TOO_LOW");
            result.setErrorMessage("杠杆倍数不能小于1");
            return result;
        }
        
        // 2. 获取最大允许杠杆
        BigDecimal maxLeverage = getMaxLeverage(order);
        if (order.getLeverage().compareTo(maxLeverage) > 0) {
            result.setValid(false);
            result.setErrorCode("LEVERAGE_EXCEEDED");
            result.setErrorMessage("杠杆倍数超过最大允许值: " + maxLeverage);
            return result;
        }
        
        return result;
    }
    
    /**
     * 获取最大允许杠杆
     * 
     * @param order 订单
     * @return 最大杠杆
     */
    private BigDecimal getMaxLeverage(Order order) {
        // 获取风险限额配置
        SymbolRiskLimitConfig riskConfig = symbolRiskLimitConfigManager.getRiskLimitConfig(order.getSymbol());
        
        // 获取当前风险等级
        RiskLevel currentRiskLevel = getCurrentRiskLevel(order);
        
        // 同一个标的全仓逐仓使用同一个风险限额配置
        // 使用逐仓模式的配置作为统一配置，因为逐仓模式通常更保守
        return riskConfig.getIsolatedModeConfig().getMaxLeverage(currentRiskLevel);
    }
    
    /**
     * 检查是否为全仓模式
     * 
     * @param order 订单
     * @return 是否为全仓模式
     */
    private boolean isCrossMode(Order order) {
        // 这里需要根据用户的实际仓位模式判断
        // 暂时返回false，表示逐仓模式
        return false;
    }
    
    /**
     * 验证杠杆规则
     * 
     * @param order 订单
     * @param existingPositions 现有持仓
     * @return 验证结果
     */
    private LeverageValidationResult validateLeverageRule(Order order, List<Position> existingPositions) {
        LeverageValidationResult result = new LeverageValidationResult();
        result.setValid(true);
        
        // 获取杠杆验证规则
        SymbolRiskLimitConfig.LeverageValidationRule rule = getLeverageValidationRule(order);
        
        switch (rule) {
            case UNIFORM:
                return validateUniformLeverage(order, existingPositions);
            case FLEXIBLE:
                return validateFlexibleLeverage(order, existingPositions);
            case INCREASING:
                return validateIncreasingLeverage(order, existingPositions);
            case DECREASING:
                return validateDecreasingLeverage(order, existingPositions);
            default:
                return result;
        }
    }
    
    /**
     * 获取杠杆验证规则
     * 
     * @param order 订单
     * @return 验证规则
     */
    private SymbolRiskLimitConfig.LeverageValidationRule getLeverageValidationRule(Order order) {
        // 根据当前风险等级获取验证规则
        // 这里可以根据用户的风险等级动态获取
        RiskLevel riskLevel = getCurrentRiskLevel(order);
        return symbolRiskLimitConfigManager.getLeverageValidationRule(order.getSymbol(), riskLevel);
    }
    
    /**
     * 获取当前风险等级
     * 撮合系统使用固定的风险等级，不进行动态调整
     * 
     * @param order 订单
     * @return 风险等级
     */
    private RiskLevel getCurrentRiskLevel(Order order) {
        // 撮合系统使用固定的NORMAL风险等级，确保系统稳定可控
        return RiskLevel.NORMAL;
    }
    
    /**
     * 验证统一杠杆规则
     * 同一用户在同一标的上只能使用相同的杠杆倍数
     * 
     * @param order 订单
     * @param existingPositions 现有持仓
     * @return 验证结果
     */
    private LeverageValidationResult validateUniformLeverage(Order order, List<Position> existingPositions) {
        LeverageValidationResult result = new LeverageValidationResult();
        
        if (!existingPositions.isEmpty()) {
            // 检查现有持仓的杠杆是否与订单杠杆一致
            BigDecimal existingLeverage = existingPositions.get(0).getLeverage();
            if (existingLeverage.compareTo(order.getLeverage()) != 0) {
                result.setValid(false);
                result.setErrorCode("LEVERAGE_MISMATCH");
                result.setErrorMessage("统一杠杆规则：现有持仓杠杆为" + existingLeverage + 
                                   "，订单杠杆为" + order.getLeverage() + "，必须保持一致");
                return result;
            }
        }
        
        result.setValid(true);
        return result;
    }
    
    /**
     * 验证灵活杠杆规则
     * 同一用户在同一标的上可以使用不同的杠杆倍数
     * 
     * @param order 订单
     * @param existingPositions 现有持仓
     * @return 验证结果
     */
    private LeverageValidationResult validateFlexibleLeverage(Order order, List<Position> existingPositions) {
        // 灵活杠杆：允许使用不同杠杆，无需特殊验证
        LeverageValidationResult result = new LeverageValidationResult();
        result.setValid(true);
        return result;
    }
    
    /**
     * 验证递增杠杆规则
     * 同一用户在同一标的上只能使用递增的杠杆倍数
     * 
     * @param order 订单
     * @param existingPositions 现有持仓
     * @return 验证结果
     */
    private LeverageValidationResult validateIncreasingLeverage(Order order, List<Position> existingPositions) {
        LeverageValidationResult result = new LeverageValidationResult();
        
        if (!existingPositions.isEmpty()) {
            // 检查订单杠杆是否大于等于现有持仓杠杆
            BigDecimal maxExistingLeverage = existingPositions.stream()
                    .map(Position::getLeverage)
                    .max(BigDecimal::compareTo)
                    .orElse(BigDecimal.ONE);
            
            if (order.getLeverage().compareTo(maxExistingLeverage) < 0) {
                result.setValid(false);
                result.setErrorCode("LEVERAGE_NOT_INCREASING");
                result.setErrorMessage("递增杠杆规则：订单杠杆" + order.getLeverage() + 
                                   "必须大于等于现有最大杠杆" + maxExistingLeverage);
                return result;
            }
        }
        
        result.setValid(true);
        return result;
    }
    
    /**
     * 验证递减杠杆规则
     * 同一用户在同一标的上只能使用递减的杠杆倍数
     * 
     * @param order 订单
     * @param existingPositions 现有持仓
     * @return 验证结果
     */
    private LeverageValidationResult validateDecreasingLeverage(Order order, List<Position> existingPositions) {
        LeverageValidationResult result = new LeverageValidationResult();
        
        if (!existingPositions.isEmpty()) {
            // 检查订单杠杆是否小于等于现有持仓杠杆
            BigDecimal minExistingLeverage = existingPositions.stream()
                    .map(Position::getLeverage)
                    .min(BigDecimal::compareTo)
                    .orElse(BigDecimal.ONE);
            
            if (order.getLeverage().compareTo(minExistingLeverage) > 0) {
                result.setValid(false);
                result.setErrorCode("LEVERAGE_NOT_DECREASING");
                result.setErrorMessage("递减杠杆规则：订单杠杆" + order.getLeverage() + 
                                   "必须小于等于现有最小杠杆" + minExistingLeverage);
                return result;
            }
        }
        
        result.setValid(true);
        return result;
    }
    
    /**
     * 验证风险限额
     * 
     * @param order 订单
     * @param existingPositions 现有持仓
     * @return 验证结果
     */
    private LeverageValidationResult validateRiskLimit(Order order, List<Position> existingPositions) {
        LeverageValidationResult result = new LeverageValidationResult();
        result.setValid(true);
        
        // 计算新的持仓价值
        BigDecimal newPositionValue = order.getQuantity().multiply(order.getPrice());
        
        // 计算现有持仓总价值
        BigDecimal existingPositionValue = existingPositions.stream()
                .map(Position::getPositionValue)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        // 计算总持仓价值
        BigDecimal totalPositionValue = existingPositionValue.add(newPositionValue);
        
        // 根据杠杆计算所需保证金
        BigDecimal requiredMargin = totalPositionValue.divide(order.getLeverage(), 8, BigDecimal.ROUND_HALF_UP);
        
        // 检查保证金是否足够
        // 这里需要根据用户的实际保证金余额进行验证
        // 暂时跳过此验证
        
        return result;
    }
    
    /**
     * 获取用户在该标的上的现有持仓
     * 
     * @param userId 用户ID
     * @param symbol 交易对
     * @return 持仓列表
     */
    private List<Position> getExistingPositions(Long userId, String symbol) {
        // 这里需要从内存管理器或数据库获取用户持仓
        // 暂时返回空列表，实际实现需要根据具体的数据存储方式
        return java.util.Collections.emptyList();
    }
    
    /**
     * 杠杆验证结果
     */
    @lombok.Data
    public static class LeverageValidationResult {
        private boolean valid;
        private String errorCode;
        private String errorMessage;
        private String orderId;
        private Long userId;
        private String symbol;
        private BigDecimal leverage;
    }
    
    /**
     * 全局杠杆信息
     */
    @lombok.Data
    public static class GlobalLeverageInfo {
        private Long userId;
        private List<Position> positions;
        private List<Order> activeOrders;
        private BigDecimal existingLeverage;
        private boolean hasExistingLeverage;
        private boolean hasMultipleLeverages;
        private boolean hasPositionsOrOrders;
        
        public GlobalLeverageInfo() {
            this.positions = new java.util.ArrayList<>();
            this.activeOrders = new java.util.ArrayList<>();
            this.existingLeverage = BigDecimal.ONE;
            this.hasExistingLeverage = false;
            this.hasMultipleLeverages = false;
            this.hasPositionsOrOrders = false;
        }
        
        /**
         * 检查是否与指定杠杆相同
         */
        public boolean isSameLeverage(BigDecimal leverage) {
            return this.existingLeverage.compareTo(leverage) == 0;
        }
    }
} 