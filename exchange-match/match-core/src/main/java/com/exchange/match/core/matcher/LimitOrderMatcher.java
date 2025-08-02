package com.exchange.match.core.matcher;

import com.exchange.match.core.model.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

/**
 * 限价单匹配器
 * 支持部分成交，剩余部分留在订单薄中
 */
@Slf4j
@Component
public class LimitOrderMatcher extends AbstractOrderMatcher {
    

    
    @Override
    public OrderType getSupportedOrderType() {
        return OrderType.LIMIT;
    }
} 