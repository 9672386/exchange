package com.exchange.common.error;

/**
 * 错误码段管理
 * 定义各个模块的错误码段范围，便于管理和扩展
 */
public class ErrorCodeSegment {
    
    /**
     * 通用错误码段 (1000-1999)
     */
    public static final int COMMON_START = 1000;
    public static final int COMMON_END = 1999;
    
    /**
     * 用户模块错误码段 (2000-2999)
     */
    public static final int USER_START = 2000;
    public static final int USER_END = 2999;
    
    /**
     * 账户模块错误码段 (3000-3999)
     */
    public static final int ACCOUNT_START = 3000;
    public static final int ACCOUNT_END = 3999;
    
    /**
     * 交易模块错误码段 (4000-4999)
     */
    public static final int TRADE_START = 4000;
    public static final int TRADE_END = 4999;
    
    /**
     * 撮合模块错误码段 (5000-5999)
     */
    public static final int MATCH_START = 5000;
    public static final int MATCH_END = 5999;
    
    /**
     * 行情模块错误码段 (6000-6999)
     */
    public static final int QUOTE_START = 6000;
    public static final int QUOTE_END = 6999;
    
    /**
     * 风控模块错误码段 (7000-7999)
     */
    public static final int RISK_START = 7000;
    public static final int RISK_END = 7999;
    
    /**
     * 消息模块错误码段 (8000-8999)
     */
    public static final int MESSAGE_START = 8000;
    public static final int MESSAGE_END = 8999;
    
    /**
     * 管理后台错误码段 (9000-9999)
     */
    public static final int ADMIN_START = 9000;
    public static final int ADMIN_END = 9999;
    
    /**
     * 检查错误码是否在指定范围内
     */
    public static boolean isInRange(int code, int start, int end) {
        return code >= start && code <= end;
    }
    
    /**
     * 检查错误码是否属于通用模块
     */
    public static boolean isCommonError(int code) {
        return isInRange(code, COMMON_START, COMMON_END);
    }
    
    /**
     * 检查错误码是否属于用户模块
     */
    public static boolean isUserError(int code) {
        return isInRange(code, USER_START, USER_END);
    }
    
    /**
     * 检查错误码是否属于账户模块
     */
    public static boolean isAccountError(int code) {
        return isInRange(code, ACCOUNT_START, ACCOUNT_END);
    }
    
    /**
     * 检查错误码是否属于交易模块
     */
    public static boolean isTradeError(int code) {
        return isInRange(code, TRADE_START, TRADE_END);
    }
    
    /**
     * 检查错误码是否属于撮合模块
     */
    public static boolean isMatchError(int code) {
        return isInRange(code, MATCH_START, MATCH_END);
    }
    
    /**
     * 检查错误码是否属于行情模块
     */
    public static boolean isQuoteError(int code) {
        return isInRange(code, QUOTE_START, QUOTE_END);
    }
    
    /**
     * 检查错误码是否属于风控模块
     */
    public static boolean isRiskError(int code) {
        return isInRange(code, RISK_START, RISK_END);
    }
    
    /**
     * 检查错误码是否属于消息模块
     */
    public static boolean isMessageError(int code) {
        return isInRange(code, MESSAGE_START, MESSAGE_END);
    }
    
    /**
     * 检查错误码是否属于管理后台模块
     */
    public static boolean isAdminError(int code) {
        return isInRange(code, ADMIN_START, ADMIN_END);
    }
    
    /**
     * 获取错误码所属模块名称
     */
    public static String getModuleName(int code) {
        if (isCommonError(code)) {
            return "通用模块";
        } else if (isUserError(code)) {
            return "用户模块";
        } else if (isAccountError(code)) {
            return "账户模块";
        } else if (isTradeError(code)) {
            return "交易模块";
        } else if (isMatchError(code)) {
            return "撮合模块";
        } else if (isQuoteError(code)) {
            return "行情模块";
        } else if (isRiskError(code)) {
            return "风控模块";
        } else if (isMessageError(code)) {
            return "消息模块";
        } else if (isAdminError(code)) {
            return "管理后台";
        } else {
            return "未知模块";
        }
    }
} 