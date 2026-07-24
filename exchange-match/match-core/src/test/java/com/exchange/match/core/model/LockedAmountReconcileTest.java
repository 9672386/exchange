package com.exchange.match.core.model;

import com.exchange.match.core.matcher.LimitOrderMatcher;
import com.exchange.match.enums.OrderSide;
import com.exchange.match.enums.OrderType;
import com.exchange.common.math.FixedPoint;
import com.exchange.match.model.Trade;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 冻结额随单模型对账测试(设计 §1):验证成交按实际结算额递减冻结残余,
 * "解冻额 = 冻结额 − 已结算",覆盖两种残余来源:
 * <ol>
 *   <li>部分成交:未成交部分的冻结留作残余(撤单时解冻)。</li>
 *   <li>完全成交但吃单价更优:价格改善的多冻部分留作残余(完成时解冻)。</li>
 * </ol>
 */
public class LockedAmountReconcileTest {

    private static final int P = 2;   // priceScale
    private static final int B = 4;   // baseScale
    private static final int Q = 8;   // quoteScale

    private Symbol spotSymbol() {
        Symbol s = new Symbol();
        s.setSymbol("BTC/USDT");
        s.setBaseCurrency("BTC");
        s.setQuoteCurrency("USDT");
        s.setPricePrecision(P);
        s.setQuantityPrecision(B);
        s.setQuoteScale(Q);
        s.setFeeRate(new BigDecimal("0.001")); // 0.1%
        return s;
    }

    private Order order(String id, OrderSide side, String price, String qty) {
        Order o = new Order();
        o.setOrderId(id);
        o.setUserId(1L);
        o.setSymbol("BTC/USDT");
        o.setSide(side);
        o.setType(OrderType.LIMIT);
        o.setStatus(OrderStatus.ACTIVE);
        o.setPrice(FixedPoint.fromBigDecimal(new BigDecimal(price), P, RoundingMode.DOWN));
        long q = FixedPoint.fromBigDecimal(new BigDecimal(qty), B, RoundingMode.DOWN);
        o.setQuantity(q);
        o.setRemainingQuantity(q);
        return o;
    }

    private long qraw(String v) { return FixedPoint.fromBigDecimal(new BigDecimal(v), Q, RoundingMode.UP); }

    @Test
    public void partialFill_residualCoversUnfilled() {
        Symbol sym = spotSymbol();
        OrderBook book = new OrderBook("BTC/USDT");

        // 挂单:卖 1.0 @ 100.00
        book.addOrder(order("S1", OrderSide.SELL, "100.00", "1.0"));

        // 吃单(taker):买 2.0 @ 100.00,冻结 quote = 2×100×1.001 = 200.2
        Order buy = order("B1", OrderSide.BUY, "100.00", "2.0");
        buy.setLockedAsset("USDT");
        long locked = qraw("200.2");
        buy.setLockedAmount(locked);
        buy.setLockedRemaining(locked);

        List<Trade> trades = new LimitOrderMatcher().matchOrder(buy, book, sym);
        assertEquals(1, trades.size());

        // 成交 1.0 @ 100:消耗 = 100 + 0.1(手续费) = 100.1;残余 = 200.2 − 100.1 = 100.1
        assertEquals(qraw("100.1"), buy.getLockedRemaining(),
                "部分成交后残余应恰好覆盖未成交的 1.0");
        assertFalse(buy.isFullyFilled());
    }

    @Test
    public void fullFillAtBetterPrice_residualIsPriceImprovement() {
        Symbol sym = spotSymbol();
        OrderBook book = new OrderBook("BTC/USDT");

        // 挂单:卖 1.0 @ 99.00(优于买单限价)
        book.addOrder(order("S2", OrderSide.SELL, "99.00", "1.0"));

        // 吃单:买 1.0 @ 100.00,按限价冻结 = 1×100×1.001 = 100.1
        Order buy = order("B2", OrderSide.BUY, "100.00", "1.0");
        buy.setLockedAsset("USDT");
        long locked = qraw("100.1");
        buy.setLockedAmount(locked);
        buy.setLockedRemaining(locked);

        List<Trade> trades = new LimitOrderMatcher().matchOrder(buy, book, sym);
        assertEquals(1, trades.size());
        assertTrue(buy.isFullyFilled());

        // 实际成交 1.0 @ 99:消耗 = 99 + 0.099 = 99.099;残余 = 100.1 − 99.099 = 1.001(价格改善多冻)
        assertEquals(qraw("1.001"), buy.getLockedRemaining(),
                "完全成交但价优时,残余=价格改善多冻部分,应在完成时解冻");
    }

    @Test
    public void consumeLocked_clampsAtZero() {
        Order o = new Order();
        o.setLockedRemaining(100L);
        o.consumeLocked(150L);            // 消耗超过残余
        assertEquals(0L, o.getLockedRemaining(), "残余 clamp 到 0,不为负");
        o.consumeLocked(50L);             // 已为 0,再消耗无效
        assertEquals(0L, o.getLockedRemaining());
    }
}
