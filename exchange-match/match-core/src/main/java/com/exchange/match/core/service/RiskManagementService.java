package com.exchange.match.core.service;

import com.exchange.match.core.model.*;
import com.exchange.match.core.memory.MemoryManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 撮合引擎风险管理服务
 * 负责执行风险降档和ADL策略，不进行风险率计算
 * 风险率计算和强平触发由风控服务负责
 */
@Slf4j
@Service
public class RiskManagementService {
    
    @Autowired
    private MemoryManager memoryManager;
    
    @Autowired
    private RiskRecalculationService riskRecalculationService;
    
    @Autowired
    private CrossPositionManager crossPositionManager;
    
    @Autowired
    private SymbolRiskLimitConfigManager symbolRiskLimitConfigManager;
    
    /**
     * 执行风险降档操作
     * 由风控服务调用，撮合引擎只负责执行
     */
    public void executeRiskReduction(Long userId, String symbol, RiskLevel riskLevel) {
        log.info("执行风险降档，用户: {}, 交易对: {}, 风险等级: {}", userId, symbol, riskLevel.getName());
        
        // 获取减仓比例
        BigDecimal reductionRatio = riskLevel.getReductionRatio();
        
        // 执行减仓
        executePositionReduction(userId, symbol, reductionRatio, riskLevel);
    }
    
    /**
     * 执行分档平仓操作
     * 从当前风险档位逐步降到安全档位
     * 
     * @param userId 用户ID
     * @param symbol 交易对
     * @param liquidationRequest 强平请求（包含风控服务传递的数据）
     * @return 分档平仓结果
     */
    public TieredLiquidationResult executeTieredLiquidation(Long userId, String symbol, LiquidationRequest liquidationRequest) {
        log.info("开始执行分档平仓，用户: {}, 交易对: {}", userId, symbol);
        
        TieredLiquidationResult result = new TieredLiquidationResult();
        result.setUserId(userId);
        result.setSymbol(symbol);
        result.setStartTime(System.currentTimeMillis());
        
        // 获取当前持仓
        Position position = memoryManager.getPosition(userId, symbol);
        if (position == null || position.getQuantity().compareTo(BigDecimal.ZERO) <= 0) {
            log.info("用户{}在{}上无持仓，跳过分档平仓", userId, symbol);
            result.setErrorMessage("用户无持仓");
            result.setEndTime(System.currentTimeMillis());
            return result;
        }
        
        // 根据仓位模式采用不同的强平策略
        if (position.isCrossMode()) {
            log.info("检测到全仓模式，执行全仓强平策略");
            return executeCrossTieredLiquidation(userId, symbol, liquidationRequest);
        } else {
            log.info("检测到逐仓模式，执行逐仓强平策略");
            return executeIsolatedTieredLiquidation(userId, symbol, liquidationRequest);
        }
    }
    
    /**
     * 执行逐仓分档平仓
     */
    private TieredLiquidationResult executeIsolatedTieredLiquidation(Long userId, String symbol, LiquidationRequest liquidationRequest) {
        TieredLiquidationResult result = new TieredLiquidationResult();
        result.setUserId(userId);
        result.setSymbol(symbol);
        result.setStartTime(System.currentTimeMillis());
        
        // 获取当前持仓
        Position position = memoryManager.getPosition(userId, symbol);
        if (position == null || position.getQuantity().compareTo(BigDecimal.ZERO) <= 0) {
            log.info("用户{}在{}上无持仓，跳过逐仓分档平仓", userId, symbol);
            result.setErrorMessage("用户无持仓");
            result.setEndTime(System.currentTimeMillis());
            return result;
        }
        
        // 获取当前风险等级
        RiskLevel currentRiskLevel = liquidationRequest.getRiskLevel();
        if (currentRiskLevel == null) {
            // 如果没有传入风险等级，重新计算
            RiskRecalculationService.RiskRecalculationResult riskResult = 
                riskRecalculationService.recalculateRisk(liquidationRequest);
            currentRiskLevel = riskResult.getRiskLevel();
        }
        
        log.info("当前风险等级: {}, 开始逐仓分档平仓", currentRiskLevel.getName());
        
        // 从当前档位开始，逐步降到安全档位
        RiskLevel targetRiskLevel = getTargetRiskLevel(currentRiskLevel);
        RiskLevel currentLevel = currentRiskLevel;
        
        while (currentLevel.getPriority() > targetRiskLevel.getPriority()) {
            log.info("执行从{}档降到{}档的逐仓平仓操作", currentLevel.getName(), getNextLowerLevel(currentLevel).getName());
            
            // 执行当前档位的平仓
            TieredLiquidationStep step = executeSingleTierLiquidation(userId, symbol, currentLevel, liquidationRequest);
            result.addStep(step);
            
            // 检查是否还有持仓
            position = memoryManager.getPosition(userId, symbol);
            if (position == null || position.getQuantity().compareTo(BigDecimal.ZERO) <= 0) {
                log.info("逐仓分档平仓后用户无持仓，结束分档平仓");
                break;
            }
            
            // 重新计算风险率
            RiskRecalculationService.RiskRecalculationResult riskResult = 
                riskRecalculationService.recalculateRisk(liquidationRequest);
            
            log.info("重新计算风险率: {}, 风险等级: {}", riskResult.getRiskRatio(), riskResult.getRiskLevel().getName());
            
            // 如果风险率已经降到安全水平，结束分档平仓
            if (!riskResult.isNeedLiquidation()) {
                log.info("风险率已降到安全水平，结束逐仓分档平仓");
                break;
            }
            
            // 更新当前风险等级
            currentLevel = riskResult.getRiskLevel();
            
            // 如果已经降到最低档位，结束分档平仓
            if (currentLevel.getPriority() <= targetRiskLevel.getPriority()) {
                log.info("已降到目标档位，结束逐仓分档平仓");
                break;
            }
        }
        
        result.setEndTime(System.currentTimeMillis());
        result.setFinalRiskLevel(currentLevel);
        
        log.info("逐仓分档平仓完成，用户: {}, 交易对: {}, 最终风险等级: {}, 执行步骤数: {}", 
                userId, symbol, currentLevel.getName(), result.getSteps().size());
        
        return result;
    }
    
    /**
     * 执行全仓分档平仓
     */
    private TieredLiquidationResult executeCrossTieredLiquidation(Long userId, String symbol, LiquidationRequest liquidationRequest) {
        TieredLiquidationResult result = new TieredLiquidationResult();
        result.setUserId(userId);
        result.setSymbol(symbol);
        result.setStartTime(System.currentTimeMillis());
        
        log.info("开始执行全仓分档平仓，用户: {}, 交易对: {}", userId, symbol);
        
        // 获取总保证金
        BigDecimal totalMargin = liquidationRequest.getMargin();
        if (totalMargin == null || totalMargin.compareTo(BigDecimal.ZERO) <= 0) {
            totalMargin = liquidationRequest.getBalance();
        }
        
        // 计算当前全仓风险率
        BigDecimal currentRiskRatio = crossPositionManager.calculateCrossRiskRatio(userId, totalMargin);
        
        // 获取目标风险率（安全水平）
        BigDecimal targetRiskRatio = BigDecimal.valueOf(0.8); // 80%以下为安全
        
        log.info("全仓风险率: {}, 目标风险率: {}", currentRiskRatio, targetRiskRatio);
        
        // 如果当前风险率已经低于目标风险率，不需要强平
        if (currentRiskRatio.compareTo(targetRiskRatio) <= 0) {
            log.info("全仓风险率{}已低于目标风险率{}，无需强平", currentRiskRatio, targetRiskRatio);
            result.setErrorMessage("风险率已安全");
            result.setEndTime(System.currentTimeMillis());
            return result;
        }
        
        // 执行全仓强平
        CrossPositionManager.CrossLiquidationResult crossResult = 
            crossPositionManager.executeCrossLiquidation(userId, targetRiskRatio, totalMargin);
        
        // 转换结果格式
        result.setEndTime(System.currentTimeMillis());
        result.setFinalRiskLevel(RiskLevel.getRiskLevel(crossResult.getFinalRiskRatio()));
        
        // 转换步骤
        for (CrossPositionManager.CrossLiquidationStep crossStep : crossResult.getSteps()) {
            TieredLiquidationStep step = new TieredLiquidationStep();
            step.setRiskLevel(RiskLevel.EMERGENCY); // 全仓强平通常为紧急等级
            step.setSuccessQuantity(crossStep.getSuccessQuantity());
            step.setTotalAmount(crossStep.getTotalAmount());
            step.setAveragePrice(crossStep.getAveragePrice());
            step.setTradeCount(1);
            step.setStartTime(crossStep.getStartTime());
            step.setEndTime(crossStep.getEndTime());
            result.addStep(step);
        }
        
        log.info("全仓分档平仓完成，用户: {}, 交易对: {}, 最终风险率: {}, 执行步骤数: {}", 
                userId, symbol, crossResult.getFinalRiskRatio(), result.getSteps().size());
        
        return result;
    }
    
    /**
     * 执行单个档位的平仓操作
     */
    private TieredLiquidationStep executeSingleTierLiquidation(Long userId, String symbol, RiskLevel riskLevel, LiquidationRequest liquidationRequest) {
        TieredLiquidationStep step = new TieredLiquidationStep();
        step.setRiskLevel(riskLevel);
        step.setStartTime(System.currentTimeMillis());
        
        log.info("执行{}档平仓，用户: {}, 交易对: {}", riskLevel.getName(), userId, symbol);
        
        // 获取当前持仓
        Position position = memoryManager.getPosition(userId, symbol);
        if (position == null || position.getQuantity().compareTo(BigDecimal.ZERO) <= 0) {
            step.setErrorMessage("无持仓可平");
            step.setEndTime(System.currentTimeMillis());
            return step;
        }
        
        // 计算当前档位的平仓数量
        BigDecimal liquidationRatio = getLiquidationRatioForTier(riskLevel, liquidationRequest);
        BigDecimal liquidationQuantity = position.getQuantity().multiply(liquidationRatio);
        
        log.info("{}档平仓数量: {}, 平仓比例: {}", riskLevel.getName(), liquidationQuantity, liquidationRatio);
        
        // 确定平仓方向
        OrderSide liquidationSide = position.getSide() == PositionSide.LONG ? OrderSide.SELL : OrderSide.BUY;
        
        // 创建平仓订单
        Order liquidationOrder = createTieredLiquidationOrder(userId, symbol, liquidationQuantity, liquidationSide, riskLevel);
        
        // 执行平仓撮合
        List<Trade> trades = executeTieredLiquidationMatching(liquidationOrder);
        
        // 更新持仓
        updatePositionAfterTieredLiquidation(position, trades);
        
        // 计算平仓结果
        calculateTieredLiquidationResult(step, trades, position);
        
        step.setEndTime(System.currentTimeMillis());
        
        log.info("{}档平仓完成，成交数量: {}, 成交金额: {}", 
                riskLevel.getName(), step.getSuccessQuantity(), step.getTotalAmount());
        
        return step;
    }
    
    /**
     * 获取目标风险等级（安全档位）
     */
    private RiskLevel getTargetRiskLevel(RiskLevel currentLevel) {
        // 根据当前档位确定目标档位
        switch (currentLevel) {
            case LIQUIDATION: // 第五档 -> 降到第四档
                return RiskLevel.EMERGENCY;
            case EMERGENCY: // 第四档 -> 降到第三档
                return RiskLevel.DANGER;
            case DANGER: // 第三档 -> 降到第二档
                return RiskLevel.WARNING;
            case WARNING: // 第二档 -> 降到第一档
                return RiskLevel.NORMAL;
            default:
                return RiskLevel.NORMAL;
        }
    }
    
    /**
     * 获取下一个较低档位
     */
    private RiskLevel getNextLowerLevel(RiskLevel currentLevel) {
        switch (currentLevel) {
            case LIQUIDATION:
                return RiskLevel.EMERGENCY;
            case EMERGENCY:
                return RiskLevel.DANGER;
            case DANGER:
                return RiskLevel.WARNING;
            case WARNING:
                return RiskLevel.NORMAL;
            default:
                return RiskLevel.NORMAL;
        }
    }
    
    /**
     * 获取档位平仓比例
     */
    private BigDecimal getLiquidationRatioForTier(RiskLevel riskLevel, LiquidationRequest liquidationRequest) {
        String symbol = liquidationRequest.getSymbol();
        
        // 获取当前持仓以确定仓位模式
        Position position = memoryManager.getPosition(liquidationRequest.getUserId(), symbol);
        if (position == null) {
            // 如果无法获取持仓，使用默认配置
            return getDefaultLiquidationRatio(riskLevel);
        }
        
        // 根据仓位模式获取相应的强平比例
        BigDecimal liquidationRatio;
        if (position.isIsolatedMode()) {
            liquidationRatio = symbolRiskLimitConfigManager.getIsolatedTieredLiquidationRatio(symbol, riskLevel);
        } else {
            liquidationRatio = symbolRiskLimitConfigManager.getCrossTieredLiquidationRatio(symbol, riskLevel);
        }
        
        log.debug("获取档位平仓比例: symbol={}, riskLevel={}, positionMode={}, ratio={}", 
                symbol, riskLevel, position.isIsolatedMode() ? "ISOLATED" : "CROSS", liquidationRatio);
        
        return liquidationRatio;
    }
    
    /**
     * 获取默认强平比例
     */
    private BigDecimal getDefaultLiquidationRatio(RiskLevel riskLevel) {
        switch (riskLevel) {
            case LIQUIDATION: return BigDecimal.valueOf(0.5);
            case EMERGENCY: return BigDecimal.valueOf(0.4);
            case DANGER: return BigDecimal.valueOf(0.3);
            case WARNING: return BigDecimal.valueOf(0.2);
            default: return BigDecimal.valueOf(0.1);
        }
    }
    
    /**
     * 检查是否启用分档平仓
     */
    private boolean isTieredLiquidationEnabled(Position position) {
        String symbol = position.getSymbol();
        
        // 根据仓位模式检查是否启用分档平仓
        if (position.isIsolatedMode()) {
            return symbolRiskLimitConfigManager.isIsolatedTieredLiquidationEnabled(symbol);
        } else {
            return symbolRiskLimitConfigManager.isCrossTieredLiquidationEnabled(symbol);
        }
    }
    
    /**
     * 获取最大分档步骤数
     */
    private int getMaxTieredSteps(Position position) {
        String symbol = position.getSymbol();
        
        // 根据仓位模式获取最大步骤数
        if (position.isIsolatedMode()) {
            return symbolRiskLimitConfigManager.getIsolatedMaxTieredSteps(symbol);
        } else {
            return symbolRiskLimitConfigManager.getCrossMaxTieredSteps(symbol);
        }
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
     * 创建分档平仓订单
     */
    private Order createTieredLiquidationOrder(Long userId, String symbol, BigDecimal quantity, OrderSide side, RiskLevel riskLevel) {
        Order order = new Order();
        order.setOrderId("TIER_" + riskLevel.name() + "_" + System.currentTimeMillis() + "_" + UUID.randomUUID().toString().substring(0, 8));
        order.setUserId(userId);
        order.setSymbol(symbol);
        order.setSide(side);
        order.setType(OrderType.MARKET); // 市价平仓
        order.setQuantity(quantity);
        order.setPositionAction(PositionAction.CLOSE);
        order.setClientOrderId("TIERED_LIQUIDATION");
        order.setRemark("分档平仓 - " + riskLevel.getName() + "档");
        
        return order;
    }
    
    /**
     * 执行分档平仓撮合
     */
    private List<Trade> executeTieredLiquidationMatching(Order order) {
        // 获取订单薄
        OrderBook orderBook = memoryManager.getOrCreateOrderBook(order.getSymbol());
        
        List<Trade> trades = new ArrayList<>();
        BigDecimal remainingQuantity = order.getQuantity();
        
        if (order.getSide() == OrderSide.BUY) {
            // 买入平仓：从卖单薄撮合
            for (List<Order> sellOrders : orderBook.getSellOrders().values()) {
                for (Order sellOrder : sellOrders) {
                    if (remainingQuantity.compareTo(BigDecimal.ZERO) <= 0) {
                        break;
                    }
                    
                    BigDecimal tradeQuantity = remainingQuantity.min(sellOrder.getRemainingQuantity());
                    BigDecimal tradePrice = sellOrder.getPrice();
                    
                    Trade trade = createTrade(order, sellOrder, tradeQuantity, tradePrice);
                    trades.add(trade);
                    
                    remainingQuantity = remainingQuantity.subtract(tradeQuantity);
                }
            }
        } else {
            // 卖出平仓：从买单薄撮合
            for (List<Order> buyOrders : orderBook.getBuyOrders().values()) {
                for (Order buyOrder : buyOrders) {
                    if (remainingQuantity.compareTo(BigDecimal.ZERO) <= 0) {
                        break;
                    }
                    
                    BigDecimal tradeQuantity = remainingQuantity.min(buyOrder.getRemainingQuantity());
                    BigDecimal tradePrice = buyOrder.getPrice();
                    
                    Trade trade = createTrade(buyOrder, order, tradeQuantity, tradePrice);
                    trades.add(trade);
                    
                    remainingQuantity = remainingQuantity.subtract(tradeQuantity);
                }
            }
        }
        
        return trades;
    }
    
    /**
     * 更新分档平仓后的持仓
     */
    private void updatePositionAfterTieredLiquidation(Position position, List<Trade> trades) {
        if (trades.isEmpty()) {
            return;
        }
        
        BigDecimal reducedQuantity = trades.stream()
                .map(Trade::getQuantity)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        // 更新持仓数量
        position.setQuantity(position.getQuantity().subtract(reducedQuantity));
        
        // 如果持仓为零，清空持仓
        if (position.getQuantity().compareTo(BigDecimal.ZERO) <= 0) {
            position.setQuantity(BigDecimal.ZERO);
            position.setSide(null);
            position.setAveragePrice(BigDecimal.ZERO);
            position.setUnrealizedPnl(BigDecimal.ZERO);
        }
        
        // 更新到内存管理器
        memoryManager.updatePosition(position);
    }
    
    /**
     * 计算分档平仓结果
     */
    private void calculateTieredLiquidationResult(TieredLiquidationStep step, List<Trade> trades, Position position) {
        if (trades.isEmpty()) {
            step.setSuccessQuantity(BigDecimal.ZERO);
            step.setTotalAmount(BigDecimal.ZERO);
            step.setAveragePrice(BigDecimal.ZERO);
            return;
        }
        
        BigDecimal totalQuantity = trades.stream()
                .map(Trade::getQuantity)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        BigDecimal totalAmount = trades.stream()
                .map(Trade::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        BigDecimal averagePrice = totalAmount.divide(totalQuantity, 8, BigDecimal.ROUND_HALF_UP);
        
        step.setSuccessQuantity(totalQuantity);
        step.setTotalAmount(totalAmount);
        step.setAveragePrice(averagePrice);
        step.setTradeCount(trades.size());
    }
    
    /**
     * 执行持仓减仓
     */
    private void executePositionReduction(Long userId, String symbol, BigDecimal reductionRatio, RiskLevel riskLevel) {
        Position position = memoryManager.getPosition(userId, symbol);
        if (position == null || position.getQuantity().compareTo(BigDecimal.ZERO) <= 0) {
            log.info("用户{}在{}上无持仓，跳过减仓", userId, symbol);
            return;
        }
        
        // 计算减仓数量
        BigDecimal reductionQuantity = position.getQuantity().multiply(reductionRatio);
        
        log.info("执行持仓减仓，用户: {}, 交易对: {}, 减仓比例: {}, 减仓数量: {}", 
                userId, symbol, reductionRatio, reductionQuantity);
        
        // 创建减仓订单
        Order reductionOrder = createReductionOrder(userId, symbol, reductionQuantity, position.getSide());
        
        // 执行减仓撮合
        List<Trade> trades = executeReductionMatching(reductionOrder);
        
        // 更新持仓
        updatePositionAfterReduction(position, trades);
        
        log.info("持仓减仓完成，用户: {}, 交易对: {}, 成交数量: {}", userId, symbol, 
                trades.stream().map(Trade::getQuantity).reduce(BigDecimal.ZERO, BigDecimal::add));
    }
    
    /**
     * 创建减仓订单
     */
    private Order createReductionOrder(Long userId, String symbol, BigDecimal quantity, PositionSide positionSide) {
        Order order = new Order();
        order.setOrderId("RED_" + System.currentTimeMillis() + "_" + UUID.randomUUID().toString().substring(0, 8));
        order.setUserId(userId);
        order.setSymbol(symbol);
        order.setSide(positionSide == PositionSide.LONG ? OrderSide.SELL : OrderSide.BUY);
        order.setType(OrderType.MARKET); // 市价减仓
        order.setQuantity(quantity);
        order.setPositionAction(PositionAction.CLOSE);
        order.setClientOrderId("RISK_REDUCTION");
        order.setRemark("风险降档减仓");
        
        return order;
    }
    
    /**
     * 执行减仓撮合
     */
    private List<Trade> executeReductionMatching(Order order) {
        // 获取订单薄
        OrderBook orderBook = memoryManager.getOrCreateOrderBook(order.getSymbol());
        
        List<Trade> trades = new ArrayList<>();
        BigDecimal remainingQuantity = order.getQuantity();
        
        if (order.getSide() == OrderSide.BUY) {
            // 买入减仓：从卖单薄撮合
            for (List<Order> sellOrders : orderBook.getSellOrders().values()) {
                for (Order sellOrder : sellOrders) {
                    if (remainingQuantity.compareTo(BigDecimal.ZERO) <= 0) {
                        break;
                    }
                    
                    BigDecimal tradeQuantity = remainingQuantity.min(sellOrder.getRemainingQuantity());
                    BigDecimal tradePrice = sellOrder.getPrice();
                    
                    Trade trade = createTrade(order, sellOrder, tradeQuantity, tradePrice);
                    trades.add(trade);
                    
                    remainingQuantity = remainingQuantity.subtract(tradeQuantity);
                }
            }
        } else {
            // 卖出减仓：从买单薄撮合
            for (List<Order> buyOrders : orderBook.getBuyOrders().values()) {
                for (Order buyOrder : buyOrders) {
                    if (remainingQuantity.compareTo(BigDecimal.ZERO) <= 0) {
                        break;
                    }
                    
                    BigDecimal tradeQuantity = remainingQuantity.min(buyOrder.getRemainingQuantity());
                    BigDecimal tradePrice = buyOrder.getPrice();
                    
                    Trade trade = createTrade(buyOrder, order, tradeQuantity, tradePrice);
                    trades.add(trade);
                    
                    remainingQuantity = remainingQuantity.subtract(tradeQuantity);
                }
            }
        }
        
        return trades;
    }
    
    /**
     * 创建成交记录
     */
    private Trade createTrade(Order buyOrder, Order sellOrder, BigDecimal quantity, BigDecimal price) {
        Trade trade = new Trade();
        trade.setTradeId(UUID.randomUUID().toString());
        trade.setSymbol(buyOrder.getSymbol());
        trade.setBuyOrderId(buyOrder.getOrderId());
        trade.setSellOrderId(sellOrder.getOrderId());
        trade.setBuyUserId(buyOrder.getUserId());
        trade.setSellUserId(sellOrder.getUserId());
        trade.setQuantity(quantity);
        trade.setPrice(price);
        trade.setAmount(quantity.multiply(price));
        trade.setTradeTime(java.time.LocalDateTime.now());
        
        return trade;
    }
    
    /**
     * 更新减仓后的持仓
     */
    private void updatePositionAfterReduction(Position position, List<Trade> trades) {
        if (trades.isEmpty()) {
            return;
        }
        
        BigDecimal reducedQuantity = trades.stream()
                .map(Trade::getQuantity)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        // 更新持仓数量
        position.setQuantity(position.getQuantity().subtract(reducedQuantity));
        
        // 如果持仓为零，清空持仓
        if (position.getQuantity().compareTo(BigDecimal.ZERO) <= 0) {
            position.setQuantity(BigDecimal.ZERO);
            position.setSide(null);
            position.setAveragePrice(BigDecimal.ZERO);
            position.setUnrealizedPnl(BigDecimal.ZERO);
        }
        
        // 更新到内存管理器
        memoryManager.updatePosition(position);
    }
    
    /**
     * 执行ADL策略
     * 由风控服务调用，撮合引擎只负责执行
     */
    public void executeADLStrategy(String symbol, ADLStrategy strategy, BigDecimal targetQuantity) {
        if (strategy.isDirectLiquidation()) {
            log.info("ADL策略为直接强平，跳过ADL执行");
            return;
        }
        
        log.info("开始执行ADL策略，交易对: {}, 策略: {}, 目标减仓数量: {}", symbol, strategy.getName(), targetQuantity);
        
        // 获取所有持仓
        Map<Long, Position> allPositions = memoryManager.getAllPositions(symbol);
        if (allPositions.isEmpty()) {
            log.info("交易对{}没有持仓，跳过ADL执行", symbol);
            return;
        }
        
        // 根据策略选择减仓目标
        List<Position> adlTargets = selectADLTargets(allPositions, strategy);
        
        // 执行ADL减仓
        executeADLReduction(adlTargets, strategy, targetQuantity);
    }
    
    /**
     * 根据策略选择ADL减仓目标
     */
    private List<Position> selectADLTargets(Map<Long, Position> allPositions, ADLStrategy strategy) {
        List<Position> positions = new ArrayList<>(allPositions.values());
        
        switch (strategy) {
            case COUNTERPARTY_FIRST:
                // 对手方优先：选择与强平用户相反方向的持仓
                return positions.stream()
                        .filter(p -> p.getSide() != null)
                        .sorted(Comparator.comparing(Position::getQuantity).reversed())
                        .collect(Collectors.toList());
                
            case PROFIT_FIRST:
                // 盈利优先：按未实现盈亏排序，盈利的优先减仓
                return positions.stream()
                        .filter(p -> p.getUnrealizedPnl() != null && p.getUnrealizedPnl().compareTo(BigDecimal.ZERO) > 0)
                        .sorted(Comparator.comparing(Position::getUnrealizedPnl).reversed())
                        .collect(Collectors.toList());
                
            case LOSS_FIRST:
                // 亏损优先：按未实现盈亏排序，亏损的优先减仓
                return positions.stream()
                        .filter(p -> p.getUnrealizedPnl() != null && p.getUnrealizedPnl().compareTo(BigDecimal.ZERO) < 0)
                        .sorted(Comparator.comparing(Position::getUnrealizedPnl))
                        .collect(Collectors.toList());
                
            case TIME_FIRST:
                // 时间优先：按持仓时间排序，先开仓的先减仓
                return positions.stream()
                        .sorted(Comparator.comparing(Position::getCreateTime))
                        .collect(Collectors.toList());
                
            case SIZE_FIRST:
                // 规模优先：按持仓规模排序，大持仓优先减仓
                return positions.stream()
                        .sorted(Comparator.comparing(Position::getQuantity).reversed())
                        .collect(Collectors.toList());
                
            case HYBRID:
                // 混合策略：综合考虑多个因素
                return positions.stream()
                        .sorted((p1, p2) -> {
                            // 综合评分：规模(40%) + 盈亏(30%) + 时间(30%)
                            BigDecimal score1 = calculateHybridScore(p1);
                            BigDecimal score2 = calculateHybridScore(p2);
                            return score2.compareTo(score1);
                        })
                        .collect(Collectors.toList());
                
            default:
                return positions;
        }
    }
    
    /**
     * 计算混合策略评分
     */
    private BigDecimal calculateHybridScore(Position position) {
        // 规模评分 (0-1)
        BigDecimal sizeScore = position.getQuantity().divide(BigDecimal.valueOf(1000), 4, BigDecimal.ROUND_HALF_UP);
        
        // 盈亏评分 (-1到1)
        BigDecimal pnlScore = position.getUnrealizedPnl() != null ? 
                position.getUnrealizedPnl().divide(BigDecimal.valueOf(1000), 4, BigDecimal.ROUND_HALF_UP) : 
                BigDecimal.ZERO;
        
        // 时间评分 (0-1)
        BigDecimal timeScore = BigDecimal.valueOf(position.getCreateTime().toEpochSecond(java.time.ZoneOffset.UTC))
                .divide(BigDecimal.valueOf(System.currentTimeMillis() / 1000), 4, BigDecimal.ROUND_HALF_UP);
        
        // 综合评分 = 规模*0.4 + 盈亏*0.3 + 时间*0.3
        return sizeScore.multiply(BigDecimal.valueOf(0.4))
                .add(pnlScore.multiply(BigDecimal.valueOf(0.3)))
                .add(timeScore.multiply(BigDecimal.valueOf(0.3)));
    }
    
    /**
     * 执行ADL减仓
     */
    private void executeADLReduction(List<Position> adlTargets, ADLStrategy strategy, BigDecimal targetQuantity) {
        BigDecimal remainingQuantity = targetQuantity;
        int adlCount = 0;
        
        for (Position position : adlTargets) {
            if (remainingQuantity.compareTo(BigDecimal.ZERO) <= 0 || adlCount >= strategy.getMaxADLCount()) {
                break;
            }
            
            // 计算本次减仓数量
            BigDecimal adlRatio = strategy.getADLRatio();
            BigDecimal reductionQuantity = position.getQuantity().multiply(adlRatio);
            
            if (reductionQuantity.compareTo(remainingQuantity) > 0) {
                reductionQuantity = remainingQuantity;
            }
            
            // 执行减仓
            executeSingleADLReduction(position, reductionQuantity);
            
            remainingQuantity = remainingQuantity.subtract(reductionQuantity);
            adlCount++;
            
            log.info("ADL减仓完成，用户: {}, 减仓数量: {}, 剩余目标: {}, ADL次数: {}", 
                    position.getUserId(), reductionQuantity, remainingQuantity, adlCount);
        }
        
        log.info("ADL策略执行完成，总减仓数量: {}, 执行次数: {}", 
                targetQuantity.subtract(remainingQuantity), adlCount);
    }
    
    /**
     * 执行单个ADL减仓
     */
    private void executeSingleADLReduction(Position position, BigDecimal reductionQuantity) {
        // 创建ADL减仓订单
        Order adlOrder = createADLOrder(position, reductionQuantity);
        
        // 执行减仓撮合
        List<Trade> trades = executeReductionMatching(adlOrder);
        
        // 更新持仓
        updatePositionAfterReduction(position, trades);
    }
    
    /**
     * 创建ADL减仓订单
     */
    private Order createADLOrder(Position position, BigDecimal quantity) {
        Order order = new Order();
        order.setOrderId("ADL_" + System.currentTimeMillis() + "_" + UUID.randomUUID().toString().substring(0, 8));
        order.setUserId(position.getUserId());
        order.setSymbol(position.getSymbol());
        order.setSide(position.getSide() == PositionSide.LONG ? OrderSide.SELL : OrderSide.BUY);
        order.setType(OrderType.MARKET); // 市价减仓
        order.setQuantity(quantity);
        order.setPositionAction(PositionAction.CLOSE);
        order.setClientOrderId("ADL_REDUCTION");
        order.setRemark("ADL自动减仓");
        
        return order;
    }
    
    /**
     * 分档平仓结果
     */
    @lombok.Data
    public static class TieredLiquidationResult {
        private Long userId;
        private String symbol;
        private long startTime;
        private long endTime;
        private RiskLevel finalRiskLevel;
        private String errorMessage;
        private List<TieredLiquidationStep> steps = new ArrayList<>();
        
        public void addStep(TieredLiquidationStep step) {
            steps.add(step);
        }
        
        public long getDuration() {
            return endTime - startTime;
        }
        
        public BigDecimal getTotalLiquidationQuantity() {
            return steps.stream()
                    .map(TieredLiquidationStep::getSuccessQuantity)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
        }
        
        public BigDecimal getTotalLiquidationAmount() {
            return steps.stream()
                    .map(TieredLiquidationStep::getTotalAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
        }
    }
    
    /**
     * 分档平仓步骤
     */
    @lombok.Data
    public static class TieredLiquidationStep {
        private RiskLevel riskLevel;
        private BigDecimal successQuantity;
        private BigDecimal totalAmount;
        private BigDecimal averagePrice;
        private int tradeCount;
        private String errorMessage;
        private long startTime;
        private long endTime;
        
        public long getDuration() {
            return endTime - startTime;
        }
    }
} 