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

/**
 * 杠杆调整服务
 * 处理杠杆调整时的仓位和委托同步
 */
@Slf4j
@Service
public class LeverageAdjustmentService {
    
    @Autowired
    private MemoryManager memoryManager;
    
    @Autowired
    private CrossPositionManager crossPositionManager;
    
    @Autowired
    private SymbolRiskLimitConfigManager symbolRiskLimitConfigManager;
    
    /**
     * 调整用户杠杆
     * 同步调整所有仓位和活跃委托的杠杆
     * 
     * @param userId 用户ID
     * @param newLeverage 新杠杆
     * @return 调整结果
     */
    public LeverageAdjustmentResult adjustUserLeverage(Long userId, BigDecimal newLeverage) {
        LeverageAdjustmentResult result = new LeverageAdjustmentResult();
        result.setUserId(userId);
        result.setNewLeverage(newLeverage);
        result.setSuccess(true);
        
        try {
            log.info("开始调整用户杠杆: userId={}, newLeverage={}", userId, newLeverage);
            
            // 1. 验证新杠杆的有效性
            LeverageAdjustmentResult validationResult = validateNewLeverage(userId, newLeverage);
            if (!validationResult.isSuccess()) {
                return validationResult;
            }
            
            // 2. 获取用户所有持仓
            List<Position> allPositions = getAllUserPositions(userId);
            result.setPositionsCount(allPositions.size());
            
            // 3. 获取用户所有活跃委托
            List<Order> activeOrders = getActiveUserOrders(userId);
            result.setOrdersCount(activeOrders.size());
            
            // 4. 调整所有持仓的杠杆
            for (Position position : allPositions) {
                adjustPositionLeverage(position, newLeverage);
            }
            
            // 5. 调整所有活跃委托的杠杆
            for (Order order : activeOrders) {
                adjustOrderLeverage(order, newLeverage);
            }
            
            // 6. 更新用户全局杠杆设置
            updateUserGlobalLeverage(userId, newLeverage);
            
            log.info("用户杠杆调整完成: userId={}, newLeverage={}, positions={}, orders={}", 
                     userId, newLeverage, allPositions.size(), activeOrders.size());
            
        } catch (Exception e) {
            log.error("调整用户杠杆失败: userId={}, newLeverage={}, error={}", 
                      userId, newLeverage, e.getMessage());
            result.setSuccess(false);
            result.setErrorCode("LEVERAGE_ADJUSTMENT_ERROR");
            result.setErrorMessage("调整杠杆失败: " + e.getMessage());
        }
        
        return result;
    }
    
    /**
     * 验证新杠杆的有效性
     * 
     * @param userId 用户ID
     * @param newLeverage 新杠杆
     * @return 验证结果
     */
    private LeverageAdjustmentResult validateNewLeverage(Long userId, BigDecimal newLeverage) {
        LeverageAdjustmentResult result = new LeverageAdjustmentResult();
        result.setUserId(userId);
        result.setNewLeverage(newLeverage);
        
        // 1. 检查杠杆是否小于最小值
        if (newLeverage.compareTo(BigDecimal.ONE) < 0) {
            result.setSuccess(false);
            result.setErrorCode("LEVERAGE_TOO_LOW");
            result.setErrorMessage("杠杆倍数不能小于1");
            return result;
        }
        
        // 2. 检查杠杆是否超过最大值
        // 这里需要根据具体的交易对和仓位模式获取最大杠杆
        // 暂时使用默认值，实际实现需要根据具体情况获取
        BigDecimal maxLeverage = getMaxLeverage("BTCUSDT", false); // 默认使用BTCUSDT和逐仓模式
        if (newLeverage.compareTo(maxLeverage) > 0) {
            result.setSuccess(false);
            result.setErrorCode("LEVERAGE_EXCEEDED");
            result.setErrorMessage("杠杆倍数超过最大允许值: " + maxLeverage);
            return result;
        }
        
        // 3. 检查是否有仓位或委托时的限制
        List<Position> positions = getAllUserPositions(userId);
        List<Order> orders = getActiveUserOrders(userId);
        
        if (!positions.isEmpty() || !orders.isEmpty()) {
            // 有仓位或委托时，只允许增加杠杆
            BigDecimal currentLeverage = getCurrentUserLeverage(userId);
            if (newLeverage.compareTo(currentLeverage) < 0) {
                result.setSuccess(false);
                result.setErrorCode("LEVERAGE_DECREASE_NOT_ALLOWED");
                result.setErrorMessage("有仓位或委托时不允许降低杠杆：当前杠杆" + currentLeverage + 
                                   "，新杠杆" + newLeverage + "，只允许增加杠杆");
                return result;
            }
        }
        
        result.setSuccess(true);
        return result;
    }
    
    /**
     * 调整持仓杠杆
     * 
     * @param position 持仓
     * @param newLeverage 新杠杆
     */
    private void adjustPositionLeverage(Position position, BigDecimal newLeverage) {
        BigDecimal oldLeverage = position.getLeverage();
        position.setLeverage(newLeverage);
        
        // 重新计算保证金
        recalculatePositionMargin(position);
        
        // 重新计算强平价格
        recalculateLiquidationPrice(position);
        
        log.debug("调整持仓杠杆: positionId={}, symbol={}, oldLeverage={}, newLeverage={}", 
                 position.getUserId(), position.getSymbol(), oldLeverage, newLeverage);
    }
    
    /**
     * 调整委托杠杆
     * 
     * @param order 委托
     * @param newLeverage 新杠杆
     */
    private void adjustOrderLeverage(Order order, BigDecimal newLeverage) {
        BigDecimal oldLeverage = order.getLeverage();
        order.setLeverage(newLeverage);
        
        log.debug("调整委托杠杆: orderId={}, symbol={}, oldLeverage={}, newLeverage={}", 
                 order.getOrderId(), order.getSymbol(), oldLeverage, newLeverage);
    }
    
    /**
     * 重新计算持仓保证金
     * 
     * @param position 持仓
     */
    private void recalculatePositionMargin(Position position) {
        // 根据新杠杆重新计算所需保证金
        BigDecimal positionValue = position.getPositionValue();
        BigDecimal requiredMargin = positionValue.divide(position.getLeverage(), 8, BigDecimal.ROUND_HALF_UP);
        
        // 更新保证金
        position.setMargin(requiredMargin);
        
        log.debug("重新计算持仓保证金: positionId={}, symbol={}, leverage={}, requiredMargin={}", 
                 position.getUserId(), position.getSymbol(), position.getLeverage(), requiredMargin);
    }
    
    /**
     * 重新计算强平价格
     * 
     * @param position 持仓
     */
    private void recalculateLiquidationPrice(Position position) {
        // 根据新杠杆重新计算强平价格
        // 这里需要根据具体的强平价格计算逻辑实现
        // 暂时跳过具体实现
        
        log.debug("重新计算强平价格: positionId={}, symbol={}, leverage={}", 
                 position.getUserId(), position.getSymbol(), position.getLeverage());
    }
    
    /**
     * 更新用户全局杠杆设置
     * 
     * @param userId 用户ID
     * @param newLeverage 新杠杆
     */
    private void updateUserGlobalLeverage(Long userId, BigDecimal newLeverage) {
        // 更新用户全局杠杆设置
        // 这里需要根据具体的数据存储方式实现
        // 暂时跳过具体实现
        
        log.debug("更新用户全局杠杆设置: userId={}, newLeverage={}", userId, newLeverage);
    }
    
    /**
     * 获取最大允许杠杆
     * 
     * @param symbol 交易对
     * @param isCrossMode 是否为全仓模式（已废弃，保留参数兼容性）
     * @return 最大杠杆
     */
    private BigDecimal getMaxLeverage(String symbol, boolean isCrossMode) {
        // 获取风险限额配置
        SymbolRiskLimitConfig riskConfig = symbolRiskLimitConfigManager.getRiskLimitConfig(symbol);
        
        // 撮合系统使用固定的NORMAL风险等级，确保系统稳定可控
        RiskLevel currentRiskLevel = RiskLevel.NORMAL;
        
        // 同一个标的全仓逐仓使用同一个风险限额配置
        // 使用逐仓模式的配置作为统一配置，因为逐仓模式通常更保守
        return riskConfig.getIsolatedModeConfig().getMaxLeverage(currentRiskLevel);
    }
    
    /**
     * 获取用户当前杠杆
     * 
     * @param userId 用户ID
     * @return 当前杠杆
     */
    private BigDecimal getCurrentUserLeverage(Long userId) {
        // 获取用户所有持仓和委托的杠杆
        List<Position> positions = getAllUserPositions(userId);
        List<Order> orders = getActiveUserOrders(userId);
        
        // 收集所有杠杆
        List<BigDecimal> allLeverages = new java.util.ArrayList<>();
        
        for (Position position : positions) {
            if (position.getLeverage() != null && position.getLeverage().compareTo(BigDecimal.ONE) > 0) {
                allLeverages.add(position.getLeverage());
            }
        }
        
        for (Order order : orders) {
            if (order.getLeverage() != null && order.getLeverage().compareTo(BigDecimal.ONE) > 0) {
                allLeverages.add(order.getLeverage());
            }
        }
        
        if (!allLeverages.isEmpty()) {
            // 返回最大杠杆作为当前杠杆
            return allLeverages.stream().max(BigDecimal::compareTo).orElse(BigDecimal.ONE);
        }
        
        return BigDecimal.ONE;
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
     * 杠杆调整结果
     */
    @lombok.Data
    public static class LeverageAdjustmentResult {
        private Long userId;
        private BigDecimal newLeverage;
        private boolean success;
        private String errorCode;
        private String errorMessage;
        private int positionsCount;
        private int ordersCount;
        
        public LeverageAdjustmentResult() {
            this.positionsCount = 0;
            this.ordersCount = 0;
        }
    }
} 