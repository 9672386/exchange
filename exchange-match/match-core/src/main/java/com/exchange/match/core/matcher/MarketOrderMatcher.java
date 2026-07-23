package com.exchange.match.core.matcher;

import com.exchange.match.enums.OrderSide;
import com.exchange.match.enums.OrderType;
import com.exchange.match.model.Trade;

import com.exchange.common.math.FixedPoint;
import com.exchange.match.core.model.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.ArrayList;

/**
 * 市价单匹配器
 * 支持滑点控制和深度限制。
 *
 * <p><b>定点边界</b>:订单薄价/量为 long raw;滑点上下界来自 {@link Symbol} 的 BigDecimal
 * 冷配置,在此转成 long 阈值(买 max 用 {@link RoundingMode#FLOOR},卖 min 用 {@link RoundingMode#CEILING},
 * 保证边界方向不放宽)。
 */
@Slf4j
@Component
public class MarketOrderMatcher extends AbstractOrderMatcher {

    @Override
    protected boolean preMatch(Order order, OrderBook orderBook, Symbol symbol) {
        // 市价单需要检查是否有对手方订单
        if (order.getSide() == OrderSide.BUY) {
            if (orderBook.getSellOrders().isEmpty()) {
                log.warn("市价买单无对手方订单: orderId={}", order.getOrderId());
                return false;
            }
        } else {
            if (orderBook.getBuyOrders().isEmpty()) {
                log.warn("市价卖单无对手方订单: orderId={}", order.getOrderId());
                return false;
            }
        }

        return true;
    }

    @Override
    protected List<Trade> matchBuyOrder(Order buyOrder, OrderBook orderBook, Symbol symbol) {
        List<Trade> trades = new ArrayList<>();
        int depthCount = 0;
        int maxDepth = symbol.getMarketMaxDepth();
        int priceScale = symbol.priceScale();

        // 获取最优卖价（long raw）
        Long bestAskPrice = orderBook.getBestAsk();
        if (bestAskPrice == null) {
            log.warn("市价买单无最优卖价: orderId={}", buyOrder.getOrderId());
            return trades;
        }

        // 计算最大可接受价格（滑点控制，冷配置 BigDecimal → long 阈值，FLOOR 不放宽上界）
        BigDecimal maxAcceptablePrice = symbol.calculateMarketBuyMaxPrice(
                FixedPoint.toBigDecimal(bestAskPrice, priceScale));
        if (maxAcceptablePrice == null) {
            log.warn("市价买单无法计算最大可接受价格: orderId={}", buyOrder.getOrderId());
            return trades;
        }
        long maxAcceptableRaw = FixedPoint.fromBigDecimal(maxAcceptablePrice, priceScale, RoundingMode.FLOOR);

        for (var entry : orderBook.getSellOrders().entrySet()) {
            long sellPrice = entry.getKey();
            java.util.List<Order> sellOrders = entry.getValue();

            // 检查价格是否超过最大可接受价格（滑点控制）
            if (sellPrice > maxAcceptableRaw) {
                log.debug("市价买单价格超过滑点限制: orderId={}, price={}, maxPrice={}",
                        buyOrder.getOrderId(), sellPrice, maxAcceptableRaw);
                break;
            }

            if (depthCount >= maxDepth) {
                log.debug("市价买单达到最大深度限制: orderId={}, depth={}", buyOrder.getOrderId(), maxDepth);
                break;
            }

            for (Order sellOrder : new ArrayList<>(sellOrders)) {
                if (buyOrder.getRemainingQuantity() <= 0) {
                    break;
                }
                if (sellOrder.getRemainingQuantity() <= 0) {
                    continue;
                }

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

            depthCount++;
        }

        return trades;
    }

    @Override
    protected List<Trade> matchSellOrder(Order sellOrder, OrderBook orderBook, Symbol symbol) {
        List<Trade> trades = new ArrayList<>();
        int depthCount = 0;
        int maxDepth = symbol.getMarketMaxDepth();
        int priceScale = symbol.priceScale();

        // 获取最优买价（long raw）
        Long bestBidPrice = orderBook.getBestBid();
        if (bestBidPrice == null) {
            log.warn("市价卖单无最优买价: orderId={}", sellOrder.getOrderId());
            return trades;
        }

        // 计算最小可接受价格（滑点控制，冷配置 BigDecimal → long 阈值，CEILING 不放宽下界）
        BigDecimal minAcceptablePrice = symbol.calculateMarketSellMinPrice(
                FixedPoint.toBigDecimal(bestBidPrice, priceScale));
        if (minAcceptablePrice == null) {
            log.warn("市价卖单无法计算最小可接受价格: orderId={}", sellOrder.getOrderId());
            return trades;
        }
        long minAcceptableRaw = FixedPoint.fromBigDecimal(minAcceptablePrice, priceScale, RoundingMode.CEILING);

        for (var entry : orderBook.getBuyOrders().entrySet()) {
            long buyPrice = entry.getKey();
            java.util.List<Order> buyOrders = entry.getValue();

            // 检查价格是否低于最小可接受价格（滑点控制）
            if (buyPrice < minAcceptableRaw) {
                log.debug("市价卖单价格低于滑点限制: orderId={}, price={}, minPrice={}",
                        sellOrder.getOrderId(), buyPrice, minAcceptableRaw);
                break;
            }

            if (depthCount >= maxDepth) {
                log.debug("市价卖单达到最大深度限制: orderId={}, depth={}", sellOrder.getOrderId(), maxDepth);
                break;
            }

            for (Order buyOrder : new ArrayList<>(buyOrders)) {
                if (sellOrder.getRemainingQuantity() <= 0) {
                    break;
                }
                if (buyOrder.getRemainingQuantity() <= 0) {
                    continue;
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

            depthCount++;
        }

        return trades;
    }

    @Override
    protected void postMatch(Order order, OrderBook orderBook, Symbol symbol, List<Trade> trades) {
        // 市价单不留在订单薄中，无论是否完全成交
        if (!trades.isEmpty()) {
            log.debug("市价单部分成交: orderId={}, filled={}, total={}",
                    order.getOrderId(), order.getFilledQuantity(), order.getQuantity());
        } else {
            log.debug("市价单未成交: orderId={}", order.getOrderId());
        }
        // 不调用父类方法，即不将订单添加到订单薄
    }

    @Override
    public OrderType getSupportedOrderType() {
        return OrderType.MARKET;
    }
}
