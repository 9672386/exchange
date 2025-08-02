package com.exchange.match.core.model;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 标的模型
 */
@Data
public class Symbol {
    
    /**
     * 交易对
     */
    private String symbol;
    
    /**
     * 交易类型
     */
    private TradingType tradingType;
    
    /**
     * 基础货币（如BTC）
     */
    private String baseCurrency;
    
    /**
     * 计价货币（如USDT）
     */
    private String quoteCurrency;
    
    /**
     * 最小交易数量
     */
    private BigDecimal minQuantity;
    
    /**
     * 最大交易数量
     */
    private BigDecimal maxQuantity;
    
    /**
     * 数量精度
     */
    private Integer quantityPrecision;
    
    /**
     * 价格精度
     */
    private Integer pricePrecision;
    
    /**
     * 最小价格变动单位
     */
    private BigDecimal tickSize;
    
    /**
     * 手续费率
     */
    private BigDecimal feeRate;
    
    /**
     * 市价单买单最大滑点（百分比，如0.01表示1%）
     */
    private BigDecimal marketBuyMaxSlippage;
    
    /**
     * 市价单卖单最大滑点（百分比，如0.01表示1%）
     */
    private BigDecimal marketSellMaxSlippage;
    
    /**
     * 市价单最大吃单深度（订单薄层数）
     */
    private Integer marketMaxDepth;
    
    /**
     * 是否支持杠杆交易
     */
    private Boolean supportsLeverage;
    
    /**
     * 最大杠杆倍数
     */
    private BigDecimal maxLeverage;
    
    /**
     * 是否支持做空
     */
    private Boolean supportsShort;
    
    /**
     * 交易状态
     */
    private SymbolStatus status;
    
    // ========== 风险限额配置 ==========
    
    /**
     * 风险限额配置（标的创建时必须配置）
     */
    private SymbolRiskLimitConfig riskLimitConfig;
    
    /**
     * 是否已配置风险限额
     */
    private Boolean riskLimitConfigured = false;
    
    /**
     * 风险限额配置时间
     */
    private LocalDateTime riskLimitConfigTime;
    
    /**
     * 创建时间
     */
    private LocalDateTime createTime;
    
    /**
     * 更新时间
     */
    private LocalDateTime updateTime;
    
    public Symbol() {
        this.createTime = LocalDateTime.now();
        this.updateTime = LocalDateTime.now();
        this.status = SymbolStatus.ACTIVE;
        this.tradingType = TradingType.SPOT; // 默认为现货
        this.supportsLeverage = false;
        this.supportsShort = false;
        this.maxLeverage = BigDecimal.ONE;
        this.feeRate = BigDecimal.ZERO;
        this.marketBuyMaxSlippage = new BigDecimal("0.01"); // 默认1%滑点
        this.marketSellMaxSlippage = new BigDecimal("0.01"); // 默认1%滑点
        this.marketMaxDepth = 10; // 默认最多吃10层订单薄
        
        // 初始化风险限额配置
        initializeRiskConfig();
    }
    
    /**
     * 初始化风险配置
     */
    private void initializeRiskConfig() {
        // 初始化风险限额配置
        this.riskLimitConfig = new SymbolRiskLimitConfig();
        this.riskLimitConfig.setSymbol(this.symbol);
        this.riskLimitConfigured = false;
        this.riskLimitConfigTime = null;
    }
    
    /**
     * 验证价格是否有效
     */
    public boolean isValidPrice(BigDecimal price) {
        if (price == null || price.compareTo(BigDecimal.ZERO) <= 0) {
            return false;
        }
        
        // 检查价格精度
        BigDecimal remainder = price.remainder(tickSize);
        return remainder.compareTo(BigDecimal.ZERO) == 0;
    }
    
    /**
     * 验证数量是否有效
     */
    public boolean isValidQuantity(BigDecimal quantity) {
        if (quantity == null || quantity.compareTo(BigDecimal.ZERO) <= 0) {
            return false;
        }
        
        // 检查数量范围
        if (quantity.compareTo(minQuantity) < 0 || quantity.compareTo(maxQuantity) > 0) {
            return false;
        }
        
        // 检查数量精度
        BigDecimal scaled = quantity.setScale(quantityPrecision, BigDecimal.ROUND_DOWN);
        return scaled.compareTo(quantity) == 0;
    }
    
    /**
     * 格式化价格
     */
    public BigDecimal formatPrice(BigDecimal price) {
        if (price == null) {
            return null;
        }
        
        // 根据tickSize格式化价格
        BigDecimal remainder = price.remainder(tickSize);
        return price.subtract(remainder);
    }
    
    /**
     * 格式化数量
     */
    public BigDecimal formatQuantity(BigDecimal quantity) {
        if (quantity == null) {
            return null;
        }
        
        return quantity.setScale(quantityPrecision, BigDecimal.ROUND_DOWN);
    }
    
    /**
     * 计算手续费
     */
    public BigDecimal calculateFee(BigDecimal quantity, BigDecimal price) {
        BigDecimal tradeValue = quantity.multiply(price);
        return tradeValue.multiply(feeRate);
    }
    
    /**
     * 检查是否为现货交易
     */
    public boolean isSpot() {
        return tradingType.isSpot();
    }
    
    /**
     * 检查是否为合约交易
     */
    public boolean isFutures() {
        return tradingType.isFutures();
    }
    
    /**
     * 检查是否为永续合约
     */
    public boolean isPerpetual() {
        return tradingType.isPerpetual();
    }
    
    /**
     * 检查是否支持仓位管理
     */
    public boolean supportsPosition() {
        return tradingType.supportsPosition();
    }
    
    /**
     * 检查是否支持杠杆交易
     */
    public boolean isLeverageSupported() {
        return tradingType.supportsLeverage() && Boolean.TRUE.equals(supportsLeverage);
    }
    
    /**
     * 检查是否支持做空
     */
    public boolean isShortSupported() {
        return tradingType.supportsShort() && Boolean.TRUE.equals(supportsShort);
    }
    
    /**
     * 检查是否可交易
     */
    public boolean isTradeable() {
        return status == SymbolStatus.ACTIVE;
    }
    
    /**
     * 计算市价单买单的最大可接受价格
     * @param bestAskPrice 当前最优卖价
     * @return 最大可接受价格
     */
    public BigDecimal calculateMarketBuyMaxPrice(BigDecimal bestAskPrice) {
        if (bestAskPrice == null || bestAskPrice.compareTo(BigDecimal.ZERO) <= 0) {
            return null;
        }
        
        BigDecimal slippageAmount = bestAskPrice.multiply(marketBuyMaxSlippage);
        return bestAskPrice.add(slippageAmount);
    }
    
    /**
     * 计算市价单卖单的最小可接受价格
     * @param bestBidPrice 当前最优买价
     * @return 最小可接受价格
     */
    public BigDecimal calculateMarketSellMinPrice(BigDecimal bestBidPrice) {
        if (bestBidPrice == null || bestBidPrice.compareTo(BigDecimal.ZERO) <= 0) {
            return null;
        }
        
        BigDecimal slippageAmount = bestBidPrice.multiply(marketSellMaxSlippage);
        return bestBidPrice.subtract(slippageAmount);
    }
    
    /**
     * 验证市价单买单价格是否在滑点范围内
     * @param orderPrice 订单价格
     * @param bestAskPrice 当前最优卖价
     * @return 是否在滑点范围内
     */
    public boolean isValidMarketBuyPrice(BigDecimal orderPrice, BigDecimal bestAskPrice) {
        if (orderPrice == null || bestAskPrice == null) {
            return false;
        }
        
        BigDecimal maxPrice = calculateMarketBuyMaxPrice(bestAskPrice);
        return orderPrice.compareTo(maxPrice) <= 0;
    }
    
    /**
     * 验证市价单卖单价格是否在滑点范围内
     * @param orderPrice 订单价格
     * @param bestBidPrice 当前最优买价
     * @return 是否在滑点范围内
     */
    public boolean isValidMarketSellPrice(BigDecimal orderPrice, BigDecimal bestBidPrice) {
        if (orderPrice == null || bestBidPrice == null) {
            return false;
        }
        
        BigDecimal minPrice = calculateMarketSellMinPrice(bestBidPrice);
        return orderPrice.compareTo(minPrice) >= 0;
    }
    
    /**
     * 获取市价单最大吃单深度
     * @return 最大深度
     */
    public Integer getMarketMaxDepth() {
        return marketMaxDepth != null ? marketMaxDepth : 10;
    }
    
    /**
     * 设置市价单滑点参数
     * @param buySlippage 买单滑点
     * @param sellSlippage 卖单滑点
     * @param maxDepth 最大深度
     */
    public void setMarketSlippageParams(BigDecimal buySlippage, BigDecimal sellSlippage, Integer maxDepth) {
        this.marketBuyMaxSlippage = buySlippage;
        this.marketSellMaxSlippage = sellSlippage;
        this.marketMaxDepth = maxDepth;
    }
    
    // ========== 风险限额相关方法 ==========
    
    /**
     * 检查是否已配置风险限额
     * @return 是否已配置
     */
    public boolean isRiskLimitConfigured() {
        return riskLimitConfigured && riskLimitConfig != null;
    }
    
    /**
     * 设置风险限额配置
     * @param riskLimitConfig 风险限额配置
     */
    public void setRiskLimitConfig(SymbolRiskLimitConfig riskLimitConfig) {
        this.riskLimitConfig = riskLimitConfig;
        this.riskLimitConfigured = true;
        this.riskLimitConfigTime = LocalDateTime.now();
    }
    
    /**
     * 获取风险限额配置
     * @return 风险限额配置
     */
    public SymbolRiskLimitConfig getRiskLimitConfig() {
        return riskLimitConfig;
    }
    
    /**
     * 检查是否可以交易（必须有风险限额配置）
     * @return 是否可以交易
     */
    public boolean canTrade() {
        return isTradeable() && isRiskLimitConfigured();
    }
    
    /**
     * 获取逐仓模式风险限额配置
     */
    public SymbolRiskLimitConfig.IsolatedModeRiskLimitConfig getIsolatedModeRiskLimitConfig() {
        return riskLimitConfig != null ? riskLimitConfig.getIsolatedModeConfig() : null;
    }
    
    /**
     * 获取全仓模式风险限额配置
     */
    public SymbolRiskLimitConfig.CrossModeRiskLimitConfig getCrossModeRiskLimitConfig() {
        return riskLimitConfig != null ? riskLimitConfig.getCrossModeConfig() : null;
    }
    
    /**
     * 获取强平配置
     */
    public SymbolRiskLimitConfig.LiquidationRiskLimitConfig getLiquidationRiskLimitConfig() {
        return riskLimitConfig != null ? riskLimitConfig.getLiquidationConfig() : null;
    }
    

} 