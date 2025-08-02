package com.exchange.match.core.matcher;

import com.exchange.match.core.model.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

/**
 * FOK（Fill or Kill）订单匹配器
 * 要么全部成交，要么全部取消
 */
@Slf4j
@Component
public class FokOrderMatcher extends AbstractOrderMatcher {
    
    @Override
    protected boolean preMatch(Order order, OrderBook orderBook, Symbol symbol) {
        // 计算可成交的总数量
        BigDecimal availableQuantity = calculateAvailableQuantity(order, orderBook);
        
        // 检查是否可以全部成交
        if (availableQuantity.compareTo(order.getQuantity()) < 0) {
            log.debug("FOK订单无法全部成交: orderId={}, required={}, available={}", 
                    order.getOrderId(), order.getQuantity(), availableQuantity);
            return false; // 不继续撮合
        }
        
        return true;
    }
    
    @Override
    protected void postMatch(Order order, OrderBook orderBook, Symbol symbol, List<Trade> trades) {
        // FOK订单不留在订单薄中，无论是否完全成交
        log.debug("FOK订单撮合完成: orderId={}, filled={}, total={}", 
                order.getOrderId(), order.getFilledQuantity(), order.getQuantity());
        // 不调用父类方法，即不将订单添加到订单薄
    }
    
    @Override
    public OrderType getSupportedOrderType() {
        return OrderType.FOK;
    }
} 