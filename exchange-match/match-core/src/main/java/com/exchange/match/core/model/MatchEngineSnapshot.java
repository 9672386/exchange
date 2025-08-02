package com.exchange.match.core.model;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 撮合引擎完整快照模型
 */
@Data
public class MatchEngineSnapshot {
    
    /**
     * 快照ID
     */
    private String snapshotId;
    
    /**
     * 快照时间戳
     */
    private long timestamp;
    
    /**
     * 快照时间
     */
    private LocalDateTime snapshotTime;
    
    /**
     * 撮合引擎状态
     */
    private EngineStatus engineStatus;
    
    /**
     * 所有交易对的订单薄快照
     */
    private Map<String, OrderBookSnapshot> orderBookSnapshots;
    
    /**
     * 所有用户持仓快照
     */
    private Map<String, PositionSnapshot> positionSnapshots;
    
    /**
     * 所有交易对配置快照
     */
    private Map<String, SymbolSnapshot> symbolSnapshots;
    
    /**
     * 所有订单数据快照
     */
    private Map<String, OrderSnapshot> orderSnapshots;
    
    /**
     * 所有仓位锁定数据快照
     */
    private Map<String, PositionLockSnapshot> positionLockSnapshots;
    
    /**
     * 内存统计信息
     */
    private MemoryStatsSnapshot memoryStats;
    
    /**
     * Kafka offset信息
     */
    private Map<String, KafkaOffsetSnapshot> kafkaOffsetSnapshots;
    
    /**
     * 最后处理的命令ID
     */
    private long lastCommandId;
    
    /**
     * 撮合引擎状态枚举
     */
    public enum EngineStatus {
        RUNNING("运行中"),
        STOPPED("已停止"),
        PAUSED("已暂停"),
        ERROR("错误状态");
        
        private final String description;
        
        EngineStatus(String description) {
            this.description = description;
        }
        
        public String getDescription() {
            return description;
        }
    }
    
    /**
     * 持仓快照
     */
    @Data
    public static class PositionSnapshot {
        private Long userId;
        private String symbol;
        private String baseCurrency;
        private String quoteCurrency;
        private PositionSide side;
        private PositionMode positionMode;
        private BigDecimal quantity;
        private BigDecimal averagePrice;
        private BigDecimal unrealizedPnl;
        private BigDecimal realizedPnl;
        private BigDecimal margin;
        private BigDecimal riskRatio;
        private BigDecimal leverage;
        private BigDecimal liquidationPrice;
        private PositionStatus status;
        private BigDecimal lockedQuantity;
        private BigDecimal availableQuantity;
        private PositionLockStatus lockStatus;
        private LocalDateTime createTime;
        private LocalDateTime updateTime;
    }
    
    /**
     * 订单快照
     */
    @Data
    public static class OrderSnapshot {
        private String orderId;
        private Long userId;
        private String symbol;
        private OrderSide side;
        private OrderType type;
        private PositionAction positionAction;
        private BigDecimal price;
        private BigDecimal quantity;
        private BigDecimal remainingQuantity;
        private BigDecimal filledQuantity;
        private BigDecimal averagePrice;
        private OrderStatus status;
        private String clientOrderId;
        private String remark;
        private LocalDateTime createTime;
        private LocalDateTime updateTime;
        private long lastUpdateTime;
    }
    
    /**
     * 仓位锁定快照
     */
    @Data
    public static class PositionLockSnapshot {
        private Long userId;
        private String symbol;
        private BigDecimal lockedQuantity;
        private BigDecimal availableQuantity;
        private PositionLockStatus lockStatus;
        private String lockReason;
        private String orderId;
        private LocalDateTime lockTime;
        private LocalDateTime unlockTime;
    }
    
    /**
     * 交易对配置快照
     */
    @Data
    public static class SymbolSnapshot {
        private String symbol;
        private String baseAsset;
        private String quoteAsset;
        private BigDecimal minQuantity;
        private BigDecimal maxQuantity;
        private BigDecimal tickSize;
        private BigDecimal minPrice;
        private BigDecimal maxPrice;
        private BigDecimal pricePrecision;
        private BigDecimal quantityPrecision;
        private Boolean enabled;
        private SymbolRiskLimitConfig riskLimitConfig;
        private LocalDateTime lastUpdateTime;
    }
    
    /**
     * 内存统计快照
     */
    @Data
    public static class MemoryStatsSnapshot {
        private int totalOrderBooks;
        private int totalPositions;
        private int totalSymbols;
        private long totalOrders;
        private long totalTrades;
        private BigDecimal totalVolume24h;
        private long snapshotTime;
        private int totalLockedPositions;
        private int totalActiveOrders;
    }
    
    /**
     * Kafka Offset快照
     */
    @Data
    public static class KafkaOffsetSnapshot {
        private String topic;
        private long currentOffset;
        private long committedOffset;
        private long pendingOffset;
        private boolean consistent;
        private long timestamp;
    }
} 