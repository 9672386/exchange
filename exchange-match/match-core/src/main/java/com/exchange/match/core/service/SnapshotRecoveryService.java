package com.exchange.match.core.service;

import com.exchange.match.core.memory.MemoryManager;
import com.exchange.match.core.model.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 快照恢复服务
 * 用于从快照数据恢复撮合引擎的完整状态
 */
@Slf4j
@Service
public class SnapshotRecoveryService {
    
    @Autowired
    private MemoryManager memoryManager;
    
    @Autowired
    private KafkaOffsetManager offsetManager;
    
    @Autowired
    private KafkaConsumerService consumerService;
    
    /**
     * 从快照恢复撮合引擎状态
     */
    public void recoverFromSnapshot(MatchEngineSnapshot snapshot) {
        log.info("开始从快照恢复撮合引擎状态: snapshotId={}", snapshot.getSnapshotId());
        
        try {
            // 1. 恢复交易对配置
            recoverSymbols(snapshot.getSymbolSnapshots());
            
            // 2. 恢复订单薄数据
            recoverOrderBooks(snapshot.getOrderBookSnapshots());
            
            // 3. 恢复持仓数据
            recoverPositions(snapshot.getPositionSnapshots());
            
            // 4. 恢复订单数据
            recoverOrders(snapshot.getOrderSnapshots());
            
            // 5. 恢复仓位锁定数据
            recoverPositionLocks(snapshot.getPositionLockSnapshots());
            
            // 6. 恢复Kafka offset数据
            recoverKafkaOffsets(snapshot.getKafkaOffsetSnapshots());
            
            // 7. 从快照offset开始消费
            startConsumingFromSnapshot(snapshot.getKafkaOffsetSnapshots());
            
            log.info("快照恢复完成: snapshotId={}, symbols={}, orderBooks={}, positions={}, orders={}, locks={}, offsets={}", 
                    snapshot.getSnapshotId(),
                    snapshot.getSymbolSnapshots().size(),
                    snapshot.getOrderBookSnapshots().size(),
                    snapshot.getPositionSnapshots().size(),
                    snapshot.getOrderSnapshots().size(),
                    snapshot.getPositionLockSnapshots().size(),
                    snapshot.getKafkaOffsetSnapshots().size());
                    
        } catch (Exception e) {
            log.error("快照恢复失败: snapshotId={}", snapshot.getSnapshotId(), e);
            throw new RuntimeException("快照恢复失败", e);
        }
    }
    
    /**
     * 从快照offset开始消费
     */
    private void startConsumingFromSnapshot(Map<String, MatchEngineSnapshot.KafkaOffsetSnapshot> kafkaOffsetSnapshots) {
        log.info("从快照offset开始消费: count={}", kafkaOffsetSnapshots.size());
        
        Map<String, Long> snapshotOffsets = new HashMap<>();
        
        for (Map.Entry<String, MatchEngineSnapshot.KafkaOffsetSnapshot> entry : kafkaOffsetSnapshots.entrySet()) {
            String topic = entry.getKey();
            MatchEngineSnapshot.KafkaOffsetSnapshot snapshot = entry.getValue();
            
            // 使用committed offset作为消费起点，确保数据连续性
            snapshotOffsets.put(topic, snapshot.getCommittedOffset());
            
            log.info("设置主题{}的消费offset为: {}", topic, snapshot.getCommittedOffset());
        }
        
        // 启动消费者服务，从快照offset开始消费
        consumerService.startConsumingFromSnapshot(snapshotOffsets);
        
        log.info("从快照offset开始消费设置完成");
    }
    
    /**
     * 恢复交易对配置
     */
    private void recoverSymbols(Map<String, MatchEngineSnapshot.SymbolSnapshot> symbolSnapshots) {
        log.info("恢复交易对配置: count={}", symbolSnapshots.size());
        
        for (Map.Entry<String, MatchEngineSnapshot.SymbolSnapshot> entry : symbolSnapshots.entrySet()) {
            MatchEngineSnapshot.SymbolSnapshot snapshot = entry.getValue();
            
            Symbol symbol = new Symbol();
            symbol.setSymbol(snapshot.getSymbol());
            symbol.setMinQuantity(snapshot.getMinQuantity());
            symbol.setMaxQuantity(snapshot.getMaxQuantity());
            symbol.setTickSize(snapshot.getTickSize());
            symbol.setRiskLimitConfig(snapshot.getRiskLimitConfig());
            
            memoryManager.addSymbol(symbol);
        }
    }
    
    /**
     * 恢复订单薄数据
     */
    private void recoverOrderBooks(Map<String, OrderBookSnapshot> orderBookSnapshots) {
        log.info("恢复订单薄数据: count={}", orderBookSnapshots.size());
        
        for (Map.Entry<String, OrderBookSnapshot> entry : orderBookSnapshots.entrySet()) {
            String symbol = entry.getKey();
            OrderBookSnapshot snapshot = entry.getValue();
            
            OrderBook orderBook = memoryManager.getOrCreateOrderBook(symbol);
            
            // 恢复订单薄基本信息
            orderBook.setLastPrice(snapshot.getLastPrice());
            orderBook.setHighPrice(snapshot.getHighPrice());
            orderBook.setLowPrice(snapshot.getLowPrice());
            orderBook.setVolume24h(snapshot.getVolume24h());
            orderBook.setCreateTime(snapshot.getCreateTime());
            orderBook.setLastUpdateTime(snapshot.getLastUpdateTime());
            
            log.debug("恢复订单薄: symbol={}, lastPrice={}, volume24h={}", 
                    symbol, snapshot.getLastPrice(), snapshot.getVolume24h());
        }
    }
    
    /**
     * 恢复持仓数据
     */
    private void recoverPositions(Map<String, MatchEngineSnapshot.PositionSnapshot> positionSnapshots) {
        log.info("恢复持仓数据: count={}", positionSnapshots.size());
        
        for (Map.Entry<String, MatchEngineSnapshot.PositionSnapshot> entry : positionSnapshots.entrySet()) {
            MatchEngineSnapshot.PositionSnapshot snapshot = entry.getValue();
            
            Position position = memoryManager.getOrCreatePosition(snapshot.getUserId(), snapshot.getSymbol());
            
            // 恢复持仓基本信息
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
            
            // 更新内存管理器中的持仓
            memoryManager.updatePosition(position);
            
            log.debug("恢复持仓: userId={}, symbol={}, quantity={}, unrealizedPnl={}", 
                    snapshot.getUserId(), snapshot.getSymbol(), 
                    snapshot.getQuantity(), snapshot.getUnrealizedPnl());
        }
    }
    
    /**
     * 恢复订单数据
     */
    private void recoverOrders(Map<String, MatchEngineSnapshot.OrderSnapshot> orderSnapshots) {
        log.info("恢复订单数据: count={}", orderSnapshots.size());
        
        for (Map.Entry<String, MatchEngineSnapshot.OrderSnapshot> entry : orderSnapshots.entrySet()) {
            MatchEngineSnapshot.OrderSnapshot snapshot = entry.getValue();
            
            Order order = new Order();
            order.setOrderId(snapshot.getOrderId());
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
            if (orderBook != null && snapshot.getStatus() != OrderStatus.CANCELLED && snapshot.getStatus() != OrderStatus.FILLED) {
                orderBook.addOrder(order);
            }
            
            log.debug("恢复订单: orderId={}, symbol={}, status={}, remainingQuantity={}", 
                    snapshot.getOrderId(), snapshot.getSymbol(), 
                    snapshot.getStatus(), snapshot.getRemainingQuantity());
        }
    }
    
    /**
     * 恢复仓位锁定数据
     */
    private void recoverPositionLocks(Map<String, MatchEngineSnapshot.PositionLockSnapshot> positionLockSnapshots) {
        log.info("恢复仓位锁定数据: count={}", positionLockSnapshots.size());
        
        for (Map.Entry<String, MatchEngineSnapshot.PositionLockSnapshot> entry : positionLockSnapshots.entrySet()) {
            MatchEngineSnapshot.PositionLockSnapshot snapshot = entry.getValue();
            
            Position position = memoryManager.getPosition(snapshot.getUserId(), snapshot.getSymbol());
            if (position != null) {
                // 恢复锁定状态
                position.setLockedQuantity(snapshot.getLockedQuantity());
                position.setAvailableQuantity(snapshot.getAvailableQuantity());
                position.setLockStatus(snapshot.getLockStatus());
                
                // 更新内存管理器中的持仓
                memoryManager.updatePosition(position);
                
                log.debug("恢复仓位锁定: userId={}, symbol={}, lockedQuantity={}, lockStatus={}", 
                        snapshot.getUserId(), snapshot.getSymbol(), 
                        snapshot.getLockedQuantity(), snapshot.getLockStatus());
            }
        }
    }
    
    /**
     * 恢复Kafka offset数据
     */
    private void recoverKafkaOffsets(Map<String, MatchEngineSnapshot.KafkaOffsetSnapshot> kafkaOffsetSnapshots) {
        log.info("恢复Kafka offset数据: count={}", kafkaOffsetSnapshots.size());
        
        for (Map.Entry<String, MatchEngineSnapshot.KafkaOffsetSnapshot> entry : kafkaOffsetSnapshots.entrySet()) {
            String topic = entry.getKey();
            MatchEngineSnapshot.KafkaOffsetSnapshot snapshot = entry.getValue();
            
            // 恢复offset状态
            offsetManager.setOffset(topic, snapshot.getCurrentOffset());
            offsetManager.commitOffset(topic, snapshot.getCommittedOffset());
            
            log.debug("恢复Kafka offset: topic={}, currentOffset={}, committedOffset={}, consistent={}", 
                    topic, snapshot.getCurrentOffset(), snapshot.getCommittedOffset(), snapshot.isConsistent());
        }
    }
    
    /**
     * 验证快照数据完整性
     */
    public boolean validateSnapshot(MatchEngineSnapshot snapshot) {
        log.info("验证快照数据完整性: snapshotId={}", snapshot.getSnapshotId());
        
        try {
            // 检查必要字段
            if (snapshot.getSnapshotId() == null || snapshot.getTimestamp() == 0) {
                log.error("快照基本信息不完整");
                return false;
            }
            
            // 检查订单薄数据
            if (snapshot.getOrderBookSnapshots() == null) {
                log.error("订单薄快照数据为空");
                return false;
            }
            
            // 检查持仓数据
            if (snapshot.getPositionSnapshots() == null) {
                log.error("持仓快照数据为空");
                return false;
            }
            
            // 检查交易对配置
            if (snapshot.getSymbolSnapshots() == null) {
                log.error("交易对配置快照数据为空");
                return false;
            }
            
            // 检查订单数据
            if (snapshot.getOrderSnapshots() == null) {
                log.error("订单快照数据为空");
                return false;
            }
            
            // 检查仓位锁定数据
            if (snapshot.getPositionLockSnapshots() == null) {
                log.error("仓位锁定快照数据为空");
                return false;
            }
            
            log.info("快照数据完整性验证通过: snapshotId={}", snapshot.getSnapshotId());
            return true;
            
        } catch (Exception e) {
            log.error("快照数据完整性验证失败: snapshotId={}", snapshot.getSnapshotId(), e);
            return false;
        }
    }
} 