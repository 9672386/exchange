package com.exchange.match.core.service;

import com.exchange.match.core.model.Symbol;
import com.exchange.match.core.model.SymbolRiskLimitConfig;
import com.exchange.match.core.model.TradingType;
import com.exchange.match.core.model.SymbolStatus;
import com.exchange.match.core.memory.MemoryManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Symbol管理服务
 * 负责标的的创建、风险限额配置和交易权限管理
 */
@Slf4j
@Service
public class SymbolManagementService {
    
    @Autowired
    private MemoryManager memoryManager;
    
    @Autowired
    private SymbolRiskLimitConfigManager symbolRiskLimitConfigManager;
    
    /**
     * 创建新的标的
     * 
     * @param symbol 交易对
     * @param tradingType 交易类型
     * @param baseCurrency 基础货币
     * @param quoteCurrency 计价货币
     * @param minQuantity 最小交易数量
     * @param maxQuantity 最大交易数量
     * @param quantityPrecision 数量精度
     * @param pricePrecision 价格精度
     * @param tickSize 最小价格变动单位
     * @param feeRate 手续费率
     * @param supportsLeverage 是否支持杠杆
     * @param supportsShort 是否支持做空
     * @return 创建的标的
     */
    public Symbol createSymbol(String symbol, TradingType tradingType, String baseCurrency, String quoteCurrency,
                             BigDecimal minQuantity, BigDecimal maxQuantity, Integer quantityPrecision, Integer pricePrecision,
                             BigDecimal tickSize, BigDecimal feeRate, Boolean supportsLeverage, Boolean supportsShort) {
        
        log.info("开始创建标的: {}, 交易类型: {}", symbol, tradingType);
        
        // 检查标的是否已存在
        if (memoryManager.getSymbol(symbol) != null) {
            throw new IllegalArgumentException("标的 " + symbol + " 已存在");
        }
        
        // 创建标的
        Symbol newSymbol = new Symbol();
        newSymbol.setSymbol(symbol);
        newSymbol.setTradingType(tradingType);
        newSymbol.setBaseCurrency(baseCurrency);
        newSymbol.setQuoteCurrency(quoteCurrency);
        newSymbol.setMinQuantity(minQuantity);
        newSymbol.setMaxQuantity(maxQuantity);
        newSymbol.setQuantityPrecision(quantityPrecision);
        newSymbol.setPricePrecision(pricePrecision);
        newSymbol.setTickSize(tickSize);
        newSymbol.setFeeRate(feeRate);
        newSymbol.setSupportsLeverage(supportsLeverage);
        newSymbol.setSupportsShort(supportsShort);
        newSymbol.setStatus(SymbolStatus.INACTIVE); // 初始状态为未激活
        
        // 初始化风险限额配置（未配置状态）
        newSymbol.setRiskLimitConfig(new SymbolRiskLimitConfig());
        newSymbol.setRiskLimitConfigured(false);
        newSymbol.setRiskLimitConfigTime(null);
        
        // 保存到内存管理器
        memoryManager.addSymbol(newSymbol);
        
        log.info("标的创建成功: {}, 状态: {}", symbol, newSymbol.getStatus());
        
        return newSymbol;
    }
    
    /**
     * 配置标的的风险限额
     * 
     * @param symbol 交易对
     * @param riskLimitConfig 风险限额配置
     * @return 更新后的标的
     */
    public Symbol configureRiskLimit(String symbol, SymbolRiskLimitConfig riskLimitConfig) {
        log.info("开始配置标的风险限额: {}", symbol);
        
        // 获取标的
        Symbol symbolObj = memoryManager.getSymbol(symbol);
        if (symbolObj == null) {
            throw new IllegalArgumentException("标的 " + symbol + " 不存在");
        }
        
        // 设置风险限额配置
        riskLimitConfig.setSymbol(symbol);
        symbolObj.setRiskLimitConfig(riskLimitConfig);
        
        // 更新标的状态为可交易
        symbolObj.setStatus(SymbolStatus.ACTIVE);
        
        // 保存到内存管理器
        memoryManager.updateSymbol(symbolObj);
        
        // 同时更新配置管理器
        symbolRiskLimitConfigManager.setRiskLimitConfig(symbol, riskLimitConfig);
        
        log.info("标的风险限额配置成功: {}, 配置时间: {}", symbol, symbolObj.getRiskLimitConfigTime());
        
        return symbolObj;
    }
    
    /**
     * 使用默认配置配置标的的风险限额
     * 
     * @param symbol 交易对
     * @return 更新后的标的
     */
    public Symbol configureRiskLimitWithDefault(String symbol) {
        log.info("使用默认配置配置标的风险限额: {}", symbol);
        
        // 获取标的
        Symbol symbolObj = memoryManager.getSymbol(symbol);
        if (symbolObj == null) {
            throw new IllegalArgumentException("标的 " + symbol + " 不存在");
        }
        
        // 获取默认风险限额配置
        SymbolRiskLimitConfig defaultConfig = symbolRiskLimitConfigManager.getRiskLimitConfig(symbol);
        
        // 配置风险限额
        return configureRiskLimit(symbol, defaultConfig);
    }
    
    /**
     * 更新标的的风险限额配置
     * 
     * @param symbol 交易对
     * @param riskLimitConfig 新的风险限额配置
     * @return 更新后的标的
     */
    public Symbol updateRiskLimit(String symbol, SymbolRiskLimitConfig riskLimitConfig) {
        log.info("开始更新标的风险限额: {}", symbol);
        
        // 获取标的
        Symbol symbolObj = memoryManager.getSymbol(symbol);
        if (symbolObj == null) {
            throw new IllegalArgumentException("标的 " + symbol + " 不存在");
        }
        
        if (!symbolObj.isRiskLimitConfigured()) {
            throw new IllegalStateException("标的 " + symbol + " 尚未配置风险限额");
        }
        
        // 更新风险限额配置
        riskLimitConfig.setSymbol(symbol);
        symbolObj.setRiskLimitConfig(riskLimitConfig);
        
        // 保存到内存管理器
        memoryManager.updateSymbol(symbolObj);
        
        // 同时更新配置管理器
        symbolRiskLimitConfigManager.setRiskLimitConfig(symbol, riskLimitConfig);
        
        log.info("标的风险限额更新成功: {}, 更新时间: {}", symbol, symbolObj.getRiskLimitConfigTime());
        
        return symbolObj;
    }
    
    /**
     * 检查标的是否可以交易
     * 
     * @param symbol 交易对
     * @return 是否可以交易
     */
    public boolean canTrade(String symbol) {
        Symbol symbolObj = memoryManager.getSymbol(symbol);
        if (symbolObj == null) {
            return false;
        }
        
        return symbolObj.canTrade();
    }
    
    /**
     * 获取所有可交易的标的
     * 
     * @return 可交易的标的列表
     */
    public List<Symbol> getTradeableSymbols() {
        return memoryManager.getAllSymbols().values().stream()
                .filter(Symbol::canTrade)
                .collect(java.util.stream.Collectors.toList());
    }
    
    /**
     * 获取所有已配置风险限额的标的
     * 
     * @return 已配置风险限额的标的列表
     */
    public List<Symbol> getConfiguredSymbols() {
        return memoryManager.getAllSymbols().values().stream()
                .filter(Symbol::isRiskLimitConfigured)
                .collect(java.util.stream.Collectors.toList());
    }
    
    /**
     * 获取所有未配置风险限额的标的
     * 
     * @return 未配置风险限额的标的列表
     */
    public List<Symbol> getUnconfiguredSymbols() {
        return memoryManager.getAllSymbols().values().stream()
                .filter(symbol -> !symbol.isRiskLimitConfigured())
                .collect(java.util.stream.Collectors.toList());
    }
    
    /**
     * 激活标的（配置风险限额后自动激活）
     * 
     * @param symbol 交易对
     * @return 更新后的标的
     */
    public Symbol activateSymbol(String symbol) {
        log.info("激活标的: {}", symbol);
        
        Symbol symbolObj = memoryManager.getSymbol(symbol);
        if (symbolObj == null) {
            throw new IllegalArgumentException("标的 " + symbol + " 不存在");
        }
        
        if (!symbolObj.isRiskLimitConfigured()) {
            throw new IllegalStateException("标的 " + symbol + " 尚未配置风险限额，无法激活");
        }
        
        symbolObj.setStatus(SymbolStatus.ACTIVE);
        memoryManager.updateSymbol(symbolObj);
        
        log.info("标的激活成功: {}", symbol);
        
        return symbolObj;
    }
    
    /**
     * 停用标的
     * 
     * @param symbol 交易对
     * @return 更新后的标的
     */
    public Symbol deactivateSymbol(String symbol) {
        log.info("停用标的: {}", symbol);
        
        Symbol symbolObj = memoryManager.getSymbol(symbol);
        if (symbolObj == null) {
            throw new IllegalArgumentException("标的 " + symbol + " 不存在");
        }
        
        symbolObj.setStatus(SymbolStatus.INACTIVE);
        memoryManager.updateSymbol(symbolObj);
        
        log.info("标的停用成功: {}", symbol);
        
        return symbolObj;
    }
    
    /**
     * 删除标的
     * 
     * @param symbol 交易对
     */
    public void deleteSymbol(String symbol) {
        log.info("删除标的: {}", symbol);
        
        Symbol symbolObj = memoryManager.getSymbol(symbol);
        if (symbolObj == null) {
            throw new IllegalArgumentException("标的 " + symbol + " 不存在");
        }
        
        // 检查是否有活跃的订单或持仓
        if (hasActiveOrders(symbol) || hasActivePositions(symbol)) {
            throw new IllegalStateException("标的 " + symbol + " 有活跃的订单或持仓，无法删除");
        }
        
        // 从内存管理器删除
        memoryManager.removeSymbol(symbol);
        
        // 从配置管理器删除
        symbolRiskLimitConfigManager.removeRiskLimitConfig(symbol);
        
        log.info("标的删除成功: {}", symbol);
    }
    
    /**
     * 检查标的是否有活跃订单
     * 
     * @param symbol 交易对
     * @return 是否有活跃订单
     */
    private boolean hasActiveOrders(String symbol) {
        // 这里需要根据实际的订单管理逻辑来实现
        // 暂时返回false
        return false;
    }
    
    /**
     * 检查标的是否有活跃持仓
     * 
     * @param symbol 交易对
     * @return 是否有活跃持仓
     */
    private boolean hasActivePositions(String symbol) {
        // 这里需要根据实际的持仓管理逻辑来实现
        // 暂时返回false
        return false;
    }
    
    /**
     * 获取标的统计信息
     * 
     * @return 统计信息
     */
    public String getSymbolStats() {
        Map<String, Symbol> allSymbols = memoryManager.getAllSymbols();
        long totalCount = allSymbols.size();
        long configuredCount = allSymbols.values().stream()
                .filter(Symbol::isRiskLimitConfigured)
                .count();
        long tradeableCount = allSymbols.values().stream()
                .filter(Symbol::canTrade)
                .count();
        
        return String.format("标的统计: 总数=%d, 已配置=%d, 可交易=%d", 
                totalCount, configuredCount, tradeableCount);
    }
    
    /**
     * 批量创建标的（带默认风险限额配置）
     * 
     * @param symbols 标的信息列表
     * @return 创建的标的列表
     */
    public List<Symbol> batchCreateSymbols(List<SymbolCreationRequest> symbols) {
        log.info("批量创建标的，数量: {}", symbols.size());
        
        List<Symbol> createdSymbols = new java.util.ArrayList<>();
        
        for (SymbolCreationRequest request : symbols) {
            try {
                // 创建标的
                Symbol symbol = createSymbol(
                    request.getSymbol(),
                    request.getTradingType(),
                    request.getBaseCurrency(),
                    request.getQuoteCurrency(),
                    request.getMinQuantity(),
                    request.getMaxQuantity(),
                    request.getQuantityPrecision(),
                    request.getPricePrecision(),
                    request.getTickSize(),
                    request.getFeeRate(),
                    request.getSupportsLeverage(),
                    request.getSupportsShort()
                );
                
                // 使用默认配置配置风险限额
                if (request.getConfigureRiskLimit()) {
                    configureRiskLimitWithDefault(request.getSymbol());
                }
                
                createdSymbols.add(symbol);
                
            } catch (Exception e) {
                log.error("创建标的失败: {}, 错误: {}", request.getSymbol(), e.getMessage());
            }
        }
        
        log.info("批量创建标的完成，成功: {}/{}", createdSymbols.size(), symbols.size());
        
        return createdSymbols;
    }
    
    /**
     * 标的创建请求
     */
    @lombok.Data
    public static class SymbolCreationRequest {
        private String symbol;
        private TradingType tradingType;
        private String baseCurrency;
        private String quoteCurrency;
        private BigDecimal minQuantity;
        private BigDecimal maxQuantity;
        private Integer quantityPrecision;
        private Integer pricePrecision;
        private BigDecimal tickSize;
        private BigDecimal feeRate;
        private Boolean supportsLeverage;
        private Boolean supportsShort;
        private Boolean configureRiskLimit = true; // 是否自动配置风险限额
    }
} 