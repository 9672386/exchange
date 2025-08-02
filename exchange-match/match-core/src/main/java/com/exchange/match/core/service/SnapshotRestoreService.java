package com.exchange.match.core.service;

import com.exchange.match.core.memory.MemoryManager;
import com.exchange.match.core.model.MatchEngineSnapshot;
import com.exchange.match.core.model.OrderBook;
import com.exchange.match.core.model.Order;
import com.exchange.match.core.model.Position;
import com.exchange.match.core.model.Symbol;
import com.exchange.match.core.model.SymbolStatus;
import com.exchange.match.core.model.OrderBookSnapshot;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Map;

/**
 * 快照恢复服务
 * 用于从快照恢复内存状态
 */
@Slf4j
@Service
public class SnapshotRestoreService {
    
    @Autowired
    private MemoryManager memoryManager;
    
    /**
     * 从快照恢复内存状态
     */
    public void restoreFromSnapshot(MatchEngineSnapshot snapshot) {
        log.info("开始从快照恢复: snapshotId={}, lastCommandId={}", 
                snapshot.getSnapshotId(), snapshot.getLastCommandId());
        
        try {
            // 1. 清空当前内存状态
            memoryManager.clearAll();
            
            // 2. 恢复交易对配置
            restoreSymbols(snapshot.getSymbolSnapshots());
            
            // 3. 恢复订单薄数据
            restoreOrderBooks(snapshot.getOrderBookSnapshots());
            
            // 4. 恢复持仓数据
            restorePositions(snapshot.getPositionSnapshots());
            
            // 5. 恢复订单数据
            restoreOrders(snapshot.getOrderSnapshots());
            
            // 6. 恢复仓位锁定
            restorePositionLocks(snapshot.getPositionLockSnapshots());
            
            // 7. 设置命令ID
            CommandIdGenerator.set(snapshot.getLastCommandId());
            
            log.info("快照恢复完成: snapshotId={}, lastCommandId={}", 
                    snapshot.getSnapshotId(), snapshot.getLastCommandId());
            
        } catch (Exception e) {
            log.error("快照恢复失败: snapshotId={}", snapshot.getSnapshotId(), e);
            throw new RuntimeException("快照恢复失败", e);
        }
    }
    
    /**
     * 恢复交易对配置
     */
    private void restoreSymbols(Map<String, MatchEngineSnapshot.SymbolSnapshot> symbolSnapshots) {
        if (symbolSnapshots == null) return;
        
        for (Map.Entry<String, MatchEngineSnapshot.SymbolSnapshot> entry : symbolSnapshots.entrySet()) {
            String symbolName = entry.getKey();
            MatchEngineSnapshot.SymbolSnapshot snapshot = entry.getValue();
            
            Symbol symbol = new Symbol();
            symbol.setSymbol(symbolName);
            symbol.setBaseCurrency(snapshot.getBaseAsset());
            symbol.setQuoteCurrency(snapshot.getQuoteAsset());
            symbol.setMinQuantity(snapshot.getMinQuantity());
            symbol.setMaxQuantity(snapshot.getMaxQuantity());
            symbol.setTickSize(snapshot.getTickSize());
            symbol.setPricePrecision(snapshot.getPricePrecision().intValue());
            symbol.setQuantityPrecision(snapshot.getQuantityPrecision().intValue());
            symbol.setStatus(snapshot.getEnabled() ? SymbolStatus.ACTIVE : SymbolStatus.INACTIVE);
            symbol.setRiskLimitConfig(snapshot.getRiskLimitConfig());
            symbol.setUpdateTime(snapshot.getLastUpdateTime());
            
            memoryManager.addSymbol(symbol);
        }
        
        log.info("恢复交易对配置完成，数量: {}", symbolSnapshots.size());
    }
    
    /**
     * 恢复订单薄数据
     */
    private void restoreOrderBooks(Map<String, OrderBookSnapshot> orderBookSnapshots) {
        if (orderBookSnapshots == null) return;
        
        for (Map.Entry<String, OrderBookSnapshot> entry : orderBookSnapshots.entrySet()) {
            String symbol = entry.getKey();
            OrderBookSnapshot snapshot = entry.getValue();
            
            OrderBook orderBook = new OrderBook(symbol);
            orderBook.setLastPrice(snapshot.getLastPrice());
            orderBook.setHighPrice(snapshot.getHighPrice());
            orderBook.setLowPrice(snapshot.getLowPrice());
            orderBook.setVolume24h(snapshot.getVolume24h());
            orderBook.setCreateTime(snapshot.getCreateTime());
            orderBook.setLastUpdateTime(snapshot.getLastUpdateTime());
            
            // 恢复深度数据（注意：这里只恢复深度，不恢复具体订单）
            // 具体订单会在restoreOrders中恢复
            
            memoryManager.setOrderBook(symbol, orderBook);
        }
        
        log.info("恢复订单薄数据完成，数量: {}", orderBookSnapshots.size());
    }
    
    /**
     * 恢复持仓数据
     */
    private void restorePositions(Map<String, MatchEngineSnapshot.PositionSnapshot> positionSnapshots) {
        if (positionSnapshots == null) return;
        
        for (Map.Entry<String, MatchEngineSnapshot.PositionSnapshot> entry : positionSnapshots.entrySet()) {
            String key = entry.getKey();
            MatchEngineSnapshot.PositionSnapshot snapshot = entry.getValue();
            
            // 解析key获取userId和symbol
            String[] parts = key.split("_");
            Long userId = Long.parseLong(parts[0]);
            String symbol = parts[1];
            
            Position position = new Position();
            position.setUserId(userId);
            position.setSymbol(symbol);
            position.setBaseCurrency(snapshot.getBaseCurrency());
            position.setQuoteCurrency(snapshot.getQuoteCurrency());
            position.setSide(snapshot.getSide());
            position.setPositionMode(snapshot.getPositionMode());
            position.setQuantity(snapshot.getQuantity());
            position.setAveragePrice(snapshot.getAveragePrice());
            position.setUnrealizedPnl(snapshot.getUnrealizedPnl());
            position.setRealizedPnl(snapshot.getRealizedPnl());
            position.setMargin(snapshot.getMargin());
            position.setLeverage(snapshot.getLeverage());
            position.setLiquidationPrice(snapshot.getLiquidationPrice());
            position.setStatus(snapshot.getStatus());
            position.setLockedQuantity(snapshot.getLockedQuantity());
            position.setAvailableQuantity(snapshot.getAvailableQuantity());
            position.setLockStatus(snapshot.getLockStatus());
            position.setCreateTime(snapshot.getCreateTime());
            position.setUpdateTime(snapshot.getUpdateTime());
            
            memoryManager.updatePosition(position);
        }
        
        log.info("恢复持仓数据完成，数量: {}", positionSnapshots.size());
    }
    
    /**
     * 恢复订单数据
     */
    private void restoreOrders(Map<String, MatchEngineSnapshot.OrderSnapshot> orderSnapshots) {
        if (orderSnapshots == null) return;
        
        for (Map.Entry<String, MatchEngineSnapshot.OrderSnapshot> entry : orderSnapshots.entrySet()) {
            String orderId = entry.getKey();
            MatchEngineSnapshot.OrderSnapshot snapshot = entry.getValue();
            
            Order order = new Order();
            order.setOrderId(orderId);
            order.setUserId(snapshot.getUserId());
            order.setSymbol(snapshot.getSymbol());
            order.setSide(snapshot.getSide());
            order.setType(snapshot.getType());
            order.setPositionAction(snapshot.getPositionAction());
            order.setPrice(snapshot.getPrice());
            order.setQuantity(snapshot.getQuantity());
            order.setRemainingQuantity(snapshot.getRemainingQuantity());
            order.setFilledQuantity(snapshot.getFilledQuantity());
            order.setStatus(snapshot.getStatus());
            order.setClientOrderId(snapshot.getClientOrderId());
            order.setRemark(snapshot.getRemark());
            order.setCreateTime(snapshot.getCreateTime());
            order.setUpdateTime(snapshot.getUpdateTime());
            
            // 将订单添加到对应的订单薄
            OrderBook orderBook = memoryManager.getOrderBook(snapshot.getSymbol());
            if (orderBook != null) {
                orderBook.addOrder(order);
            }
        }
        
        log.info("恢复订单数据完成，数量: {}", orderSnapshots.size());
    }
    
    /**
     * 恢复仓位锁定数据
     */
    private void restorePositionLocks(Map<String, MatchEngineSnapshot.PositionLockSnapshot> positionLockSnapshots) {
        if (positionLockSnapshots == null) return;
        
        for (Map.Entry<String, MatchEngineSnapshot.PositionLockSnapshot> entry : positionLockSnapshots.entrySet()) {
            String key = entry.getKey();
            MatchEngineSnapshot.PositionLockSnapshot snapshot = entry.getValue();
            
            // 解析key获取userId和symbol
            String[] parts = key.split("_");
            Long userId = Long.parseLong(parts[0]);
            String symbol = parts[1];
            
            Position position = memoryManager.getPosition(userId, symbol);
            if (position != null) {
                position.setLockedQuantity(snapshot.getLockedQuantity());
                position.setAvailableQuantity(snapshot.getAvailableQuantity());
                position.setLockStatus(snapshot.getLockStatus());
                
                memoryManager.updatePosition(position);
            }
        }
        
        log.info("恢复仓位锁定数据完成，数量: {}", positionLockSnapshots.size());
    }
} 