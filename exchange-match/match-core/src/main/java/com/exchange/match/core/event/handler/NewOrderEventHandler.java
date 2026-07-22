package com.exchange.match.core.event.handler;

import com.exchange.match.core.event.EventHandler;
import com.exchange.match.core.event.MatchEvent;
import com.exchange.match.core.memory.MemoryManager;
import com.exchange.match.core.matcher.OrderMatcher;
import com.exchange.match.core.matcher.OrderMatcherFactory;
import com.exchange.match.core.model.*;
import com.exchange.match.core.model.MatchResponse;
import com.exchange.match.core.transport.AeronMatchResultPublisher;
import com.exchange.match.enums.EventType;
import com.exchange.match.request.EventNewOrderReq;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 新订单事件处理器（Disruptor 单线程上执行）。
 *
 * <h3>输出路径</h3>
 * <pre>
 *   撮合结果
 *     └→ AeronMatchResultPublisher（MDC 广播 + Archive 录制）
 *          └→ TradeSettlementForwarder 消费 Archive → BATCH_SETTLE → Asset Cluster
 * </pre>
 *
 * <p>Kafka 已完全移除：订单状态变更事件通过 Aeron Archive 传递，
 * 不再依赖 Kafka 作为成交结果的传输通道。
 */
@Slf4j
@Component
public class NewOrderEventHandler implements EventHandler {

    @Autowired
    private MemoryManager memoryManager;

    @Autowired
    private OrderMatcherFactory orderMatcherFactory;

    /**
     * Aeron MDC 出站发布者（可选）。
     * required = false：Aeron 未启用时（aeron.enabled=false）Spring 不注入，
     * 撮合引擎静默跳过 Archive 广播（通常仅用于单元测试）。
     */
    @Autowired(required = false)
    private AeronMatchResultPublisher aeronPublisher;

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

            // Aeron MDC：实时广播给 Asset / Risk / Quote（μs 级，非阻塞）
            // Archive 录制由 AeronMatchResultPublisher 自动开启，
            // TradeSettlementForwarder 通过 Archive replay 消费并结算。
            if (aeronPublisher != null) {
                aeronPublisher.send(response);
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
                if (order.getPositionAction() == null) {
                    Position currentPosition = memoryManager.getPosition(order.getUserId(), order.getSymbol());
                    PositionSide currentPositionSide = currentPosition != null ? currentPosition.getSide() : null;
                    order.setPositionAction(PositionAction.determineAction(order.getSide(), currentPositionSide));
                }

                if (order.getPositionAction() != null) {
                    PositionBalance balance = getPositionBalance(order.getSymbol());
                    if (balance != null) {
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

                if (order.getPositionAction() != null && order.getPositionAction().isClose()) {
                    Position position = memoryManager.getPosition(order.getUserId(), order.getSymbol());
                    if (position != null) {
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

                        if (!position.lockPosition(order.getQuantity(), order.getOrderId(), "平仓订单锁定")) {
                            response.setStatus(MatchStatus.REJECTED);
                            response.setErrorMessage("仓位锁定失败");
                            response.setRejectInfo(createRejectInfo(
                                MatchResponse.RejectInfo.RejectType.SYSTEM_ERROR,
                                "仓位锁定失败"
                            ));
                            return response;
                        }

                        memoryManager.updatePosition(position);
                    }
                }
            } else {
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

            if (order.isFullyFilled()) {
                response.setStatus(MatchStatus.SUCCESS);
            } else if (order.isPartiallyFilled()) {
                response.setStatus(MatchStatus.PARTIALLY_FILLED);
            } else {
                response.setStatus(MatchStatus.PENDING);
            }

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

    private List<Trade> executeMatching(Order order, OrderBook orderBook, Symbol symbol) {
        OrderMatcher matcher = orderMatcherFactory.getMatcher(order.getType());
        List<Trade> trades = matcher.matchOrder(order, orderBook, symbol);

        if (symbol.supportsPosition()) {
            for (Trade trade : trades) {
                updatePositionsFromTrade(trade);
            }
        }

        return trades;
    }

    private void updatePositionsFromTrade(Trade trade) {
        Position buyPosition = memoryManager.getOrCreatePosition(trade.getBuyUserId(), trade.getSymbol());
        if (trade.getBuyPositionAction() != null && trade.getBuyPositionAction().isOpen()) {
            buyPosition.openPosition(trade.getQuantity(), trade.getPrice());
        } else if (trade.getBuyPositionAction() != null) {
            buyPosition.closePosition(trade.getQuantity(), trade.getPrice());
        }
        memoryManager.updatePosition(buyPosition);

        Position sellPosition = memoryManager.getOrCreatePosition(trade.getSellUserId(), trade.getSymbol());
        if (trade.getSellPositionAction() != null && trade.getSellPositionAction().isOpen()) {
            sellPosition.openPosition(trade.getQuantity(), trade.getPrice());
        } else if (trade.getSellPositionAction() != null) {
            sellPosition.closePosition(trade.getQuantity(), trade.getPrice());
        }
        memoryManager.updatePosition(sellPosition);
    }

    private MatchResponse.PositionChange calculatePositionChange(Order order, List<Trade> trades) {
        MatchResponse.PositionChange positionChange = new MatchResponse.PositionChange();
        positionChange.setUserId(order.getUserId());
        positionChange.setSymbol(order.getSymbol());
        positionChange.setPositionAction(order.getPositionAction());

        if (order.getSide() == OrderSide.BUY) {
            positionChange.setSide(PositionSide.LONG);
        } else {
            positionChange.setSide(PositionSide.SHORT);
        }

        BigDecimal totalQuantity = trades.stream()
                .map(Trade::getQuantity)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalValue = trades.stream()
                .map(Trade::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

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

        Position position = memoryManager.getPosition(order.getUserId(), order.getSymbol());
        if (position != null) {
            positionChange.setNewQuantity(position.getQuantity().add(positionChangeQuantity));
            positionChange.setNewValue(position.getPositionValue().add(totalValue));
            positionChange.setNewUnrealizedPnl(position.getUnrealizedPnl());
            positionChange.setNewRealizedPnl(position.getRealizedPnl());
            positionChange.setAverageCost(position.getAveragePrice());

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

        if (position.getSide() == PositionSide.LONG) {
            return totalCloseQuantity.multiply(closeAveragePrice.subtract(openAveragePrice));
        } else {
            return totalCloseQuantity.multiply(openAveragePrice.subtract(closeAveragePrice));
        }
    }

    private PositionBalance getPositionBalance(String symbol) {
        Map<Long, Position> allPositions = memoryManager.getAllPositions(symbol);
        if (allPositions == null || allPositions.isEmpty()) {
            return null;
        }

        PositionBalance balance = new PositionBalance(symbol);
        balance.updatePositions(allPositions);
        return balance;
    }

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
