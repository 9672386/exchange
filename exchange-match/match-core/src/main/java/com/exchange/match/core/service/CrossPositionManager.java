package com.exchange.match.core.service;

import com.exchange.match.core.model.*;
import com.exchange.match.core.memory.MemoryManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 全仓仓位管理器
 * 负责管理全仓模式下的仓位和风险计算
 */
@Slf4j
@Service
public class CrossPositionManager {
    
    @Autowired
    private MemoryManager memoryManager;
    
    /**
     * 计算用户全仓风险率
     * 
     * @param userId 用户ID
     * @param totalMargin 总保证金
     * @return 全仓风险率
     */
    public BigDecimal calculateCrossRiskRatio(Long userId, BigDecimal totalMargin) {
        // 获取用户所有全仓模式的持仓
        // 注意：这里需要遍历所有交易对来获取用户的全仓持仓
        // 简化实现，实际需要从数据库或缓存中获取用户的所有持仓
        log.info("计算用户{}的全仓风险率，总保证金: {}", userId, totalMargin);
        
        // 这里应该从用户持仓数据中获取全仓持仓
        // 由于当前MemoryManager没有按用户ID获取所有持仓的方法，
        // 这里先返回一个默认值，实际实现需要扩展MemoryManager
        return BigDecimal.ZERO;
    }
    
    /**
     * 获取用户全仓持仓列表
     * 
     * @param userId 用户ID
     * @return 全仓持仓列表
     */
    public List<Position> getCrossPositions(Long userId) {
        // 简化实现，实际需要从数据库或缓存中获取用户的所有持仓
        log.info("获取用户{}的全仓持仓列表", userId);
        return new java.util.ArrayList<>();
    }
    
    /**
     * 获取用户逐仓持仓列表
     * 
     * @param userId 用户ID
     * @return 逐仓持仓列表
     */
    public List<Position> getIsolatedPositions(Long userId) {
        // 简化实现，实际需要从数据库或缓存中获取用户的所有持仓
        log.info("获取用户{}的逐仓持仓列表", userId);
        return new java.util.ArrayList<>();
    }
    
    /**
     * 计算全仓模式下的强平优先级
     * 
     * @param userId 用户ID
     * @return 按强平优先级排序的持仓列表
     */
    public List<Position> getCrossPositionsByLiquidationPriority(Long userId) {
        List<Position> crossPositions = getCrossPositions(userId);
        
        // 按未实现盈亏排序，亏损最多的优先强平
        return crossPositions.stream()
                .filter(p -> p.getQuantity().compareTo(BigDecimal.ZERO) > 0)
                .sorted((p1, p2) -> {
                    BigDecimal pnl1 = p1.getUnrealizedPnl() != null ? p1.getUnrealizedPnl() : BigDecimal.ZERO;
                    BigDecimal pnl2 = p2.getUnrealizedPnl() != null ? p2.getUnrealizedPnl() : BigDecimal.ZERO;
                    return pnl1.compareTo(pnl2); // 亏损最多的排在前面
                })
                .collect(Collectors.toList());
    }
    
    /**
     * 执行全仓强平
     * 
     * @param userId 用户ID
     * @param targetRiskRatio 目标风险率
     * @param totalMargin 总保证金
     * @return 强平结果
     */
    public CrossLiquidationResult executeCrossLiquidation(Long userId, BigDecimal targetRiskRatio, BigDecimal totalMargin) {
        log.info("开始执行全仓强平，用户: {}, 目标风险率: {}, 总保证金: {}", userId, targetRiskRatio, totalMargin);
        
        CrossLiquidationResult result = new CrossLiquidationResult();
        result.setUserId(userId);
        result.setStartTime(System.currentTimeMillis());
        
        // 获取按优先级排序的全仓持仓
        List<Position> crossPositions = getCrossPositionsByLiquidationPriority(userId);
        
        if (crossPositions.isEmpty()) {
            log.info("用户{}没有全仓持仓，跳过高仓强平", userId);
            result.setErrorMessage("没有全仓持仓");
            result.setEndTime(System.currentTimeMillis());
            return result;
        }
        
        BigDecimal currentRiskRatio = calculateCrossRiskRatio(userId, totalMargin);
        log.info("当前全仓风险率: {}", currentRiskRatio);
        
        // 如果当前风险率已经低于目标风险率，不需要强平
        if (currentRiskRatio.compareTo(targetRiskRatio) <= 0) {
            log.info("当前风险率{}已低于目标风险率{}，无需强平", currentRiskRatio, targetRiskRatio);
            result.setFinalRiskRatio(currentRiskRatio);
            result.setEndTime(System.currentTimeMillis());
            return result;
        }
        
        // 逐个强平持仓，直到达到目标风险率
        for (Position position : crossPositions) {
            if (currentRiskRatio.compareTo(targetRiskRatio) <= 0) {
                break; // 已达到目标风险率
            }
            
            log.info("强平持仓: {}, 当前风险率: {}, 目标风险率: {}", 
                    position.getSymbol(), currentRiskRatio, targetRiskRatio);
            
            // 执行单个持仓的强平
            CrossLiquidationStep step = executeSingleCrossLiquidation(position, currentRiskRatio, targetRiskRatio, totalMargin);
            result.addStep(step);
            
            // 重新计算风险率
            currentRiskRatio = calculateCrossRiskRatio(userId, totalMargin);
            log.info("强平后风险率: {}", currentRiskRatio);
        }
        
        result.setFinalRiskRatio(currentRiskRatio);
        result.setEndTime(System.currentTimeMillis());
        
        log.info("全仓强平完成，用户: {}, 最终风险率: {}, 执行步骤数: {}", 
                userId, currentRiskRatio, result.getSteps().size());
        
        return result;
    }
    
    /**
     * 执行单个全仓持仓的强平
     */
    private CrossLiquidationStep executeSingleCrossLiquidation(Position position, BigDecimal currentRiskRatio, 
                                                              BigDecimal targetRiskRatio, BigDecimal totalMargin) {
        CrossLiquidationStep step = new CrossLiquidationStep();
        step.setSymbol(position.getSymbol());
        step.setStartTime(System.currentTimeMillis());
        
        // 计算需要强平的数量
        BigDecimal liquidationQuantity = calculateCrossLiquidationQuantity(position, currentRiskRatio, targetRiskRatio, totalMargin);
        
        log.info("全仓强平持仓: {}, 强平数量: {}", position.getSymbol(), liquidationQuantity);
        
        // 执行强平（这里需要调用撮合引擎的强平逻辑）
        // 简化实现，实际需要调用撮合引擎
        step.setLiquidationQuantity(liquidationQuantity);
        step.setSuccessQuantity(liquidationQuantity); // 假设全部成功
        step.setAveragePrice(position.getAveragePrice());
        step.setTotalAmount(liquidationQuantity.multiply(position.getAveragePrice()));
        
        step.setEndTime(System.currentTimeMillis());
        
        return step;
    }
    
    /**
     * 计算全仓强平数量
     */
    private BigDecimal calculateCrossLiquidationQuantity(Position position, BigDecimal currentRiskRatio, 
                                                       BigDecimal targetRiskRatio, BigDecimal totalMargin) {
        // 简化计算：强平50%的持仓
        BigDecimal liquidationRatio = BigDecimal.valueOf(0.5);
        return position.getQuantity().multiply(liquidationRatio);
    }
    
    /**
     * 全仓强平结果
     */
    @lombok.Data
    public static class CrossLiquidationResult {
        private Long userId;
        private BigDecimal finalRiskRatio;
        private String errorMessage;
        private long startTime;
        private long endTime;
        private List<CrossLiquidationStep> steps = new java.util.ArrayList<>();
        
        public void addStep(CrossLiquidationStep step) {
            steps.add(step);
        }
        
        public long getDuration() {
            return endTime - startTime;
        }
        
        public BigDecimal getTotalLiquidationQuantity() {
            return steps.stream()
                    .map(CrossLiquidationStep::getSuccessQuantity)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
        }
        
        public BigDecimal getTotalLiquidationAmount() {
            return steps.stream()
                    .map(CrossLiquidationStep::getTotalAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
        }
    }
    
    /**
     * 全仓强平步骤
     */
    @lombok.Data
    public static class CrossLiquidationStep {
        private String symbol;
        private BigDecimal liquidationQuantity;
        private BigDecimal successQuantity;
        private BigDecimal averagePrice;
        private BigDecimal totalAmount;
        private long startTime;
        private long endTime;
        
        public long getDuration() {
            return endTime - startTime;
        }
    }
} 