package com.exchange.match.core.service;

import com.exchange.match.core.model.PositionBalance;
import com.exchange.match.core.memory.MemoryManager;
import com.exchange.match.core.model.OrderBook;
import com.exchange.match.core.model.Position;
import com.exchange.match.core.model.PositionSide;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Map;

/**
 * 持仓意愿查询服务
 * 提供持仓意愿和订单深度的查询接口
 */
@Slf4j
@Service
public class PositionIntentionService {
    
    @Autowired
    private MemoryManager memoryManager;
    
    /**
     * 获取持仓意愿信息
     */
    public PositionIntentionInfo getPositionIntention(String symbol) {
        // 获取所有持仓
        Map<Long, Position> allPositions = memoryManager.getAllPositions(symbol);
        if (allPositions == null || allPositions.isEmpty()) {
            return new PositionIntentionInfo(symbol);
        }
        
        // 计算多空意愿
        BigDecimal longIntention = BigDecimal.ZERO;
        BigDecimal shortIntention = BigDecimal.ZERO;
        
        for (Position position : allPositions.values()) {
            if (position.getQuantity().compareTo(BigDecimal.ZERO) > 0) {
                if (position.getSide() == PositionSide.LONG) {
                    longIntention = longIntention.add(position.getQuantity());
                } else if (position.getSide() == PositionSide.SHORT) {
                    shortIntention = shortIntention.add(position.getQuantity());
                }
            }
        }
        
        return new PositionIntentionInfo(symbol, longIntention, shortIntention);
    }
    
    /**
     * 获取订单深度信息
     */
    public OrderDepthInfo getOrderDepth(String symbol) {
        OrderBook orderBook = memoryManager.getOrderBook(symbol);
        if (orderBook == null) {
            return new OrderDepthInfo(symbol);
        }
        
        // 计算买单深度（前5档）
        BigDecimal buyDepth = orderBook.getBuyOrders().entrySet().stream()
                .limit(5)
                .map(entry -> entry.getValue().stream()
                        .map(order -> order.getRemainingQuantity())
                        .reduce(BigDecimal.ZERO, BigDecimal::add))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        // 计算卖单深度（前5档）
        BigDecimal sellDepth = orderBook.getSellOrders().entrySet().stream()
                .limit(5)
                .map(entry -> entry.getValue().stream()
                        .map(order -> order.getRemainingQuantity())
                        .reduce(BigDecimal.ZERO, BigDecimal::add))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        return new OrderDepthInfo(symbol, buyDepth, sellDepth);
    }
    
    /**
     * 获取综合市场信息
     */
    public MarketInfo getMarketInfo(String symbol) {
        PositionIntentionInfo intentionInfo = getPositionIntention(symbol);
        OrderDepthInfo depthInfo = getOrderDepth(symbol);
        
        return new MarketInfo(symbol, intentionInfo, depthInfo);
    }
    
    /**
     * 持仓意愿信息
     */
    public static class PositionIntentionInfo {
        private String symbol;
        private BigDecimal longIntention;
        private BigDecimal shortIntention;
        private BigDecimal intentionRatio;
        private String intentionStatus;
        
        public PositionIntentionInfo(String symbol) {
            this.symbol = symbol;
            this.longIntention = BigDecimal.ZERO;
            this.shortIntention = BigDecimal.ZERO;
            this.intentionRatio = BigDecimal.ONE;
            this.intentionStatus = "无持仓";
        }
        
        public PositionIntentionInfo(String symbol, BigDecimal longIntention, BigDecimal shortIntention) {
            this.symbol = symbol;
            this.longIntention = longIntention;
            this.shortIntention = shortIntention;
            
            // 计算意愿比例
            if (shortIntention.compareTo(BigDecimal.ZERO) > 0) {
                this.intentionRatio = longIntention.divide(shortIntention, 4, BigDecimal.ROUND_HALF_UP);
            } else if (longIntention.compareTo(BigDecimal.ZERO) > 0) {
                this.intentionRatio = BigDecimal.valueOf(100); // 只有多仓意愿
            } else {
                this.intentionRatio = BigDecimal.ONE; // 无意愿
            }
            
            // 判断意愿状态
            if (longIntention.compareTo(BigDecimal.ZERO) == 0 && shortIntention.compareTo(BigDecimal.ZERO) == 0) {
                this.intentionStatus = "无持仓";
            } else if (intentionRatio.compareTo(BigDecimal.valueOf(1.2)) > 0) {
                this.intentionStatus = "多仓意愿偏重";
            } else if (intentionRatio.compareTo(BigDecimal.valueOf(0.8)) < 0) {
                this.intentionStatus = "空仓意愿偏重";
            } else {
                this.intentionStatus = "意愿平衡";
            }
        }
        
        // Getters
        public String getSymbol() { return symbol; }
        public BigDecimal getLongIntention() { return longIntention; }
        public BigDecimal getShortIntention() { return shortIntention; }
        public BigDecimal getIntentionRatio() { return intentionRatio; }
        public String getIntentionStatus() { return intentionStatus; }
    }
    
    /**
     * 订单深度信息
     */
    public static class OrderDepthInfo {
        private String symbol;
        private BigDecimal buyDepth;
        private BigDecimal sellDepth;
        private BigDecimal depthRatio;
        private String depthStatus;
        
        public OrderDepthInfo(String symbol) {
            this.symbol = symbol;
            this.buyDepth = BigDecimal.ZERO;
            this.sellDepth = BigDecimal.ZERO;
            this.depthRatio = BigDecimal.ONE;
            this.depthStatus = "无订单";
        }
        
        public OrderDepthInfo(String symbol, BigDecimal buyDepth, BigDecimal sellDepth) {
            this.symbol = symbol;
            this.buyDepth = buyDepth;
            this.sellDepth = sellDepth;
            
            // 计算深度比例
            if (sellDepth.compareTo(BigDecimal.ZERO) > 0) {
                this.depthRatio = buyDepth.divide(sellDepth, 4, BigDecimal.ROUND_HALF_UP);
            } else if (buyDepth.compareTo(BigDecimal.ZERO) > 0) {
                this.depthRatio = BigDecimal.valueOf(100); // 只有买单
            } else {
                this.depthRatio = BigDecimal.ONE; // 无订单
            }
            
            // 判断深度状态
            if (buyDepth.compareTo(BigDecimal.ZERO) == 0 && sellDepth.compareTo(BigDecimal.ZERO) == 0) {
                this.depthStatus = "无订单";
            } else if (depthRatio.compareTo(BigDecimal.valueOf(1.2)) > 0) {
                this.depthStatus = "买单偏重";
            } else if (depthRatio.compareTo(BigDecimal.valueOf(0.8)) < 0) {
                this.depthStatus = "卖单偏重";
            } else {
                this.depthStatus = "深度平衡";
            }
        }
        
        // Getters
        public String getSymbol() { return symbol; }
        public BigDecimal getBuyDepth() { return buyDepth; }
        public BigDecimal getSellDepth() { return sellDepth; }
        public BigDecimal getDepthRatio() { return depthRatio; }
        public String getDepthStatus() { return depthStatus; }
    }
    
    /**
     * 综合市场信息
     */
    public static class MarketInfo {
        private String symbol;
        private PositionIntentionInfo intentionInfo;
        private OrderDepthInfo depthInfo;
        private String marketStatus;
        private String marketAdvice;
        
        public MarketInfo(String symbol, PositionIntentionInfo intentionInfo, OrderDepthInfo depthInfo) {
            this.symbol = symbol;
            this.intentionInfo = intentionInfo;
            this.depthInfo = depthInfo;
            
            // 综合判断市场状态
            if (intentionInfo.getIntentionStatus().equals("意愿平衡") && 
                depthInfo.getDepthStatus().equals("深度平衡")) {
                this.marketStatus = "市场平衡";
                this.marketAdvice = "市场状态良好，可以正常交易";
            } else if (intentionInfo.getIntentionStatus().contains("偏重") || 
                       depthInfo.getDepthStatus().contains("偏重")) {
                this.marketStatus = "市场偏重";
                this.marketAdvice = "市场存在偏向，建议谨慎交易";
            } else {
                this.marketStatus = "市场异常";
                this.marketAdvice = "市场状态异常，建议暂停交易";
            }
        }
        
        // Getters
        public String getSymbol() { return symbol; }
        public PositionIntentionInfo getIntentionInfo() { return intentionInfo; }
        public OrderDepthInfo getDepthInfo() { return depthInfo; }
        public String getMarketStatus() { return marketStatus; }
        public String getMarketAdvice() { return marketAdvice; }
    }
} 