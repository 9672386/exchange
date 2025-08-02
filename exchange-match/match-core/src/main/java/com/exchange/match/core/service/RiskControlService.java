package com.exchange.match.core.service;

import com.exchange.match.core.model.RiskLevel;
import com.exchange.match.core.model.ADLStrategy;

import java.math.BigDecimal;

/**
 * 风控服务接口
 * 负责风险率计算、强平触发判断和发送强平指令到撮合引擎
 * 
 * 注意：此接口在实际项目中应该由独立的风控服务实现
 * 撮合引擎只负责接收风控服务的指令并执行相应的操作
 */
public interface RiskControlService {
    
    /**
     * 计算用户风险率
     * 
     * @param userId 用户ID
     * @param symbol 交易对
     * @return 风险率 (0-1)
     */
    BigDecimal calculateRiskRatio(Long userId, String symbol);
    
    /**
     * 获取用户当前风险等级
     * 
     * @param userId 用户ID
     * @param symbol 交易对
     * @return 风险等级
     */
    RiskLevel getCurrentRiskLevel(Long userId, String symbol);
    
    /**
     * 检查是否需要风险降档
     * 
     * @param userId 用户ID
     * @param symbol 交易对
     * @return 是否需要风险降档
     */
    boolean needRiskReduction(Long userId, String symbol);
    
    /**
     * 检查是否需要强平
     * 
     * @param userId 用户ID
     * @param symbol 交易对
     * @return 是否需要强平
     */
    boolean needLiquidation(Long userId, String symbol);
    
    /**
     * 确定ADL策略
     * 
     * @param liquidationType 强平类型
     * @param riskLevel 风险等级
     * @return ADL策略
     */
    ADLStrategy determineADLStrategy(String liquidationType, RiskLevel riskLevel);
    
    /**
     * 触发强平
     * 
     * @param userId 用户ID
     * @param symbol 交易对
     * @param liquidationType 强平类型
     * @param reason 强平原因
     */
    void triggerLiquidation(Long userId, String symbol, String liquidationType, String reason);
    
    /**
     * 监控用户风险状态
     * 
     * @param userId 用户ID
     * @param symbol 交易对
     */
    void monitorUserRisk(Long userId, String symbol);
    
    /**
     * 获取用户保证金
     * 
     * @param userId 用户ID
     * @param symbol 交易对
     * @return 保证金金额
     */
    BigDecimal getUserMargin(Long userId, String symbol);
    
    /**
     * 获取当前市场价格
     * 
     * @param symbol 交易对
     * @return 当前价格
     */
    BigDecimal getCurrentPrice(String symbol);
    
    /**
     * 计算未实现盈亏
     * 
     * @param userId 用户ID
     * @param symbol 交易对
     * @return 未实现盈亏
     */
    BigDecimal calculateUnrealizedPnl(Long userId, String symbol);
} 