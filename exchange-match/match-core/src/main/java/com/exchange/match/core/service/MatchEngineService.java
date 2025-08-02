package com.exchange.match.core.service;

import com.exchange.match.core.memory.MemoryStats;
import com.exchange.match.core.model.*;

import java.math.BigDecimal;
import java.util.List;

/**
 * 撮合引擎服务接口
 */
public interface MatchEngineService {
    
    /**
     * 提交订单
     */
    MatchResponse submitOrder(Order order);
    
    /**
     * 撤销订单
     */
    MatchResponse cancelOrder(String orderId, Long userId);
    
    /**
     * 获取订单
     */
    Order getOrder(String orderId);
    
    /**
     * 获取用户订单
     */
    List<Order> getUserOrders(Long userId, String symbol);
    
    /**
     * 获取订单薄深度
     */
    OrderBookSnapshot getOrderBookSnapshot(String symbol);
    
    /**
     * 获取仓位
     */
    Position getPosition(Long userId, String symbol);
    
    /**
     * 获取用户所有仓位
     */
    List<Position> getUserPositions(Long userId);
    
    /**
     * 更新仓位未实现盈亏
     */
    void updatePositionPnl(Long userId, String symbol, BigDecimal currentPrice);
    
    /**
     * 添加标的
     */
    void addSymbol(Symbol symbol);
    
    /**
     * 获取标的
     */
    Symbol getSymbol(String symbol);
    
    /**
     * 获取所有活跃标的
     */
    List<Symbol> getActiveSymbols();
    
    /**
     * 获取内存统计信息
     */
    MemoryStats getMemoryStats();
    
    /**
     * 清空指定标的的所有数据
     */
    void clearSymbolData(String symbol);
    
    /**
     * 清空所有数据
     */
    void clearAllData();
} 