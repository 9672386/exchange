package com.exchange.match.core.service.impl;

import com.exchange.match.model.MatchResponse;
import com.exchange.match.enums.MatchStatus;
import com.exchange.match.enums.OrderSide;
import com.exchange.match.enums.PositionAction;
import com.exchange.match.enums.PositionSide;
import com.exchange.match.model.Trade;

import com.exchange.match.core.memory.MemoryManager;
import com.exchange.match.core.memory.MemoryStats;
import com.exchange.match.core.matcher.OrderMatcher;
import com.exchange.match.core.matcher.OrderMatcherFactory;
import com.exchange.match.core.model.*;
import com.exchange.match.core.service.MatchEngineService;
import com.exchange.common.math.FixedPoint;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.UUID;

/**
 * 撮合引擎服务实现类
 */
@Slf4j
@Service
public class MatchEngineServiceImpl implements MatchEngineService {
    
    @Autowired
    private MemoryManager memoryManager;
    
    @Autowired
    private OrderMatcherFactory orderMatcherFactory;

    // ===================== 定点边界换算 (Order long ↔ MatchResponse/Symbol BigDecimal) =====================

    /** 该 symbol 的价格 scale(找不到 symbol 兜底 8)。 */
    private int priceScaleOf(String symbolCode) {
        Symbol s = memoryManager.getSymbol(symbolCode);
        return s != null ? s.priceScale() : 8;
    }

    /** 该 symbol 的数量/base scale(找不到 symbol 兜底 8)。 */
    private int baseScaleOf(String symbolCode) {
        Symbol s = memoryManager.getSymbol(symbolCode);
        return s != null ? s.baseScale() : 8;
    }

    /** 价格 raw → BigDecimal。 */
    private BigDecimal priceBd(String symbolCode, long raw) {
        return FixedPoint.toBigDecimal(raw, priceScaleOf(symbolCode));
    }

    /** 数量 raw → BigDecimal。 */
    private BigDecimal qtyBd(String symbolCode, long raw) {
        return FixedPoint.toBigDecimal(raw, baseScaleOf(symbolCode));
    }

    /**
     * 对订单残余冻结额构造一条解冻指令并清零 lockedRemaining;无残余返回 null。
     * 单撤/批量撤单共用,保证"解冻额 = 冻结额 − 已结算"。
     */
    private MatchResponse.CancelRelease buildRelease(Order order) {
        long releaseRaw = order.getLockedRemaining();
        if (releaseRaw <= 0 || order.getLockedAsset() == null) return null;
        Symbol relSym = memoryManager.getSymbol(order.getSymbol());
        int lockScale = (relSym != null && order.getLockedAsset().equals(relSym.getQuoteCurrency()))
                ? relSym.quoteScaleOrDefault()
                : (relSym != null && order.getLockedAsset().equals(relSym.getBaseCurrency()))
                    ? relSym.baseScale() : 8;
        String acct = (relSym != null && relSym.supportsPosition()) ? "FUTURES" : "SPOT";
        MatchResponse.CancelRelease rel = new MatchResponse.CancelRelease(
                order.getUserId(), acct, order.getLockedAsset(),
                FixedPoint.toBigDecimal(releaseRaw, lockScale),
                "RELEASE:" + order.getOrderId(), order.getOrderId());
        order.setLockedRemaining(0L);
        return rel;
    }

    /**
     * 撤销指定用户在所有标的上的全部挂单(确定性:按 orderId 升序)。
     * 每笔产出解冻指令,聚合到返回的 {@link MatchResponse#getReleases()}。
     */
    @Override
    public MatchResponse cancelUserOrders(Long userId) {
        MatchResponse resp = new MatchResponse();
        resp.setUserId(userId);
        resp.setStatus(MatchStatus.CANCELLED);

        List<Order> targets = new ArrayList<>();
        for (OrderBook ob : memoryManager.getAllOrderBooks().values()) {
            targets.addAll(ob.getUserOrders(userId));
        }
        targets.sort(java.util.Comparator.comparing(Order::getOrderId));

        List<MatchResponse.CancelRelease> releases = new ArrayList<>();
        for (Order o : targets) {
            OrderBook ob = memoryManager.getOrderBook(o.getSymbol());
            o.cancel();
            if (ob != null) ob.removeOrder(o.getOrderId());
            MatchResponse.CancelRelease rel = buildRelease(o);
            if (rel != null) releases.add(rel);
        }
        resp.setReleases(releases);
        log.info("[MatchEngine] CANCEL_USER userId={} canceled={} releases={}",
                userId, targets.size(), releases.size());
        return resp;
    }

    /**
     * 撤销指定标的的全部挂单(确定性:按 orderId 升序)。用于下架前清空订单簿。
     */
    @Override
    public MatchResponse cancelSymbolOrders(String symbol) {
        MatchResponse resp = new MatchResponse();
        resp.setSymbol(symbol);
        resp.setStatus(MatchStatus.CANCELLED);

        OrderBook ob = memoryManager.getOrderBook(symbol);
        if (ob == null) {
            resp.setReleases(new ArrayList<>());
            return resp;
        }

        List<Order> targets = new ArrayList<>();
        ob.getBuyOrders().values().forEach(targets::addAll);
        ob.getSellOrders().values().forEach(targets::addAll);
        targets.sort(java.util.Comparator.comparing(Order::getOrderId));

        List<MatchResponse.CancelRelease> releases = new ArrayList<>();
        for (Order o : targets) {
            o.cancel();
            ob.removeOrder(o.getOrderId());
            MatchResponse.CancelRelease rel = buildRelease(o);
            if (rel != null) releases.add(rel);
        }
        resp.setReleases(releases);
        log.info("[MatchEngine] CANCEL_SYMBOL symbol={} canceled={} releases={}",
                symbol, targets.size(), releases.size());
        return resp;
    }

    @Override
    public MatchResponse submitOrder(Order order) {
        MatchResponse response = new MatchResponse();
        response.setOrderId(order.getOrderId());
        response.setUserId(order.getUserId());
        response.setSymbol(order.getSymbol());
        response.setSide(order.getSide());
        response.setOrderType(order.getType());
        response.setOrderPrice(priceBd(order.getSymbol(), order.getPrice()));
        response.setOrderQuantity(qtyBd(order.getSymbol(), order.getQuantity()));

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
            
            // 验证订单参数（Order 为 long raw，转 BigDecimal 交给 Symbol 冷校验）
            int priceScale = symbol.priceScale();
            int baseScale  = symbol.baseScale();
            BigDecimal priceBdVal = FixedPoint.toBigDecimal(order.getPrice(), priceScale);
            BigDecimal qtyBdVal   = FixedPoint.toBigDecimal(order.getQuantity(), baseScale);

            if (!symbol.isValidPrice(priceBdVal)) {
                response.setStatus(MatchStatus.REJECTED);
                response.setErrorMessage("价格无效: " + priceBdVal);
                response.setRejectInfo(createRejectInfo(
                    MatchResponse.RejectInfo.RejectType.INVALID_PRICE,
                    "价格无效: " + priceBdVal
                ));
                return response;
            }

            if (!symbol.isValidQuantity(qtyBdVal)) {
                response.setStatus(MatchStatus.REJECTED);
                response.setErrorMessage("数量无效: " + qtyBdVal);
                response.setRejectInfo(createRejectInfo(
                    MatchResponse.RejectInfo.RejectType.INVALID_QUANTITY,
                    "数量无效: " + qtyBdVal
                ));
                return response;
            }

            // 格式化价格和数量（Symbol 冷格式化返回 BigDecimal，转回 long raw；DOWN 不放大金额）
            order.setPrice(FixedPoint.fromBigDecimal(symbol.formatPrice(priceBdVal), priceScale, RoundingMode.DOWN));
            order.setQuantity(FixedPoint.fromBigDecimal(symbol.formatQuantity(qtyBdVal), baseScale, RoundingMode.DOWN));
            order.setRemainingQuantity(order.getQuantity());
            
            // 根据交易类型设置开平仓动作
            if (symbol.supportsPosition()) {
                // 合约交易：如果没有设置开平仓动作，则自动判断
                if (order.getPositionAction() == null) {
                    Position currentPosition = memoryManager.getPosition(order.getUserId(), order.getSymbol());
                    PositionSide currentPositionSide = currentPosition != null ? currentPosition.getSide() : null;
                    order.setPositionAction(PositionAction.determineAction(order.getSide(), currentPositionSide));
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
            response.setMatchQuantity(FixedPoint.toBigDecimal(order.getFilledQuantity(), baseScale));
            response.setRemainingQuantity(FixedPoint.toBigDecimal(order.getRemainingQuantity(), baseScale));
            
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

            // 完全成交但吃单价优于限价时,残余冻结额需解冻(否则价格改善的剩余冻结泄漏)。
            // 仅 taker 会有此残余(maker 按自身价成交,消耗=冻结)。
            if (order.isFullyFilled() && order.getLockedRemaining() > 0) {
                MatchResponse.CancelRelease rel = buildRelease(order);
                if (rel != null) {
                    List<MatchResponse.CancelRelease> rs = response.getReleases() != null
                            ? response.getReleases() : new java.util.ArrayList<>();
                    rs.add(rel);
                    response.setReleases(rs);
                }
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
            
            log.info("订单提交成功: orderId={}, symbol={}, status={}, filled={}, remaining={}",
                    order.getOrderId(), order.getSymbol(), response.getStatus(), 
                    response.getMatchQuantity(), response.getRemainingQuantity());
            
        } catch (Exception e) {
            log.error("订单提交失败: orderId={}", order.getOrderId(), e);
            response.setStatus(MatchStatus.REJECTED);
            response.setErrorMessage("订单提交失败: " + e.getMessage());
            response.setRejectInfo(createRejectInfo(
                MatchResponse.RejectInfo.RejectType.SYSTEM_ERROR,
                "订单提交失败: " + e.getMessage()
            ));
        }
        
        return response;
    }
    
    @Override
    public MatchResponse cancelOrder(String orderId, Long userId) {
        MatchResponse response = new MatchResponse();
        response.setOrderId(orderId);
        response.setUserId(userId);
        
        try {
            // 查找订单
            OrderBook orderBook = findOrderBookByOrderId(orderId);
            if (orderBook == null) {
                response.setStatus(MatchStatus.REJECTED);
                response.setErrorMessage("订单不存在: " + orderId);
                response.setRejectInfo(createRejectInfo(
                    MatchResponse.RejectInfo.RejectType.SYSTEM_ERROR,
                    "订单不存在: " + orderId
                ));
                return response;
            }
            
            Order order = orderBook.getOrder(orderId);
            if (order == null) {
                response.setStatus(MatchStatus.REJECTED);
                response.setErrorMessage("订单不存在: " + orderId);
                response.setRejectInfo(createRejectInfo(
                    MatchResponse.RejectInfo.RejectType.SYSTEM_ERROR,
                    "订单不存在: " + orderId
                ));
                return response;
            }
            
            // 验证用户权限
            if (!order.getUserId().equals(userId)) {
                response.setStatus(MatchStatus.REJECTED);
                response.setErrorMessage("用户权限不足: " + userId);
                response.setRejectInfo(createRejectInfo(
                    MatchResponse.RejectInfo.RejectType.SYSTEM_ERROR,
                    "用户权限不足: " + userId
                ));
                return response;
            }
            
            // 设置撤单信息
            MatchResponse.CancelInfo cancelInfo = new MatchResponse.CancelInfo();
            cancelInfo.setCancelUserId(userId);
            cancelInfo.setCancelQuantity(qtyBd(order.getSymbol(), order.getRemainingQuantity()));
            cancelInfo.setCancelReason("用户主动撤单");
            // 将OrderStatus转换为MatchStatus
            MatchStatus previousStatus = convertOrderStatusToMatchStatus(order.getStatus());
            cancelInfo.setPreviousStatus(previousStatus);
            response.setCancelInfo(cancelInfo);
            
            // 取消订单
            order.cancel();
            orderBook.updateOrder(order);

            // 撮合驱动解冻:对残余冻结额产出一条 UNFREEZE 指令(经可靠结算流到资产服务)
            MatchResponse.CancelRelease rel = buildRelease(order);
            if (rel != null) {
                response.setReleases(new java.util.ArrayList<>(java.util.List.of(rel)));
            }
            
            // 设置响应信息
            response.setStatus(MatchStatus.CANCELLED);
            response.setSymbol(order.getSymbol());
            response.setSide(order.getSide());
            response.setOrderType(order.getType());
            response.setOrderPrice(priceBd(order.getSymbol(), order.getPrice()));
            response.setOrderQuantity(qtyBd(order.getSymbol(), order.getQuantity()));
            response.setMatchQuantity(qtyBd(order.getSymbol(), order.getFilledQuantity()));
            response.setRemainingQuantity(qtyBd(order.getSymbol(), order.getRemainingQuantity()));

            log.info("订单取消成功: orderId={}, userId={}, cancelQuantity={}",
                    orderId, userId, cancelInfo.getCancelQuantity());
            
        } catch (Exception e) {
            log.error("订单取消失败: orderId={}, userId={}", orderId, userId, e);
            response.setStatus(MatchStatus.REJECTED);
            response.setErrorMessage("订单取消失败: " + e.getMessage());
            response.setRejectInfo(createRejectInfo(
                MatchResponse.RejectInfo.RejectType.SYSTEM_ERROR,
                "订单取消失败: " + e.getMessage()
            ));
        }
        
        return response;
    }
    
    @Override
    public Order getOrder(String orderId) {
        OrderBook orderBook = findOrderBookByOrderId(orderId);
        return orderBook != null ? orderBook.getOrder(orderId) : null;
    }
    
    @Override
    public List<Order> getUserOrders(Long userId, String symbol) {
        OrderBook orderBook = memoryManager.getOrderBook(symbol);
        return orderBook != null ? orderBook.getUserOrders(userId) : new ArrayList<>();
    }
    
    @Override
    public OrderBookSnapshot getOrderBookSnapshot(String symbol) {
        OrderBook orderBook = memoryManager.getOrderBook(symbol);
        return orderBook != null ? orderBook.getSnapshot() : null;
    }
    
    @Override
    public Position getPosition(Long userId, String symbol) {
        return memoryManager.getPosition(userId, symbol);
    }
    
    @Override
    public List<Position> getUserPositions(Long userId) {
        return memoryManager.getUserPositions(userId);
    }
    
    @Override
    public void updatePositionPnl(Long userId, String symbol, BigDecimal currentPrice) {
        Position position = memoryManager.getPosition(userId, symbol);
        if (position != null) {
            position.updateUnrealizedPnl(currentPrice);
            memoryManager.updatePosition(position);
        }
    }
    
    @Override
    public void addSymbol(Symbol symbol) {
        memoryManager.addSymbol(symbol);
    }
    
    @Override
    public Symbol getSymbol(String symbol) {
        return memoryManager.getSymbol(symbol);
    }
    
    @Override
    public List<Symbol> getActiveSymbols() {
        return memoryManager.getActiveSymbols();
    }
    
    @Override
    public com.exchange.match.core.memory.MemoryStats getMemoryStats() {
        return memoryManager.getMemoryStats();
    }
    
    @Override
    public void clearSymbolData(String symbol) {
        memoryManager.removeSymbol(symbol);
    }
    
    @Override
    public void clearAllData() {
        memoryManager.clearAll();
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
        if (trade.getBuyPositionAction().isOpen()) {
            buyPosition.openPosition(trade.getQuantity(), trade.getPrice());
        } else {
            buyPosition.closePosition(trade.getQuantity(), trade.getPrice());
        }
        memoryManager.updatePosition(buyPosition);
        
        // 更新卖方仓位
        Position sellPosition = memoryManager.getOrCreatePosition(trade.getSellUserId(), trade.getSymbol());
        if (trade.getSellPositionAction().isOpen()) {
            sellPosition.openPosition(trade.getQuantity(), trade.getPrice());
        } else {
            sellPosition.closePosition(trade.getQuantity(), trade.getPrice());
        }
        memoryManager.updatePosition(sellPosition);
    }
    
    /**
     * 根据订单ID查找订单薄
     */
    private OrderBook findOrderBookByOrderId(String orderId) {
        for (OrderBook orderBook : memoryManager.getAllOrderBooks().values()) {
            if (orderBook.getOrder(orderId) != null) {
                return orderBook;
            }
        }
        return null;
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
            // 如果开平仓操作无效，抛出异常，让调用方处理
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
            if (order.getPositionAction().isClose()) {
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
        if (!order.getPositionAction().isClose()) {
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
     * 将OrderStatus转换为MatchStatus
     */
    private MatchStatus convertOrderStatusToMatchStatus(OrderStatus orderStatus) {
        switch (orderStatus) {
            case PENDING:
                return MatchStatus.PENDING;
            case PARTIALLY_FILLED:
                return MatchStatus.PARTIALLY_FILLED;
            case FILLED:
                return MatchStatus.SUCCESS;
            case CANCELLED:
                return MatchStatus.CANCELLED;
            case REJECTED:
                return MatchStatus.REJECTED;
            default:
                return MatchStatus.PENDING;
        }
    }
} 