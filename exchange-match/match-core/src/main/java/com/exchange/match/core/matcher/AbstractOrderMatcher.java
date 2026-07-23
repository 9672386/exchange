package com.exchange.match.core.matcher;

import com.exchange.match.enums.OrderSide;
import com.exchange.match.enums.PositionAction;
import com.exchange.match.model.Trade;
import com.exchange.match.enums.TradeSide;

import com.exchange.common.id.SnowflakeId;
import com.exchange.common.math.FixedPoint;
import com.exchange.match.core.model.*;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * 抽象订单匹配器基类
 * 提取通用的撮合逻辑，子类只需要实现特定的订单类型逻辑。
 *
 * <p><b>定点</b>:订单价/量为 long raw(priceScale/baseScale);金额/手续费用
 * {@link Symbol#calcAmountRaw}/{@link Symbol#calcFeeRaw} 定点计算,生成 {@link Trade}
 * 时经 {@link FixedPoint#toBigDecimal} 转回 BigDecimal(对外输出契约不变)。
 */
@Slf4j
public abstract class AbstractOrderMatcher implements OrderMatcher {

    @Override
    public List<Trade> matchOrder(Order order, OrderBook orderBook, Symbol symbol) {
        List<Trade> trades = new ArrayList<>();

        // 子类特定的预处理逻辑
        if (!preMatch(order, orderBook, symbol)) {
            return trades;
        }

        // 执行通用撮合逻辑
        if (order.getSide() == OrderSide.BUY) {
            trades = matchBuyOrder(order, orderBook, symbol);
        } else {
            trades = matchSellOrder(order, orderBook, symbol);
        }

        // 子类特定的后处理逻辑
        postMatch(order, orderBook, symbol, trades);

        return trades;
    }

    /**
     * 撮合前预处理
     */
    protected boolean preMatch(Order order, OrderBook orderBook, Symbol symbol) {
        return true;
    }

    /**
     * 撮合后处理
     */
    protected void postMatch(Order order, OrderBook orderBook, Symbol symbol, List<Trade> trades) {
        // 默认实现：将订单添加到订单薄（如果还有剩余数量）
        if (order.getRemainingQuantity() > 0) {
            orderBook.addOrder(order);
        }
    }

    /**
     * 买单撮合（通用逻辑）
     */
    protected List<Trade> matchBuyOrder(Order buyOrder, OrderBook orderBook, Symbol symbol) {
        List<Trade> trades = new ArrayList<>();

        for (var entry : orderBook.getSellOrders().entrySet()) {
            long sellPrice = entry.getKey();
            java.util.List<Order> sellOrders = entry.getValue();

            // 检查价格是否匹配
            if (buyOrder.getPrice() < sellPrice) {
                break; // 价格不匹配，停止撮合
            }

            for (Order sellOrder : new ArrayList<>(sellOrders)) {
                if (buyOrder.getRemainingQuantity() <= 0) {
                    break; // 买单已完全成交
                }
                if (sellOrder.getRemainingQuantity() <= 0) {
                    continue; // 卖单已完全成交
                }

                // 计算成交数量
                long matchQuantity = Math.min(buyOrder.getRemainingQuantity(),
                        sellOrder.getRemainingQuantity());

                Trade trade = createTrade(buyOrder, sellOrder, sellPrice, matchQuantity, symbol);
                trades.add(trade);

                buyOrder.updateFilledQuantity(matchQuantity);
                sellOrder.updateFilledQuantity(matchQuantity);

                orderBook.updateOrder(sellOrder);
                orderBook.updateLastPrice(sellPrice);
                orderBook.addVolume(matchQuantity);
            }
        }

        return trades;
    }

    /**
     * 卖单撮合（通用逻辑）
     */
    protected List<Trade> matchSellOrder(Order sellOrder, OrderBook orderBook, Symbol symbol) {
        List<Trade> trades = new ArrayList<>();

        for (var entry : orderBook.getBuyOrders().entrySet()) {
            long buyPrice = entry.getKey();
            java.util.List<Order> buyOrders = entry.getValue();

            // 检查价格是否匹配
            if (sellOrder.getPrice() > buyPrice) {
                break; // 价格不匹配，停止撮合
            }

            for (Order buyOrder : new ArrayList<>(buyOrders)) {
                if (sellOrder.getRemainingQuantity() <= 0) {
                    break; // 卖单已完全成交
                }
                if (buyOrder.getRemainingQuantity() <= 0) {
                    continue; // 买单已完全成交
                }

                long matchQuantity = Math.min(sellOrder.getRemainingQuantity(),
                        buyOrder.getRemainingQuantity());

                Trade trade = createTrade(buyOrder, sellOrder, buyPrice, matchQuantity, symbol);
                trades.add(trade);

                buyOrder.updateFilledQuantity(matchQuantity);
                sellOrder.updateFilledQuantity(matchQuantity);

                orderBook.updateOrder(buyOrder);
                orderBook.updateLastPrice(buyPrice);
                orderBook.addVolume(matchQuantity);
            }
        }

        return trades;
    }

    /**
     * 创建成交记录（通用逻辑）
     *
     * @param price    成交价 raw(priceScale)
     * @param quantity 成交量 raw(baseScale)
     */
    protected Trade createTrade(Order buyOrder, Order sellOrder, long price,
                                long quantity, Symbol symbol) {
        Trade trade = new Trade();
        trade.setTradeId(SnowflakeId.nextIdStr());
        trade.setSymbol(buyOrder.getSymbol());
        trade.setBuyOrderId(buyOrder.getOrderId());
        trade.setSellOrderId(sellOrder.getOrderId());
        trade.setBuyUserId(buyOrder.getUserId());
        trade.setSellUserId(sellOrder.getUserId());
        trade.setPrice(FixedPoint.toBigDecimal(price, symbol.priceScale()));
        trade.setQuantity(FixedPoint.toBigDecimal(quantity, symbol.baseScale()));
        trade.setSide(TradeSide.BUY);

        // 设置开平仓动作（仅合约交易）
        if (symbol.supportsPosition()) {
            trade.setBuyPositionAction(buyOrder.getPositionAction());
            trade.setSellPositionAction(sellOrder.getPositionAction());

            trade.setBuyPositionChange(calculatePositionChange(buyOrder, quantity, symbol));
            trade.setSellPositionChange(calculatePositionChange(sellOrder, quantity, symbol));
        }

        // 定点计算成交金额与手续费,再转 BigDecimal 填入 Trade
        long amountRaw = symbol.calcAmountRaw(price, quantity);
        long feeRaw    = symbol.calcFeeRaw(amountRaw);
        int quoteScale = symbol.quoteScaleOrDefault();
        trade.setAmount(FixedPoint.toBigDecimal(amountRaw, quoteScale));
        trade.setBuyFee(FixedPoint.toBigDecimal(feeRaw, quoteScale));
        trade.setSellFee(FixedPoint.toBigDecimal(feeRaw, quoteScale));

        return trade;
    }

    /**
     * 计算仓位变化(baseScale raw → BigDecimal,走既有 PositionAction BigDecimal 路径)
     */
    private BigDecimal calculatePositionChange(Order order, long quantity, Symbol symbol) {
        if (order.getPositionAction() == null) {
            return BigDecimal.ZERO;
        }

        BigDecimal qtyBd = FixedPoint.toBigDecimal(quantity, symbol.baseScale());
        PositionAction.PositionChangeResult result = PositionAction.calculatePositionChange(
                order.getSide(),
                order.getPositionAction(),
                null, // 暂时设为null，在service层会重新计算
                qtyBd);

        if (!result.isValid()) {
            throw new IllegalArgumentException("无效的开平仓操作: " + result.getReason());
        }

        return result.getQuantityChange();
    }

    /**
     * 计算可成交的总数量（通用逻辑，baseScale raw）
     */
    protected long calculateAvailableQuantity(Order order, OrderBook orderBook) {
        long totalAvailable = 0L;

        if (order.getSide() == OrderSide.BUY) {
            // 买单：计算卖单队列中价格<=买单价格的总数量
            for (var entry : orderBook.getSellOrders().entrySet()) {
                long sellPrice = entry.getKey();
                if (order.getPrice() >= sellPrice) {
                    java.util.List<Order> sellOrders = entry.getValue();
                    for (Order sellOrder : sellOrders) {
                        if (sellOrder.getRemainingQuantity() > 0) {
                            totalAvailable = Math.addExact(totalAvailable, sellOrder.getRemainingQuantity());
                        }
                    }
                }
            }
        } else {
            // 卖单：计算买单队列中价格>=卖单价格的总数量
            for (var entry : orderBook.getBuyOrders().entrySet()) {
                long buyPrice = entry.getKey();
                if (order.getPrice() <= buyPrice) {
                    java.util.List<Order> buyOrders = entry.getValue();
                    for (Order buyOrder : buyOrders) {
                        if (buyOrder.getRemainingQuantity() > 0) {
                            totalAvailable = Math.addExact(totalAvailable, buyOrder.getRemainingQuantity());
                        }
                    }
                }
            }
        }

        return totalAvailable;
    }
}
