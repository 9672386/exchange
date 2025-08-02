package com.exchange.match.core.memory;

import com.exchange.match.core.model.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 内存管理器
 * 管理订单薄、仓位和标的的内存数据
 */
@Slf4j
@Component
public class MemoryManager {
    
    /**
     * 订单薄映射（symbol -> OrderBook）
     */
    private final Map<String, OrderBook> orderBooks = new ConcurrentHashMap<>();
    
    /**
     * 仓位映射（userId_symbol -> Position）
     */
    private final Map<String, Position> positions = new ConcurrentHashMap<>();
    
    /**
     * 标的映射（symbol -> Symbol）
     */
    private final Map<String, Symbol> symbols = new ConcurrentHashMap<>();
    
    /**
     * 获取或创建订单薄
     */
    public OrderBook getOrCreateOrderBook(String symbol) {
        return orderBooks.computeIfAbsent(symbol, OrderBook::new);
    }
    
    /**
     * 获取订单薄
     */
    public OrderBook getOrderBook(String symbol) {
        return orderBooks.get(symbol);
    }
    
    /**
     * 移除订单薄
     */
    public void removeOrderBook(String symbol) {
        OrderBook orderBook = orderBooks.remove(symbol);
        if (orderBook != null) {
            orderBook.clear();
            log.info("移除订单薄: symbol={}", symbol);
        }
    }
    
    /**
     * 获取所有订单薄
     */
    public Map<String, OrderBook> getAllOrderBooks() {
        return new ConcurrentHashMap<>(orderBooks);
    }
    
    /**
     * 获取或创建仓位
     */
    public Position getOrCreatePosition(Long userId, String symbol) {
        String key = generatePositionKey(userId, symbol);
        return positions.computeIfAbsent(key, k -> {
            Position position = new Position();
            position.setUserId(userId);
            position.setSymbol(symbol);
            return position;
        });
    }
    
    /**
     * 获取仓位
     */
    public Position getPosition(Long userId, String symbol) {
        String key = generatePositionKey(userId, symbol);
        return positions.get(key);
    }
    
    /**
     * 移除仓位
     */
    public void removePosition(Long userId, String symbol) {
        String key = generatePositionKey(userId, symbol);
        Position position = positions.remove(key);
        if (position != null) {
            log.info("移除仓位: userId={}, symbol={}", userId, symbol);
        }
    }
    
    /**
     * 获取用户的所有仓位
     */
    public List<Position> getUserPositions(Long userId) {
        return positions.values().stream()
                .filter(position -> position.getUserId().equals(userId))
                .toList();
    }
    
    /**
     * 获取标的所有仓位
     */
    public List<Position> getSymbolPositions(String symbol) {
        return positions.values().stream()
                .filter(position -> position.getSymbol().equals(symbol))
                .toList();
    }
    
    /**
     * 获取标的的所有仓位映射
     */
    public Map<Long, Position> getAllPositions(String symbol) {
        Map<Long, Position> symbolPositions = new ConcurrentHashMap<>();
        positions.values().stream()
                .filter(position -> position.getSymbol().equals(symbol))
                .forEach(position -> symbolPositions.put(position.getUserId(), position));
        return symbolPositions;
    }
    
    /**
     * 添加标的
     */
    public void addSymbol(Symbol symbol) {
        symbols.put(symbol.getSymbol(), symbol);
        log.info("添加标的: symbol={}", symbol.getSymbol());
    }
    
    /**
     * 获取标的
     */
    public Symbol getSymbol(String symbol) {
        return symbols.get(symbol);
    }
    
    /**
     * 更新标的
     */
    public void updateSymbol(Symbol symbol) {
        symbols.put(symbol.getSymbol(), symbol);
        log.info("更新标的: symbol={}", symbol.getSymbol());
    }
    
    /**
     * 移除标的
     */
    public void removeSymbol(String symbol) {
        Symbol removedSymbol = symbols.remove(symbol);
        if (removedSymbol != null) {
            // 同时移除相关的订单薄和仓位
            removeOrderBook(symbol);
            removeAllSymbolPositions(symbol);
            log.info("移除标的: symbol={}", symbol);
        }
    }
    
    /**
     * 获取所有标的
     */
    public Map<String, Symbol> getAllSymbols() {
        return new ConcurrentHashMap<>(symbols);
    }
    
    /**
     * 获取活跃的标的
     */
    public List<Symbol> getActiveSymbols() {
        return symbols.values().stream()
                .filter(Symbol::isTradeable)
                .toList();
    }
    
    /**
     * 更新仓位
     */
    public void updatePosition(Position position) {
        String key = generatePositionKey(position.getUserId(), position.getSymbol());
        positions.put(key, position);
    }
    
    /**
     * 获取内存统计信息
     */
    public MemoryStats getMemoryStats() {
        MemoryStats stats = new MemoryStats();
        stats.setOrderBookCount(orderBooks.size());
        stats.setPositionCount(positions.size());
        stats.setSymbolCount(symbols.size());
        
        // 计算总订单数
        int totalOrders = orderBooks.values().stream()
                .mapToInt(OrderBook::getOrderCount)
                .sum();
        stats.setTotalOrderCount(totalOrders);
        
        return stats;
    }
    
    /**
     * 清空所有数据
     */
    public void clearAll() {
        orderBooks.clear();
        positions.clear();
        symbols.clear();
        log.info("清空所有内存数据");
    }
    
    /**
     * 生成仓位键
     */
    private String generatePositionKey(Long userId, String symbol) {
        return userId + "_" + symbol;
    }
    
    /**
     * 移除标的所有仓位
     */
    private void removeAllSymbolPositions(String symbol) {
        positions.entrySet().removeIf(entry -> {
            Position position = entry.getValue();
            boolean shouldRemove = position.getSymbol().equals(symbol);
            if (shouldRemove) {
                log.debug("移除仓位: userId={}, symbol={}", position.getUserId(), symbol);
            }
            return shouldRemove;
        });
    }
} 