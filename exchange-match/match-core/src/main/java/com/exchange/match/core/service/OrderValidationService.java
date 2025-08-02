package com.exchange.match.core.service;

import com.exchange.match.core.model.Order;
import com.exchange.match.core.model.Symbol;
import com.exchange.match.core.model.SymbolRiskLimitConfig;
import com.exchange.match.core.model.Position;
import com.exchange.match.core.memory.MemoryManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

/**
 * 订单验证服务
 * 负责验证订单的有效性，包括标的风险限额配置检查和杠杆验证
 */
@Slf4j
@Service
public class OrderValidationService {
    
    @Autowired
    private MemoryManager memoryManager;
    
    @Autowired
    private SymbolManagementService symbolManagementService;
    
    @Autowired
    private SymbolRiskLimitConfigManager symbolRiskLimitConfigManager;
    
    @Autowired
    private CrossPositionManager crossPositionManager;
    
    @Autowired
    private LeverageValidationService leverageValidationService;
    
    /**
     * 验证订单是否可以下单
     * 
     * @param order 订单
     * @return 验证结果
     */
    public OrderValidationResult validateOrder(Order order) {
        OrderValidationResult result = new OrderValidationResult();
        result.setValid(true);
        
        try {
            // 1. 检查标的是否存在
            Symbol symbol = memoryManager.getSymbol(order.getSymbol());
            if (symbol == null) {
                result.setValid(false);
                result.setErrorCode("SYMBOL_NOT_FOUND");
                result.setErrorMessage("标的不存在: " + order.getSymbol());
                return result;
            }
            
            // 2. 检查标的是否可以交易（必须有风险限额配置）
            if (!symbol.canTrade()) {
                result.setValid(false);
                result.setErrorCode("SYMBOL_NOT_TRADEABLE");
                if (!symbol.isRiskLimitConfigured()) {
                    result.setErrorMessage("标的未配置风险限额，无法交易: " + order.getSymbol());
                } else {
                    result.setErrorMessage("标的不可交易: " + order.getSymbol());
                }
                return result;
            }
            
            // 3. 检查标的交易状态
            if (!symbol.isTradeable()) {
                result.setValid(false);
                result.setErrorCode("SYMBOL_INACTIVE");
                result.setErrorMessage("标的未激活: " + order.getSymbol());
                return result;
            }
            
            // 4. 验证价格
            if (!symbol.isValidPrice(order.getPrice())) {
                result.setValid(false);
                result.setErrorCode("INVALID_PRICE");
                result.setErrorMessage("价格无效: " + order.getPrice());
                return result;
            }
            
            // 5. 验证数量
            if (!symbol.isValidQuantity(order.getQuantity())) {
                result.setValid(false);
                result.setErrorCode("INVALID_QUANTITY");
                result.setErrorMessage("数量无效: " + order.getQuantity());
                return result;
            }
            
            // 6. 验证杠杆
            LeverageValidationService.LeverageValidationResult leverageResult = leverageValidationService.validateOrderLeverage(order);
            if (!leverageResult.isValid()) {
                result.setValid(false);
                result.setErrorCode(leverageResult.getErrorCode());
                result.setErrorMessage(leverageResult.getErrorMessage());
                return result;
            }
            
            // 7. 检查做空支持
            if (order.getSide() == com.exchange.match.core.model.OrderSide.SELL && 
                order.getPositionAction() == com.exchange.match.core.model.PositionAction.OPEN) {
                if (!symbol.isShortSupported()) {
                    result.setValid(false);
                    result.setErrorCode("SHORT_NOT_SUPPORTED");
                    result.setErrorMessage("标的不支持做空: " + order.getSymbol());
                    return result;
                }
            }
            
            log.debug("订单验证通过: orderId={}, symbol={}, leverage={}", 
                     order.getOrderId(), order.getSymbol(), order.getLeverage());
            
        } catch (Exception e) {
            log.error("订单验证异常: orderId={}, error={}", order.getOrderId(), e.getMessage());
            result.setValid(false);
            result.setErrorCode("VALIDATION_ERROR");
            result.setErrorMessage("订单验证异常: " + e.getMessage());
        }
        
        return result;
    }
    

    
    /**
     * 检查标的是否可以交易
     * 
     * @param symbol 交易对
     * @return 是否可以交易
     */
    public boolean canTradeSymbol(String symbol) {
        return symbolManagementService.canTrade(symbol);
    }
    
    /**
     * 获取标的交易状态信息
     * 
     * @param symbol 交易对
     * @return 状态信息
     */
    public SymbolTradeStatus getSymbolTradeStatus(String symbol) {
        SymbolTradeStatus status = new SymbolTradeStatus();
        status.setSymbol(symbol);
        
        Symbol symbolObj = memoryManager.getSymbol(symbol);
        if (symbolObj == null) {
            status.setExists(false);
            status.setTradeable(false);
            status.setRiskLimitConfigured(false);
            status.setMessage("标的不存在");
            return status;
        }
        
        status.setExists(true);
        status.setTradeable(symbolObj.canTrade());
        status.setRiskLimitConfigured(symbolObj.isRiskLimitConfigured());
        status.setActive(symbolObj.isTradeable());
        
        if (!symbolObj.isRiskLimitConfigured()) {
            status.setMessage("标的未配置风险限额");
        } else if (!symbolObj.isTradeable()) {
            status.setMessage("标的未激活");
        } else {
            status.setMessage("可以交易");
        }
        
        return status;
    }
    
    /**
     * 订单验证结果
     */
    @lombok.Data
    public static class OrderValidationResult {
        private boolean valid;
        private String errorCode;
        private String errorMessage;
        private String orderId;
        private String symbol;
    }
    
    /**
     * 标的交易状态
     */
    @lombok.Data
    public static class SymbolTradeStatus {
        private String symbol;
        private boolean exists;
        private boolean tradeable;
        private boolean riskLimitConfigured;
        private boolean active;
        private String message;
    }
} 