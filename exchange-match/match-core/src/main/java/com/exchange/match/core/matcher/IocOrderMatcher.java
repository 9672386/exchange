package com.exchange.match.core.matcher;

import com.exchange.match.core.model.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

/**
 * IOC（Immediate or Cancel）订单匹配器
 * 立即成交可成交部分，剩余部分取消
 */
@Slf4j
@Component
public class IocOrderMatcher extends AbstractOrderMatcher {
    
    @Override
    protected void postMatch(Order order, OrderBook orderBook, Symbol symbol, List<Trade> trades) {
        // IOC订单不留在订单薄中，无论是否完全成交
        if (!trades.isEmpty()) {
            log.debug("IOC订单部分成交: orderId={}, filled={}, total={}", 
                    order.getOrderId(), order.getFilledQuantity(), order.getQuantity());
        } else {
            log.debug("IOC订单未成交: orderId={}", order.getOrderId());
        }
        // 不调用父类方法，即不将订单添加到订单薄
    }
    
    @Override
    public OrderType getSupportedOrderType() {
        return OrderType.IOC;
    }
} 