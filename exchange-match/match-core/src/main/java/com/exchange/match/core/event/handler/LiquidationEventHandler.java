package com.exchange.match.core.event.handler;

import com.exchange.match.core.event.EventHandler;
import com.exchange.match.core.event.MatchEvent;
import com.exchange.match.core.memory.MemoryManager;
import com.exchange.match.core.model.*;
import com.exchange.match.core.model.MatchResponse;
import com.exchange.match.core.service.RiskManagementService;
import com.exchange.match.core.service.RiskRecalculationService;
import com.exchange.match.enums.EventType;
import com.exchange.match.request.EventLiquidationReq;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 强平事件处理器
 */
@Slf4j
@Component
public class LiquidationEventHandler implements EventHandler {
    
    @Autowired
    private MemoryManager memoryManager;
    
    @Autowired
    private RiskManagementService riskManagementService;
    
    @Autowired
    private RiskRecalculationService riskRecalculationService;
    
    @Override
    public void handle(MatchEvent event) {
        try {
            EventLiquidationReq liquidationReq = event.getLiquidationReq();
            log.info("处理强平事件: liquidationId={}, userId={}, symbol={}, type={}", 
                    liquidationReq.getLiquidationId(), liquidationReq.getUserId(), 
                    liquidationReq.getSymbol(), liquidationReq.getLiquidationType());
            
            // 创建强平请求
            LiquidationRequest liquidationRequest = createLiquidationRequest(liquidationReq);
            
            // 执行强平逻辑
            LiquidationRequest.LiquidationResult result = processLiquidation(liquidationRequest);
            
            // 设置处理结果
            event.setResult(result);
            
            log.info("强平事件处理完成: liquidationId={}, status={}, successQuantity={}", 
                    liquidationRequest.getLiquidationId(), liquidationRequest.getStatus(), 
                    result.getSuccessQuantity());
            
        } catch (Exception e) {
            log.error("处理强平事件失败", e);
            event.setException(e);
        }
    }
    
    /**
     * 从请求创建强平请求对象
     */
    private LiquidationRequest createLiquidationRequest(EventLiquidationReq liquidationReq) {
        LiquidationRequest request = new LiquidationRequest();
        request.setLiquidationId(liquidationReq.getLiquidationId());
        request.setUserId(liquidationReq.getUserId());
        request.setSymbol(liquidationReq.getSymbol());
        
        // 转换字符串为枚举
        if (liquidationReq.getLiquidationType() != null) {
            request.setLiquidationType(LiquidationType.valueOf(liquidationReq.getLiquidationType()));
        }
        if (liquidationReq.getSide() != null) {
            request.setSide(OrderSide.valueOf(liquidationReq.getSide()));
        }
        
        request.setQuantity(liquidationReq.getQuantity());
        request.setPrice(liquidationReq.getPrice());
        request.setReason(liquidationReq.getReason());
        request.setTriggerParams(liquidationReq.getTriggerParams());
        request.setIsFullLiquidation(liquidationReq.getIsFullLiquidation());
        request.setIsEmergency(liquidationReq.getIsEmergency());
        request.setPriority(liquidationReq.getPriority());
        
        // 设置风控服务传入的参数
        if (liquidationReq.getRiskLevel() != null) {
            request.setRiskLevel(RiskLevel.valueOf(liquidationReq.getRiskLevel()));
        }
        request.setNeedRiskReduction(liquidationReq.getNeedRiskReduction());
        
        // 设置风控服务传递的上下文信息
        request.setIndexPrice(liquidationReq.getIndexPrice());
        request.setBalance(liquidationReq.getBalance());
        request.setMargin(liquidationReq.getMargin());
        request.setRiskRatio(liquidationReq.getRiskRatio());
        request.setUnrealizedPnl(liquidationReq.getUnrealizedPnl());
        request.setRealizedPnl(liquidationReq.getRealizedPnl());
        request.setMaintenanceMarginRatio(liquidationReq.getMaintenanceMarginRatio());
        request.setInitialMarginRatio(liquidationReq.getInitialMarginRatio());
        request.setPositionInfo(liquidationReq.getPositionInfo());
        request.setMarketInfo(liquidationReq.getMarketInfo());
        request.setRiskCalculationTime(liquidationReq.getRiskCalculationTime());
        
        // 设置优先级和紧急标志
        request.setPriorityFromType();
        request.setEmergencyFromType();
        
        return request;
    }
    
    /**
     * 处理强平逻辑
     */
    private LiquidationRequest.LiquidationResult processLiquidation(LiquidationRequest liquidationRequest) {
        LiquidationRequest.LiquidationResult result = new LiquidationRequest.LiquidationResult();
        result.setExecuteTime(LocalDateTime.now());
        
        try {
            // 更新状态为执行中
            liquidationRequest.setStatus(LiquidationRequest.LiquidationStatus.EXECUTING);
            liquidationRequest.setExecuteTime(LocalDateTime.now());
            
            // 获取用户持仓
            Position position = memoryManager.getPosition(liquidationRequest.getUserId(), liquidationRequest.getSymbol());
            if (position == null || position.getQuantity().compareTo(BigDecimal.ZERO) <= 0) {
                result.setErrorMessage("用户无持仓或持仓为零");
                result.setSuccessQuantity(BigDecimal.ZERO);
                result.setFailedQuantity(BigDecimal.ZERO);
                liquidationRequest.setStatus(LiquidationRequest.LiquidationStatus.COMPLETED);
                return result;
            }
            
            // 根据风控服务传递的数据重新计算风险率
            log.info("根据风控数据重新计算风险率，用户: {}, 交易对: {}", 
                    liquidationRequest.getUserId(), liquidationRequest.getSymbol());
            
            RiskRecalculationService.RiskRecalculationResult riskResult = 
                riskRecalculationService.recalculateRisk(liquidationRequest);
            
            log.info("风险重新计算完成，用户: {}, 交易对: {}, 风险率: {}, 风险等级: {}, 需要降档: {}, 需要强平: {}", 
                    liquidationRequest.getUserId(), liquidationRequest.getSymbol(), 
                    riskResult.getRiskRatio(), riskResult.getRiskLevel().getName(), 
                    riskResult.isNeedRiskReduction(), riskResult.isNeedLiquidation());
            
            // 如果重新计算后不需要强平，直接返回
            if (!riskResult.isNeedLiquidation()) {
                log.info("重新计算后不需要强平，用户: {}, 交易对: {}, 风险率: {}", 
                        liquidationRequest.getUserId(), liquidationRequest.getSymbol(), riskResult.getRiskRatio());
                result.setErrorMessage("重新计算后风险率正常，无需强平");
                result.setSuccessQuantity(BigDecimal.ZERO);
                result.setFailedQuantity(BigDecimal.ZERO);
                liquidationRequest.setStatus(LiquidationRequest.LiquidationStatus.COMPLETED);
                return result;
            }
            
            // 检查是否需要执行分档平仓
            if (shouldExecuteTieredLiquidation(riskResult.getRiskLevel())) {
                log.info("执行分档平仓，用户: {}, 交易对: {}, 风险等级: {}", 
                        liquidationRequest.getUserId(), liquidationRequest.getSymbol(), 
                        riskResult.getRiskLevel().getName());
                
                // 执行分档平仓
                RiskManagementService.TieredLiquidationResult tieredResult = 
                    riskManagementService.executeTieredLiquidation(
                        liquidationRequest.getUserId(), 
                        liquidationRequest.getSymbol(), 
                        liquidationRequest
                    );
                
                // 计算分档平仓结果
                calculateTieredLiquidationResult(result, tieredResult);
                
                log.info("分档平仓完成，用户: {}, 交易对: {}, 总平仓数量: {}, 总平仓金额: {}, 最终风险等级: {}", 
                        liquidationRequest.getUserId(), liquidationRequest.getSymbol(),
                        tieredResult.getTotalLiquidationQuantity(), tieredResult.getTotalLiquidationAmount(),
                        tieredResult.getFinalRiskLevel().getName());
                
                liquidationRequest.setStatus(LiquidationRequest.LiquidationStatus.COMPLETED);
                return result;
            }
            
            // 如果需要风险降档，先执行降档
            if (riskResult.isNeedRiskReduction()) {
                log.info("执行风险降档，用户: {}, 交易对: {}, 风险等级: {}", 
                        liquidationRequest.getUserId(), liquidationRequest.getSymbol(), 
                        riskResult.getRiskLevel().getName());
                riskManagementService.executeRiskReduction(liquidationRequest.getUserId(), liquidationRequest.getSymbol(), riskResult.getRiskLevel());
                
                // 降档后重新计算风险率
                log.info("降档后重新计算风险率，用户: {}, 交易对: {}", 
                        liquidationRequest.getUserId(), liquidationRequest.getSymbol());
                riskResult = riskRecalculationService.recalculateRisk(liquidationRequest);
                
                log.info("降档后风险重新计算完成，用户: {}, 交易对: {}, 风险率: {}, 风险等级: {}, 需要强平: {}", 
                        liquidationRequest.getUserId(), liquidationRequest.getSymbol(), 
                        riskResult.getRiskRatio(), riskResult.getRiskLevel().getName(), 
                        riskResult.isNeedLiquidation());
                
                // 如果降档后不需要强平，直接返回
                if (!riskResult.isNeedLiquidation()) {
                    log.info("降档后不需要强平，用户: {}, 交易对: {}, 风险率: {}", 
                            liquidationRequest.getUserId(), liquidationRequest.getSymbol(), riskResult.getRiskRatio());
                    result.setErrorMessage("降档后风险率正常，无需强平");
                    result.setSuccessQuantity(BigDecimal.ZERO);
                    result.setFailedQuantity(BigDecimal.ZERO);
                    liquidationRequest.setStatus(LiquidationRequest.LiquidationStatus.COMPLETED);
                    return result;
                }
                
                // 重新获取持仓（降档后可能发生变化）
                position = memoryManager.getPosition(liquidationRequest.getUserId(), liquidationRequest.getSymbol());
                if (position == null || position.getQuantity().compareTo(BigDecimal.ZERO) <= 0) {
                    result.setErrorMessage("风险降档后用户无持仓");
                    result.setSuccessQuantity(BigDecimal.ZERO);
                    result.setFailedQuantity(BigDecimal.ZERO);
                    liquidationRequest.setStatus(LiquidationRequest.LiquidationStatus.COMPLETED);
                    return result;
                }
            }
            
            // 确定强平数量和方向
            BigDecimal liquidationQuantity = determineLiquidationQuantity(liquidationRequest, position);
            OrderSide liquidationSide = determineLiquidationSide(liquidationRequest, position);
            
            if (liquidationQuantity.compareTo(BigDecimal.ZERO) <= 0) {
                result.setErrorMessage("强平数量为零");
                result.setSuccessQuantity(BigDecimal.ZERO);
                result.setFailedQuantity(BigDecimal.ZERO);
                liquidationRequest.setStatus(LiquidationRequest.LiquidationStatus.COMPLETED);
                return result;
            }
            
            // 检查是否需要执行ADL策略
            ADLStrategy adlStrategy = determineADLStrategy(liquidationRequest, riskResult.getRiskLevel());
            
            if (adlStrategy.isADLStrategy()) {
                log.info("执行ADL策略，用户: {}, 交易对: {}, 策略: {}, 强平数量: {}", 
                        liquidationRequest.getUserId(), liquidationRequest.getSymbol(), 
                        adlStrategy.getName(), liquidationQuantity);
                
                // 执行ADL策略
                riskManagementService.executeADLStrategy(liquidationRequest.getSymbol(), adlStrategy, liquidationQuantity);
                
                // 重新获取持仓（ADL后可能发生变化）
                position = memoryManager.getPosition(liquidationRequest.getUserId(), liquidationRequest.getSymbol());
                if (position == null || position.getQuantity().compareTo(BigDecimal.ZERO) <= 0) {
                    result.setErrorMessage("ADL策略执行后用户无持仓");
                    result.setSuccessQuantity(BigDecimal.ZERO);
                    result.setFailedQuantity(BigDecimal.ZERO);
                    liquidationRequest.setStatus(LiquidationRequest.LiquidationStatus.COMPLETED);
                    return result;
                }
                
                // 重新计算强平数量
                liquidationQuantity = determineLiquidationQuantity(liquidationRequest, position);
                if (liquidationQuantity.compareTo(BigDecimal.ZERO) <= 0) {
                    result.setErrorMessage("ADL策略执行后无需强平");
                    result.setSuccessQuantity(BigDecimal.ZERO);
                    result.setFailedQuantity(BigDecimal.ZERO);
                    liquidationRequest.setStatus(LiquidationRequest.LiquidationStatus.COMPLETED);
                    return result;
                }
            }
            
            // 创建强平订单
            Order liquidationOrder = createLiquidationOrder(liquidationRequest, liquidationQuantity, liquidationSide);
            
            // 执行强平撮合
            List<Trade> trades = executeLiquidationMatching(liquidationOrder, liquidationRequest);
            
            // 计算强平结果
            calculateLiquidationResult(result, trades, position);
            
            liquidationRequest.setStatus(LiquidationRequest.LiquidationStatus.COMPLETED);
            
        } catch (Exception e) {
            log.error("强平执行失败: liquidationId={}", liquidationRequest.getLiquidationId(), e);
            liquidationRequest.setStatus(LiquidationRequest.LiquidationStatus.FAILED);
            result.setErrorMessage("强平执行失败: " + e.getMessage());
            result.setFailedQuantity(liquidationRequest.getQuantity());
        }
        
        liquidationRequest.setResult(result);
        return result;
    }
    
    /**
     * 判断是否需要执行分档平仓
     */
    private boolean shouldExecuteTieredLiquidation(RiskLevel riskLevel) {
        // 当风险等级为LIQUIDATION（第五档）时，执行分档平仓
        return riskLevel == RiskLevel.LIQUIDATION;
    }
    
    /**
     * 计算分档平仓结果
     */
    private void calculateTieredLiquidationResult(LiquidationRequest.LiquidationResult result, 
                                                 RiskManagementService.TieredLiquidationResult tieredResult) {
        if (tieredResult.getSteps().isEmpty()) {
            result.setSuccessQuantity(BigDecimal.ZERO);
            result.setFailedQuantity(BigDecimal.ZERO);
            result.setTotalAmount(BigDecimal.ZERO);
            result.setAveragePrice(BigDecimal.ZERO);
            result.setErrorMessage(tieredResult.getErrorMessage());
            return;
        }
        
        // 计算总平仓数量
        BigDecimal totalQuantity = tieredResult.getTotalLiquidationQuantity();
        BigDecimal totalAmount = tieredResult.getTotalLiquidationAmount();
        
        // 计算平均价格
        BigDecimal averagePrice = totalAmount.divide(totalQuantity, 8, BigDecimal.ROUND_HALF_UP);
        
        result.setSuccessQuantity(totalQuantity);
        result.setFailedQuantity(BigDecimal.ZERO); // 分档平仓不计算失败数量
        result.setTotalAmount(totalAmount);
        result.setAveragePrice(averagePrice);
        
        // 构建详细信息
        StringBuilder details = new StringBuilder();
        details.append("分档平仓完成，执行步骤数: ").append(tieredResult.getSteps().size());
        details.append("，最终风险等级: ").append(tieredResult.getFinalRiskLevel().getName());
        details.append("，总平仓数量: ").append(totalQuantity);
        details.append("，总平仓金额: ").append(totalAmount);
        details.append("，执行时间: ").append(tieredResult.getDuration()).append("ms");
        
        result.setDetails(details.toString());
    }
    
    /**
     * 确定强平数量
     */
    private BigDecimal determineLiquidationQuantity(LiquidationRequest request, Position position) {
        if (request.isFullLiquidation()) {
            return position.getQuantity();
        } else if (request.isPartialLiquidation()) {
            return request.getQuantity().min(position.getQuantity());
        } else {
            return BigDecimal.ZERO;
        }
    }
    
    /**
     * 确定强平方向
     */
    private OrderSide determineLiquidationSide(LiquidationRequest request, Position position) {
        if (request.getSide() != null) {
            return request.getSide();
        } else {
            // 根据持仓方向确定强平方向
            return position.getSide() == PositionSide.LONG ? OrderSide.SELL : OrderSide.BUY;
        }
    }
    
    /**
     * 创建强平订单
     */
    private Order createLiquidationOrder(LiquidationRequest request, BigDecimal quantity, OrderSide side) {
        Order order = new Order();
        order.setOrderId("LIQ_" + request.getLiquidationId() + "_" + UUID.randomUUID().toString().substring(0, 8));
        order.setUserId(request.getUserId());
        order.setSymbol(request.getSymbol());
        order.setSide(side);
        order.setType(request.isMarketLiquidation() ? OrderType.MARKET : OrderType.LIMIT);
        order.setPrice(request.getPrice());
        order.setQuantity(quantity);
        order.setPositionAction(PositionAction.CLOSE);
        order.setClientOrderId("LIQUIDATION_" + request.getLiquidationId());
        order.setRemark("强平订单 - " + request.getLiquidationType().getName());
        
        return order;
    }
    
    /**
     * 执行强平撮合
     */
    private List<Trade> executeLiquidationMatching(Order order, LiquidationRequest request) {
        // 获取订单薄
        OrderBook orderBook = memoryManager.getOrCreateOrderBook(order.getSymbol());
        
        // 执行撮合
        List<Trade> trades = new ArrayList<>();
        
        if (order.getType() == OrderType.MARKET) {
            // 市价强平：立即成交
            trades = executeMarketLiquidation(order, orderBook);
        } else {
            // 限价强平：按限价撮合
            trades = executeLimitLiquidation(order, orderBook);
        }
        
        return trades;
    }
    
    /**
     * 执行市价强平
     */
    private List<Trade> executeMarketLiquidation(Order order, OrderBook orderBook) {
        List<Trade> trades = new ArrayList<>();
        BigDecimal remainingQuantity = order.getQuantity();
        
        if (order.getSide() == OrderSide.BUY) {
            // 买入强平：从卖单薄撮合
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
            // 卖出强平：从买单薄撮合
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
     * 执行限价强平
     */
    private List<Trade> executeLimitLiquidation(Order order, OrderBook orderBook) {
        List<Trade> trades = new ArrayList<>();
        BigDecimal remainingQuantity = order.getQuantity();
        
        if (order.getSide() == OrderSide.BUY) {
            // 买入强平：从卖单薄撮合，价格不超过限价
            for (List<Order> sellOrders : orderBook.getSellOrders().values()) {
                for (Order sellOrder : sellOrders) {
                    if (remainingQuantity.compareTo(BigDecimal.ZERO) <= 0) {
                        break;
                    }
                    
                    if (sellOrder.getPrice().compareTo(order.getPrice()) > 0) {
                        break; // 价格超过限价
                    }
                    
                    BigDecimal tradeQuantity = remainingQuantity.min(sellOrder.getRemainingQuantity());
                    BigDecimal tradePrice = sellOrder.getPrice();
                    
                    Trade trade = createTrade(order, sellOrder, tradeQuantity, tradePrice);
                    trades.add(trade);
                    
                    remainingQuantity = remainingQuantity.subtract(tradeQuantity);
                }
            }
        } else {
            // 卖出强平：从买单薄撮合，价格不低于限价
            for (List<Order> buyOrders : orderBook.getBuyOrders().values()) {
                for (Order buyOrder : buyOrders) {
                    if (remainingQuantity.compareTo(BigDecimal.ZERO) <= 0) {
                        break;
                    }
                    
                    if (buyOrder.getPrice().compareTo(order.getPrice()) < 0) {
                        break; // 价格低于限价
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
        trade.setTradeTime(LocalDateTime.now());
        
        return trade;
    }
    
    /**
     * 计算强平结果
     */
    private void calculateLiquidationResult(LiquidationRequest.LiquidationResult result, List<Trade> trades, Position position) {
        if (trades.isEmpty()) {
            result.setSuccessQuantity(BigDecimal.ZERO);
            result.setFailedQuantity(position.getQuantity());
            result.setAveragePrice(BigDecimal.ZERO);
            result.setTotalAmount(BigDecimal.ZERO);
            result.setFee(BigDecimal.ZERO);
            result.setRealizedPnl(BigDecimal.ZERO);
            result.setDetails("强平失败：无成交");
            return;
        }
        
        // 计算成功数量
        BigDecimal successQuantity = trades.stream()
                .map(Trade::getQuantity)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        // 计算总金额
        BigDecimal totalAmount = trades.stream()
                .map(Trade::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        // 计算平均价格
        BigDecimal averagePrice = totalAmount.divide(successQuantity, 8, BigDecimal.ROUND_HALF_UP);
        
        // 计算手续费（简化计算）
        BigDecimal fee = totalAmount.multiply(BigDecimal.valueOf(0.001)); // 0.1%手续费
        
        // 计算已实现盈亏
        BigDecimal realizedPnl = calculateRealizedPnl(position, averagePrice, successQuantity);
        
        result.setSuccessQuantity(successQuantity);
        result.setFailedQuantity(position.getQuantity().subtract(successQuantity));
        result.setAveragePrice(averagePrice);
        result.setTotalAmount(totalAmount);
        result.setFee(fee);
        result.setRealizedPnl(realizedPnl);
        result.setDetails("强平成功，成交" + trades.size() + "笔");
    }
    
    /**
     * 计算已实现盈亏
     */
    private BigDecimal calculateRealizedPnl(Position position, BigDecimal closePrice, BigDecimal closeQuantity) {
        BigDecimal openPrice = position.getAveragePrice();
        
        if (position.getSide() == PositionSide.LONG) {
            // 多仓平仓：平仓价格 - 开仓价格
            return closeQuantity.multiply(closePrice.subtract(openPrice));
        } else {
            // 空仓平仓：开仓价格 - 平仓价格
            return closeQuantity.multiply(openPrice.subtract(closePrice));
        }
    }
    
    /**
     * 更新强平后的持仓
     */
    private void updatePositionAfterLiquidation(Position position, List<Trade> trades) {
        if (trades.isEmpty()) {
            return;
        }
        
        BigDecimal closedQuantity = trades.stream()
                .map(Trade::getQuantity)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        // 更新持仓数量
        position.setQuantity(position.getQuantity().subtract(closedQuantity));
        
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
     * 确定ADL策略
     */
    private ADLStrategy determineADLStrategy(LiquidationRequest liquidationRequest, RiskLevel riskLevel) {
        // 根据强平类型和风险等级确定ADL策略
        switch (liquidationRequest.getLiquidationType()) {
            case MARGIN_INSUFFICIENT:
                // 保证金不足：优先使用对手方优先策略
                return riskLevel == RiskLevel.LIQUIDATION ? ADLStrategy.NONE : ADLStrategy.COUNTERPARTY_FIRST;
                
            case RISK_EXCEEDED:
                // 风险度超限：使用盈利优先策略
                return riskLevel == RiskLevel.LIQUIDATION ? ADLStrategy.NONE : ADLStrategy.PROFIT_FIRST;
                
            case PRICE_DEVIATION:
                // 价格偏离：使用混合策略
                return ADLStrategy.HYBRID;
                
            case SYSTEM_RISK:
                // 系统风险：直接强平，不使用ADL
                return ADLStrategy.NONE;
                
            case REGULATORY:
                // 监管强平：直接强平，不使用ADL
                return ADLStrategy.NONE;
                
            case MANUAL:
                // 手动强平：根据风险等级选择策略
                if (riskLevel == RiskLevel.LIQUIDATION) {
                    return ADLStrategy.NONE;
                } else if (riskLevel == RiskLevel.EMERGENCY) {
                    return ADLStrategy.LOSS_FIRST;
                } else {
                    return ADLStrategy.TIME_FIRST;
                }
                
            case EXPIRY:
                // 合约到期：使用时间优先策略
                return ADLStrategy.TIME_FIRST;
                
            case OTHER:
            default:
                // 其他情况：使用规模优先策略
                return ADLStrategy.SIZE_FIRST;
        }
    }
    
    @Override
    public EventType getSupportedEventType() {
        return EventType.LIQUIDATION;
    }
} 