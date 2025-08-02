package com.exchange.match.core.event.handler;

import com.exchange.match.core.event.EventHandler;
import com.exchange.match.core.event.MatchEvent;
import com.exchange.match.core.memory.MemoryManager;
import com.exchange.match.core.matcher.OrderMatcher;
import com.exchange.match.core.matcher.OrderMatcherFactory;
import com.exchange.match.core.model.*;
import com.exchange.match.core.model.MatchResponse;
import com.exchange.match.core.service.BatchKafkaService;
import com.exchange.match.core.service.CommandIdGenerator;
import com.exchange.match.core.model.StateChangeEvent;
import com.exchange.match.enums.EventType;
import com.exchange.match.request.EventNewOrderReq;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 新订单事件处理器
 * 整合撮合逻辑到Disruptor事件中
 */
@Slf4j
@Component
public class NewOrderEventHandler implements EventHandler {
    
    @Autowired
    private MemoryManager memoryManager;
    
    @Autowired
    private OrderMatcherFactory orderMatcherFactory;
    
    @Autowired
    private BatchKafkaService batchKafkaService;
    
    @Override
    public void handle(MatchEvent event) {
        try {
            EventNewOrderReq newOrderReq = event.getNewOrderReq();
            log.info("处理新订单事件: orderId={}, userId={}, symbol={}", 
                    newOrderReq.getOrderId(), newOrderReq.getUserId(), newOrderReq.getSymbol());
            
            // 创建订单对象
            Order order = createOrderFromRequest(newOrderReq);
            
            // 执行撮合逻辑
            MatchResponse response = processOrder(order);
            
            // 推送撮合结果到Kafka
            batchKafkaService.pushMatchResult(response);
            
            // 只有成功的事件才推送状态变动事件
            if (response.getStatus() == MatchStatus.SUCCESS || 
                response.getStatus() == MatchStatus.PARTIALLY_FILLED || 
                response.getStatus() == MatchStatus.PENDING) {
                
                // 生成命令ID
                long commandId = CommandIdGenerator.nextId();
                
                // 创建状态变动事件
                StateChangeEvent stateChangeEvent = StateChangeEvent.createSuccess(
                    commandId, 
                    EventType.NEW_ORDER, 
                    newOrderReq, 
                    response
                );
                
                // 推送状态变动事件到Kafka
                batchKafkaService.pushStateChangeEvent(stateChangeEvent);
                
                log.info("推送状态变动事件: commandId={}, orderId={}, status={}", 
                        commandId, newOrderReq.getOrderId(), response.getStatus());
            } else {
                log.info("订单被拒绝，不推送状态变动事件: orderId={}, status={}, reason={}", 
                        newOrderReq.getOrderId(), response.getStatus(), response.getErrorMessage());
            }
            
            // 设置处理结果
            event.setResult(response);
            
            log.info("新订单处理完成: orderId={}, status={}, filled={}, remaining={}", 
                    order.getOrderId(), response.getStatus(), 
                    response.getMatchQuantity(), response.getRemainingQuantity());
            
        } catch (Exception e) {
            log.error("处理新订单事件失败", e);
            event.setException(e);
        }
    }
    
    /**
     * 从请求创建订单对象
     */
    private Order createOrderFromRequest(EventNewOrderReq newOrderReq) {
        Order order = new Order();
        order.setOrderId(newOrderReq.getOrderId());
        order.setUserId(newOrderReq.getUserId());
        order.setSymbol(newOrderReq.getSymbol());
        
        // 转换字符串为枚举
        if (newOrderReq.getSide() != null) {
            order.setSide(OrderSide.valueOf(newOrderReq.getSide()));
        }
        if (newOrderReq.getOrderType() != null) {
            order.setType(OrderType.valueOf(newOrderReq.getOrderType()));
        }
        if (newOrderReq.getPositionAction() != null) {
            order.setPositionAction(PositionAction.valueOf(newOrderReq.getPositionAction()));
        }
        
        order.setPrice(newOrderReq.getPrice());
        order.setQuantity(newOrderReq.getQuantity());
        order.setClientOrderId(newOrderReq.getClientOrderId());
        order.setRemark(newOrderReq.getRemark());
        return order;
    }
    
    /**
     * 处理订单撮合逻辑
     */
    private MatchResponse processOrder(Order order) {
        MatchResponse response = new MatchResponse();
        response.setOrderId(order.getOrderId());
        response.setUserId(order.getUserId());
        response.setSymbol(order.getSymbol());
        response.setSide(order.getSide());
        response.setOrderType(order.getType());
        response.setOrderPrice(order.getPrice());
        response.setOrderQuantity(order.getQuantity());
        
        try {
            // 验证标的
            Symbol symbol = memoryManager.getSymbol(order.getSymbol());
            if (symbol == null) {
                response.setStatus(MatchStatus.REJECTED);
                response.setErrorMessage("标的不存在: " + order.getSymbol());
                response.setRejectInfo(createRejectInfo(
                    MatchResponse.RejectInfo.RejectType.SYMBOL_NOT_TRADABLE,
                    "标的不存在: " + order.getSymbol()
                ));
                return response;
            }
            
            if (!symbol.isTradeable()) {
                response.setStatus(MatchStatus.REJECTED);
                response.setErrorMessage("标的不可交易: " + order.getSymbol());
                response.setRejectInfo(createRejectInfo(
                    MatchResponse.RejectInfo.RejectType.MARKET_CLOSED,
                    "标的不可交易: " + order.getSymbol()
                ));
                return response;
            }
            
            // 验证订单参数
            if (!symbol.isValidPrice(order.getPrice())) {
                response.setStatus(MatchStatus.REJECTED);
                response.setErrorMessage("价格无效: " + order.getPrice());
                response.setRejectInfo(createRejectInfo(
                    MatchResponse.RejectInfo.RejectType.INVALID_PRICE,
                    "价格无效: " + order.getPrice()
                ));
                return response;
            }
            
            if (!symbol.isValidQuantity(order.getQuantity())) {
                response.setStatus(MatchStatus.REJECTED);
                response.setErrorMessage("数量无效: " + order.getQuantity());
                response.setRejectInfo(createRejectInfo(
                    MatchResponse.RejectInfo.RejectType.INVALID_QUANTITY,
                    "数量无效: " + order.getQuantity()
                ));
                return response;
            }
            
            // 格式化价格和数量
            order.setPrice(symbol.formatPrice(order.getPrice()));
            order.setQuantity(symbol.formatQuantity(order.getQuantity()));
            order.setRemainingQuantity(order.getQuantity());
            
            // 根据交易类型设置开平仓动作
            if (symbol.supportsPosition()) {
                // 合约交易：如果没有设置开平仓动作，则自动判断
                if (order.getPositionAction() == null) {
                    Position currentPosition = memoryManager.getPosition(order.getUserId(), order.getSymbol());
                    PositionSide currentPositionSide = currentPosition != null ? currentPosition.getSide() : null;
                    order.setPositionAction(PositionAction.determineAction(order.getSide(), currentPositionSide));
                }
                
                // 开平仓订单需要检查持仓平衡
                if (order.getPositionAction() != null) {
                    PositionBalance balance = getPositionBalance(order.getSymbol());
                    if (balance != null) {
                        // 检查开平仓是否会导致持仓不平衡
                        if (!balance.checkOpenCloseBalance(order.getPositionAction(), order.getSide(), order.getQuantity())) {
                            response.setStatus(MatchStatus.REJECTED);
                            response.setErrorMessage("开平仓操作会导致持仓不平衡");
                            response.setRejectInfo(createRejectInfo(
                                MatchResponse.RejectInfo.RejectType.POSITION_IMBALANCE,
                                "开平仓操作会导致持仓不平衡。当前状态: " + balance.getStatus().getDescription() + 
                                ", 建议: " + balance.getBalanceAdvice()
                            ));
                            return response;
                        }
                    }
                }
                
                // 平仓订单需要检查仓位锁定
                if (order.getPositionAction() != null && order.getPositionAction().isClose()) {
                    Position position = memoryManager.getPosition(order.getUserId(), order.getSymbol());
                    if (position != null) {
                        // 检查可用数量是否足够
                        if (!position.canClose(order.getQuantity())) {
                            response.setStatus(MatchStatus.REJECTED);
                            response.setErrorMessage("可用仓位不足，无法平仓");
                            response.setRejectInfo(createRejectInfo(
                                MatchResponse.RejectInfo.RejectType.INSUFFICIENT_POSITION,
                                "可用仓位不足，无法平仓。可用数量: " + position.getAvailableQuantity() + 
                                ", 请求数量: " + order.getQuantity()
                            ));
                            return response;
                        }
                        
                        // 锁定仓位
                        if (!position.lockPosition(order.getQuantity(), order.getOrderId(), "平仓订单锁定")) {
                            response.setStatus(MatchStatus.REJECTED);
                            response.setErrorMessage("仓位锁定失败");
                            response.setRejectInfo(createRejectInfo(
                                MatchResponse.RejectInfo.RejectType.SYSTEM_ERROR,
                                "仓位锁定失败"
                            ));
                            return response;
                        }
                        
                        // 更新仓位到内存管理器
                        memoryManager.updatePosition(position);
                    }
                }
            } else {
                // 现货交易：不设置开平仓动作
                order.setPositionAction(null);
            }
            
            // 获取订单薄
            OrderBook orderBook = memoryManager.getOrCreateOrderBook(order.getSymbol());
            
            // 执行撮合
            List<Trade> trades = executeMatching(order, orderBook, symbol);
            
            // 更新响应信息
            response.setTrades(trades);
            response.setMatchQuantity(order.getFilledQuantity());
            response.setRemainingQuantity(order.getRemainingQuantity());
            
            // 计算成交价格和金额
            if (!trades.isEmpty()) {
                BigDecimal totalAmount = trades.stream()
                        .map(Trade::getAmount)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
                BigDecimal totalQuantity = trades.stream()
                        .map(Trade::getQuantity)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
                
                response.setMatchAmount(totalAmount);
                response.setMatchPrice(totalAmount.divide(totalQuantity, symbol.getPricePrecision(), BigDecimal.ROUND_HALF_UP));
                response.setFee(symbol.calculateFee(totalAmount, BigDecimal.ONE));
            }
            
            // 更新状态
            if (order.isFullyFilled()) {
                response.setStatus(MatchStatus.SUCCESS);
            } else if (order.isPartiallyFilled()) {
                response.setStatus(MatchStatus.PARTIALLY_FILLED);
            } else {
                response.setStatus(MatchStatus.PENDING);
            }
            
            // 更新仓位变化信息（仅合约交易）
            if (!trades.isEmpty() && symbol.supportsPosition()) {
                try {
                    response.setPositionChange(calculatePositionChange(order, trades));
                } catch (IllegalArgumentException e) {
                    response.setStatus(MatchStatus.REJECTED);
                    response.setErrorMessage(e.getMessage());
                    response.setRejectInfo(createRejectInfo(
                        MatchResponse.RejectInfo.RejectType.INVALID_POSITION_ACTION,
                        e.getMessage()
                    ));
                    return response;
                }
            }
            
        } catch (Exception e) {
            log.error("处理订单失败: orderId={}", order.getOrderId(), e);
            response.setStatus(MatchStatus.REJECTED);
            response.setErrorMessage("系统错误: " + e.getMessage());
            response.setRejectInfo(createRejectInfo(
                MatchResponse.RejectInfo.RejectType.SYSTEM_ERROR,
                "系统错误: " + e.getMessage()
            ));
        }
        
        return response;
    }
    
    /**
     * 执行订单撮合
     */
    private List<Trade> executeMatching(Order order, OrderBook orderBook, Symbol symbol) {
        // 根据订单类型获取对应的匹配器
        OrderMatcher matcher = orderMatcherFactory.getMatcher(order.getType());
        
        // 执行撮合
        List<Trade> trades = matcher.matchOrder(order, orderBook, symbol);
        
        // 更新仓位（仅合约交易）
        if (symbol.supportsPosition()) {
            for (Trade trade : trades) {
                updatePositionsFromTrade(trade);
            }
        }
        
        return trades;
    }
    
    /**
     * 从成交记录更新仓位
     */
    private void updatePositionsFromTrade(Trade trade) {
        // 更新买方仓位
        Position buyPosition = memoryManager.getOrCreatePosition(trade.getBuyUserId(), trade.getSymbol());
        if (trade.getBuyPositionAction() != null && trade.getBuyPositionAction().isOpen()) {
            buyPosition.openPosition(trade.getQuantity(), trade.getPrice());
        } else if (trade.getBuyPositionAction() != null) {
            buyPosition.closePosition(trade.getQuantity(), trade.getPrice());
        }
        memoryManager.updatePosition(buyPosition);
        
        // 更新卖方仓位
        Position sellPosition = memoryManager.getOrCreatePosition(trade.getSellUserId(), trade.getSymbol());
        if (trade.getSellPositionAction() != null && trade.getSellPositionAction().isOpen()) {
            sellPosition.openPosition(trade.getQuantity(), trade.getPrice());
        } else if (trade.getSellPositionAction() != null) {
            sellPosition.closePosition(trade.getQuantity(), trade.getPrice());
        }
        memoryManager.updatePosition(sellPosition);
    }
    
    /**
     * 计算仓位变化信息
     */
    private MatchResponse.PositionChange calculatePositionChange(Order order, List<Trade> trades) {
        MatchResponse.PositionChange positionChange = new MatchResponse.PositionChange();
        positionChange.setUserId(order.getUserId());
        positionChange.setSymbol(order.getSymbol());
        positionChange.setPositionAction(order.getPositionAction());
        
        // 根据订单方向设置仓位方向
        if (order.getSide() == OrderSide.BUY) {
            positionChange.setSide(PositionSide.LONG);
        } else {
            positionChange.setSide(PositionSide.SHORT);
        }
        
        // 计算仓位变化
        BigDecimal totalQuantity = trades.stream()
                .map(Trade::getQuantity)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalValue = trades.stream()
                .map(Trade::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        // 使用新的计算逻辑
        Position currentPosition = memoryManager.getPosition(order.getUserId(), order.getSymbol());
        PositionSide currentPositionSide = currentPosition != null ? currentPosition.getSide() : null;
        
        PositionAction.PositionChangeResult result = PositionAction.calculatePositionChange(
            order.getSide(), 
            order.getPositionAction(), 
            currentPositionSide,
            totalQuantity
        );
        
        if (!result.isValid()) {
            throw new IllegalArgumentException("无效的开平仓操作: " + result.getReason());
        }
        
        BigDecimal positionChangeQuantity = result.getQuantityChange();
        
        positionChange.setQuantityChange(positionChangeQuantity);
        positionChange.setValueChange(totalValue);
        
        // 获取当前仓位
        Position position = memoryManager.getPosition(order.getUserId(), order.getSymbol());
        if (position != null) {
            positionChange.setNewQuantity(position.getQuantity().add(positionChangeQuantity));
            positionChange.setNewValue(position.getPositionValue().add(totalValue));
            positionChange.setNewUnrealizedPnl(position.getUnrealizedPnl());
            positionChange.setNewRealizedPnl(position.getRealizedPnl());
            positionChange.setAverageCost(position.getAveragePrice());
            
            // 计算已实现盈亏变化（平仓时）
            if (order.getPositionAction() != null && order.getPositionAction().isClose()) {
                BigDecimal realizedPnlChange = calculateRealizedPnlChange(position, order, trades);
                positionChange.setRealizedPnlChange(realizedPnlChange);
                positionChange.setNewRealizedPnl(position.getRealizedPnl().add(realizedPnlChange));
            }
        } else {
            positionChange.setNewQuantity(positionChangeQuantity);
            positionChange.setNewValue(totalValue);
            positionChange.setNewUnrealizedPnl(BigDecimal.ZERO);
            positionChange.setNewRealizedPnl(BigDecimal.ZERO);
            positionChange.setAverageCost(totalValue.divide(totalQuantity, 8, BigDecimal.ROUND_HALF_UP));
        }
        
        return positionChange;
    }
    
    /**
     * 计算已实现盈亏变化
     */
    private BigDecimal calculateRealizedPnlChange(Position position, Order order, List<Trade> trades) {
        if (order.getPositionAction() == null || !order.getPositionAction().isClose()) {
            return BigDecimal.ZERO;
        }
        
        BigDecimal totalCloseValue = trades.stream()
                .map(Trade::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalCloseQuantity = trades.stream()
                .map(Trade::getQuantity)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        BigDecimal closeAveragePrice = totalCloseValue.divide(totalCloseQuantity, 8, BigDecimal.ROUND_HALF_UP);
        BigDecimal openAveragePrice = position.getAveragePrice();
        
        // 计算已实现盈亏
        if (position.getSide() == PositionSide.LONG) {
            // 多仓平仓：平仓价格 - 开仓价格
            return totalCloseQuantity.multiply(closeAveragePrice.subtract(openAveragePrice));
        } else {
            // 空仓平仓：开仓价格 - 平仓价格
            return totalCloseQuantity.multiply(openAveragePrice.subtract(closeAveragePrice));
        }
    }
    
    /**
     * 获取持仓平衡信息
     */
    private PositionBalance getPositionBalance(String symbol) {
        // 获取所有持仓
        Map<Long, Position> allPositions = memoryManager.getAllPositions(symbol);
        if (allPositions == null || allPositions.isEmpty()) {
            return null;
        }
        
        PositionBalance balance = new PositionBalance(symbol);
        balance.updatePositions(allPositions);
        return balance;
    }
    
    /**
     * 创建拒绝信息
     */
    private MatchResponse.RejectInfo createRejectInfo(MatchResponse.RejectInfo.RejectType rejectType, String reason) {
        MatchResponse.RejectInfo rejectInfo = new MatchResponse.RejectInfo();
        rejectInfo.setRejectType(rejectType);
        rejectInfo.setRejectCode(rejectType.name());
        rejectInfo.setRejectReason(reason);
        return rejectInfo;
    }
    
    @Override
    public EventType getSupportedEventType() {
        return EventType.NEW_ORDER;
    }
} 