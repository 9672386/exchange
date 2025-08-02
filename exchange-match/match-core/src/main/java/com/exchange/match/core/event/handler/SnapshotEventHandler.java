package com.exchange.match.core.event.handler;

import com.exchange.match.core.event.EventHandler;
import com.exchange.match.core.event.MatchEvent;
import com.exchange.match.core.memory.MemoryManager;
import com.exchange.match.core.memory.MemoryStats;
import com.exchange.match.core.model.*;
import com.exchange.match.core.service.AsyncSnapshotService;
import com.exchange.match.core.service.BatchKafkaService;
import com.exchange.match.core.service.KafkaOffsetManager;
import com.exchange.match.core.service.CommandIdGenerator;
import com.exchange.match.core.service.SnapshotStorageService;
import com.exchange.match.enums.EventType;
import com.exchange.match.request.EventSnapshotReq;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * 快照事件处理器
 * 使用异步快照服务，避免阻塞撮合流程
 */
@Slf4j
@Component
public class SnapshotEventHandler implements EventHandler {
    
    @Autowired
    private MemoryManager memoryManager;
    
    @Autowired
    private BatchKafkaService batchKafkaService;
    
    @Autowired
    private KafkaOffsetManager offsetManager;
    
    @Autowired
    private AsyncSnapshotService asyncSnapshotService;
    
    @Autowired
    private SnapshotStorageService snapshotStorageService;
    
    @Override
    public void handle(MatchEvent event) {
        try {
            EventSnapshotReq snapshotReq = event.getSnapshotReq();
            log.info("处理快照事件: symbol={}", snapshotReq != null ? snapshotReq.getSymbol() : "all");
            
            // 异步生成快照，不阻塞当前线程
            CompletableFuture<MatchEngineSnapshot> future = asyncSnapshotService.generateSnapshotAsync(snapshotReq);
            
            // 设置处理结果（立即返回，不等待快照完成）
            event.setResult("快照任务已提交，正在异步处理");
            
            // 异步处理快照完成后的操作
            future.thenAccept(snapshot -> {
                try {
                    // 保存快照到文件
                    String fileName = snapshotStorageService.saveSnapshot(snapshot);
                    
                    log.info("异步快照处理完成: snapshotId={}, file={}, orderBooks={}, positions={}, symbols={}, orders={}, locks={}, offsets={}", 
                            snapshot.getSnapshotId(), fileName,
                            snapshot.getOrderBookSnapshots().size(),
                            snapshot.getPositionSnapshots().size(),
                            snapshot.getSymbolSnapshots().size(),
                            snapshot.getOrderSnapshots().size(),
                            snapshot.getPositionLockSnapshots().size(),
                            snapshot.getKafkaOffsetSnapshots().size());
                } catch (Exception e) {
                    log.error("保存快照失败: snapshotId={}", snapshot.getSnapshotId(), e);
                }
            }).exceptionally(throwable -> {
                log.error("异步快照处理失败", throwable);
                return null;
            });
            
        } catch (Exception e) {
            log.error("处理快照事件失败", e);
            event.setException(e);
        }
    }
    
    /**
     * 生成完整快照
     */
    public MatchEngineSnapshot generateFullSnapshot(EventSnapshotReq snapshotReq) {
        MatchEngineSnapshot snapshot = new MatchEngineSnapshot();
        
        // 设置基本信息
        snapshot.setSnapshotId(UUID.randomUUID().toString());
        snapshot.setTimestamp(System.currentTimeMillis());
        snapshot.setSnapshotTime(LocalDateTime.now());
        snapshot.setEngineStatus(MatchEngineSnapshot.EngineStatus.RUNNING);
        
        // 生成订单薄快照
        snapshot.setOrderBookSnapshots(generateOrderBookSnapshots(snapshotReq));
        
        // 生成持仓快照
        snapshot.setPositionSnapshots(generatePositionSnapshots(snapshotReq));
        
        // 生成交易对配置快照
        snapshot.setSymbolSnapshots(generateSymbolSnapshots(snapshotReq));
        
        // 生成订单快照
        snapshot.setOrderSnapshots(generateOrderSnapshots(snapshotReq));
        
        // 生成仓位锁定快照
        snapshot.setPositionLockSnapshots(generatePositionLockSnapshots(snapshotReq));
        
        // 生成内存统计快照
        snapshot.setMemoryStats(generateMemoryStatsSnapshot());
        
        // 生成Kafka offset快照
        snapshot.setKafkaOffsetSnapshots(generateKafkaOffsetSnapshots());
        
        // 记录当前命令ID
        snapshot.setLastCommandId(CommandIdGenerator.getCurrentId());
        
        return snapshot;
    }
    
    /**
     * 生成订单薄快照
     */
    private Map<String, OrderBookSnapshot> generateOrderBookSnapshots(EventSnapshotReq snapshotReq) {
        Map<String, OrderBookSnapshot> snapshots = new HashMap<>();
        
        if (snapshotReq != null && snapshotReq.getSymbol() != null) {
            // 生成指定交易对的快照
            OrderBook orderBook = memoryManager.getOrderBook(snapshotReq.getSymbol());
            if (orderBook != null) {
                snapshots.put(snapshotReq.getSymbol(), orderBook.getSnapshot());
            }
        } else {
            // 生成所有交易对的快照
            Map<String, OrderBook> allOrderBooks = memoryManager.getAllOrderBooks();
            for (Map.Entry<String, OrderBook> entry : allOrderBooks.entrySet()) {
                snapshots.put(entry.getKey(), entry.getValue().getSnapshot());
            }
        }
        
        return snapshots;
    }
    
    /**
     * 生成持仓快照
     */
    private Map<String, MatchEngineSnapshot.PositionSnapshot> generatePositionSnapshots(EventSnapshotReq snapshotReq) {
        Map<String, MatchEngineSnapshot.PositionSnapshot> snapshots = new HashMap<>();
        
        if (snapshotReq != null && snapshotReq.getSymbol() != null) {
            // 生成指定交易对的持仓快照
            Map<Long, Position> symbolPositions = memoryManager.getAllPositions(snapshotReq.getSymbol());
            for (Map.Entry<Long, Position> entry : symbolPositions.entrySet()) {
                Position position = entry.getValue();
                snapshots.put(generatePositionKey(position), convertToPositionSnapshot(position));
            }
        } else {
            // 生成所有持仓快照
            Map<String, OrderBook> allOrderBooks = memoryManager.getAllOrderBooks();
            for (String symbol : allOrderBooks.keySet()) {
                Map<Long, Position> symbolPositions = memoryManager.getAllPositions(symbol);
                for (Map.Entry<Long, Position> entry : symbolPositions.entrySet()) {
                    Position position = entry.getValue();
                    snapshots.put(generatePositionKey(position), convertToPositionSnapshot(position));
                }
            }
        }
        
        return snapshots;
    }
    
    /**
     * 生成订单快照
     */
    private Map<String, MatchEngineSnapshot.OrderSnapshot> generateOrderSnapshots(EventSnapshotReq snapshotReq) {
        Map<String, MatchEngineSnapshot.OrderSnapshot> snapshots = new HashMap<>();
        
        if (snapshotReq != null && snapshotReq.getSymbol() != null) {
            // 生成指定交易对的订单快照
            OrderBook orderBook = memoryManager.getOrderBook(snapshotReq.getSymbol());
            if (orderBook != null) {
                // 通过反射获取orderMap字段，或者使用其他方式获取订单
                // 暂时使用getUserOrders来获取所有订单
                Map<Long, List<Order>> userOrders = orderBook.getUserOrders();
                for (List<Order> userOrderList : userOrders.values()) {
                    for (Order order : userOrderList) {
                        snapshots.put(order.getOrderId(), convertToOrderSnapshot(order));
                    }
                }
            }
        } else {
            // 生成所有订单快照
            Map<String, OrderBook> allOrderBooks = memoryManager.getAllOrderBooks();
            for (Map.Entry<String, OrderBook> entry : allOrderBooks.entrySet()) {
                OrderBook orderBook = entry.getValue();
                // 通过反射获取orderMap字段，或者使用其他方式获取订单
                // 暂时使用getUserOrders来获取所有订单
                Map<Long, List<Order>> userOrders = orderBook.getUserOrders();
                for (List<Order> userOrderList : userOrders.values()) {
                    for (Order order : userOrderList) {
                        snapshots.put(order.getOrderId(), convertToOrderSnapshot(order));
                    }
                }
            }
        }
        
        return snapshots;
    }
    
    /**
     * 生成仓位锁定快照
     */
    private Map<String, MatchEngineSnapshot.PositionLockSnapshot> generatePositionLockSnapshots(EventSnapshotReq snapshotReq) {
        Map<String, MatchEngineSnapshot.PositionLockSnapshot> snapshots = new HashMap<>();
        
        if (snapshotReq != null && snapshotReq.getSymbol() != null) {
            // 生成指定交易对的仓位锁定快照
            Map<Long, Position> symbolPositions = memoryManager.getAllPositions(snapshotReq.getSymbol());
            for (Map.Entry<Long, Position> entry : symbolPositions.entrySet()) {
                Position position = entry.getValue();
                if (position.getLockedQuantity().compareTo(BigDecimal.ZERO) > 0) {
                    snapshots.put(generatePositionKey(position), convertToPositionLockSnapshot(position));
                }
            }
        } else {
            // 生成所有仓位锁定快照
            Map<String, OrderBook> allOrderBooks = memoryManager.getAllOrderBooks();
            for (String symbol : allOrderBooks.keySet()) {
                Map<Long, Position> symbolPositions = memoryManager.getAllPositions(symbol);
                for (Map.Entry<Long, Position> entry : symbolPositions.entrySet()) {
                    Position position = entry.getValue();
                    if (position.getLockedQuantity().compareTo(BigDecimal.ZERO) > 0) {
                        snapshots.put(generatePositionKey(position), convertToPositionLockSnapshot(position));
                    }
                }
            }
        }
        
        return snapshots;
    }
    
    /**
     * 生成交易对配置快照
     */
    private Map<String, MatchEngineSnapshot.SymbolSnapshot> generateSymbolSnapshots(EventSnapshotReq snapshotReq) {
        Map<String, MatchEngineSnapshot.SymbolSnapshot> snapshots = new HashMap<>();
        
        if (snapshotReq != null && snapshotReq.getSymbol() != null) {
            // 生成指定交易对的配置快照
            Symbol symbol = memoryManager.getSymbol(snapshotReq.getSymbol());
            if (symbol != null) {
                snapshots.put(symbol.getSymbol(), convertToSymbolSnapshot(symbol));
            }
        } else {
            // 生成所有交易对的配置快照
            Map<String, Symbol> allSymbols = memoryManager.getAllSymbols();
            for (Map.Entry<String, Symbol> entry : allSymbols.entrySet()) {
                snapshots.put(entry.getKey(), convertToSymbolSnapshot(entry.getValue()));
            }
        }
        
        return snapshots;
    }
    
    /**
     * 生成内存统计快照
     */
    private MatchEngineSnapshot.MemoryStatsSnapshot generateMemoryStatsSnapshot() {
        MemoryStats memoryStats = memoryManager.getMemoryStats();
        MatchEngineSnapshot.MemoryStatsSnapshot snapshot = new MatchEngineSnapshot.MemoryStatsSnapshot();
        
        snapshot.setTotalOrderBooks(memoryStats.getOrderBookCount());
        snapshot.setTotalPositions(memoryStats.getPositionCount());
        snapshot.setTotalSymbols(memoryStats.getSymbolCount());
        snapshot.setTotalOrders(memoryStats.getTotalOrderCount());
        snapshot.setTotalTrades(memoryStats.getTotalTradeCount());
        snapshot.setTotalVolume24h(memoryStats.getTotalVolume24h());
        snapshot.setSnapshotTime(System.currentTimeMillis());
        
        // 计算锁定仓位和活跃订单数量
        int totalLockedPositions = 0;
        int totalActiveOrders = 0;
        
        Map<String, OrderBook> allOrderBooks = memoryManager.getAllOrderBooks();
        for (Map.Entry<String, OrderBook> entry : allOrderBooks.entrySet()) {
            OrderBook orderBook = entry.getValue();
            totalActiveOrders += orderBook.getOrderCount();
            
            Map<Long, Position> symbolPositions = memoryManager.getAllPositions(entry.getKey());
            for (Position position : symbolPositions.values()) {
                if (position.getLockedQuantity().compareTo(BigDecimal.ZERO) > 0) {
                    totalLockedPositions++;
                }
            }
        }
        
        snapshot.setTotalLockedPositions(totalLockedPositions);
        snapshot.setTotalActiveOrders(totalActiveOrders);
        
        return snapshot;
    }
    
    /**
     * 生成Kafka offset快照
     */
    private Map<String, MatchEngineSnapshot.KafkaOffsetSnapshot> generateKafkaOffsetSnapshots() {
        Map<String, MatchEngineSnapshot.KafkaOffsetSnapshot> snapshots = new HashMap<>();
        
        Map<String, KafkaOffsetManager.OffsetStatus> offsetStatus = offsetManager.getAllOffsetStatus();
        
        for (Map.Entry<String, KafkaOffsetManager.OffsetStatus> entry : offsetStatus.entrySet()) {
            String topic = entry.getKey();
            KafkaOffsetManager.OffsetStatus status = entry.getValue();
            
            MatchEngineSnapshot.KafkaOffsetSnapshot snapshot = new MatchEngineSnapshot.KafkaOffsetSnapshot();
            snapshot.setTopic(topic);
            snapshot.setCurrentOffset(status.getCurrentOffset());
            snapshot.setCommittedOffset(status.getCommittedOffset());
            snapshot.setPendingOffset(status.getPendingOffset());
            snapshot.setConsistent(status.getCurrentOffset() == status.getCommittedOffset());
            snapshot.setTimestamp(status.getTimestamp());
            
            snapshots.put(topic, snapshot);
        }
        
        return snapshots;
    }
    
    /**
     * 转换持仓为快照格式
     */
    private MatchEngineSnapshot.PositionSnapshot convertToPositionSnapshot(Position position) {
        MatchEngineSnapshot.PositionSnapshot snapshot = new MatchEngineSnapshot.PositionSnapshot();
        
        snapshot.setUserId(position.getUserId());
        snapshot.setSymbol(position.getSymbol());
        snapshot.setBaseCurrency(position.getBaseCurrency());
        snapshot.setQuoteCurrency(position.getQuoteCurrency());
        snapshot.setSide(position.getSide());
        snapshot.setPositionMode(position.getPositionMode());
        snapshot.setQuantity(position.getQuantity());
        snapshot.setAveragePrice(position.getAveragePrice());
        snapshot.setUnrealizedPnl(position.getUnrealizedPnl());
        snapshot.setRealizedPnl(position.getRealizedPnl());
        snapshot.setMargin(position.getMargin());
        snapshot.setLeverage(position.getLeverage());
        snapshot.setLiquidationPrice(position.getLiquidationPrice());
        snapshot.setStatus(position.getStatus());
        snapshot.setLockedQuantity(position.getLockedQuantity());
        snapshot.setAvailableQuantity(position.getAvailableQuantity());
        snapshot.setLockStatus(position.getLockStatus());
        snapshot.setCreateTime(position.getCreateTime());
        snapshot.setUpdateTime(position.getUpdateTime());
        
        // 根据仓位模式计算风险率
        if (position.isIsolatedMode()) {
            snapshot.setRiskRatio(position.getIsolatedRiskRatio());
        } else {
            // 全仓模式需要外部传入总保证金和总未实现盈亏
            snapshot.setRiskRatio(java.math.BigDecimal.ZERO); // 暂时设为0，实际需要外部计算
        }
        
        return snapshot;
    }
    
    /**
     * 转换订单为快照格式
     */
    private MatchEngineSnapshot.OrderSnapshot convertToOrderSnapshot(Order order) {
        MatchEngineSnapshot.OrderSnapshot snapshot = new MatchEngineSnapshot.OrderSnapshot();
        
        snapshot.setOrderId(order.getOrderId());
        snapshot.setUserId(order.getUserId());
        snapshot.setSymbol(order.getSymbol());
        snapshot.setSide(order.getSide());
        snapshot.setType(order.getType());
        snapshot.setPositionAction(order.getPositionAction());
        snapshot.setPrice(order.getPrice());
        snapshot.setQuantity(order.getQuantity());
        snapshot.setRemainingQuantity(order.getRemainingQuantity());
        snapshot.setFilledQuantity(order.getFilledQuantity());
        // Order类没有averagePrice字段，暂时设为null
        snapshot.setAveragePrice(null);
        snapshot.setStatus(order.getStatus());
        snapshot.setClientOrderId(order.getClientOrderId());
        snapshot.setRemark(order.getRemark());
        snapshot.setCreateTime(order.getCreateTime());
        snapshot.setUpdateTime(order.getUpdateTime());
        // Order类没有lastUpdateTime字段，使用updateTime
        snapshot.setLastUpdateTime(order.getUpdateTime().atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli());
        
        return snapshot;
    }
    
    /**
     * 转换仓位锁定为快照格式
     */
    private MatchEngineSnapshot.PositionLockSnapshot convertToPositionLockSnapshot(Position position) {
        MatchEngineSnapshot.PositionLockSnapshot snapshot = new MatchEngineSnapshot.PositionLockSnapshot();
        
        snapshot.setUserId(position.getUserId());
        snapshot.setSymbol(position.getSymbol());
        snapshot.setLockedQuantity(position.getLockedQuantity());
        snapshot.setAvailableQuantity(position.getAvailableQuantity());
        snapshot.setLockStatus(position.getLockStatus());
        // 注意：Position类可能没有这些字段，需要根据实际情况调整
        // snapshot.setLockReason(position.getLockReason());
        // snapshot.setOrderId(position.getLockOrderId());
        // snapshot.setLockTime(position.getLockTime());
        // snapshot.setUnlockTime(position.getUnlockTime());
        
        return snapshot;
    }
    
    /**
     * 转换交易对为快照格式
     */
    private MatchEngineSnapshot.SymbolSnapshot convertToSymbolSnapshot(Symbol symbol) {
        MatchEngineSnapshot.SymbolSnapshot snapshot = new MatchEngineSnapshot.SymbolSnapshot();
        
        snapshot.setSymbol(symbol.getSymbol());
        // 简化字段设置，只保留基本字段
        snapshot.setMinQuantity(symbol.getMinQuantity());
        snapshot.setMaxQuantity(symbol.getMaxQuantity());
        snapshot.setTickSize(symbol.getTickSize());
        snapshot.setRiskLimitConfig(symbol.getRiskLimitConfig());
        
        return snapshot;
    }
    
    /**
     * 生成持仓键
     */
    private String generatePositionKey(Position position) {
        return position.getUserId() + "_" + position.getSymbol();
    }
    
    @Override
    public EventType getSupportedEventType() {
        return EventType.SNAPSHOT;
    }
} 