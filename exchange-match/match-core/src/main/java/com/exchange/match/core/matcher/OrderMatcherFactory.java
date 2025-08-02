package com.exchange.match.core.matcher;

import com.exchange.match.core.model.OrderType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 订单匹配工厂
 * 根据订单类型选择合适的匹配器
 */
@Slf4j
@Component
public class OrderMatcherFactory {
    
    private final Map<OrderType, OrderMatcher> matchers;
    
    @Autowired
    public OrderMatcherFactory(List<OrderMatcher> matcherList) {
        // 将匹配器按订单类型分组
        this.matchers = matcherList.stream()
                .collect(Collectors.toMap(
                        OrderMatcher::getSupportedOrderType,
                        matcher -> matcher
                ));
        
        log.info("订单匹配工厂初始化完成，支持的订单类型: {}", matchers.keySet());
    }
    
    /**
     * 获取订单匹配器
     * @param orderType 订单类型
     * @return 匹配器
     */
    public OrderMatcher getMatcher(OrderType orderType) {
        OrderMatcher matcher = matchers.get(orderType);
        if (matcher == null) {
            throw new IllegalArgumentException("不支持的订单类型: " + orderType);
        }
        return matcher;
    }
    
    /**
     * 检查是否支持指定订单类型
     * @param orderType 订单类型
     * @return 是否支持
     */
    public boolean supports(OrderType orderType) {
        return matchers.containsKey(orderType);
    }
    
    /**
     * 获取所有支持的订单类型
     * @return 支持的订单类型列表
     */
    public List<OrderType> getSupportedOrderTypes() {
        return List.copyOf(matchers.keySet());
    }
} 