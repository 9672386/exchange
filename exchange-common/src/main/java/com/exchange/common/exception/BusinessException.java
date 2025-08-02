package com.exchange.common.exception;

import com.exchange.common.error.CommonErrorCode;
import com.exchange.common.error.ErrorCode;

/**
 * 业务异常类
 * 用于抛出业务相关的异常，会被全局异常处理器捕获并转换为统一响应格式
 */
public class BusinessException extends RuntimeException {
    
    private final ErrorCode errorCode;
    private final Object data;
    
    public BusinessException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
        this.data = null;
    }
    
    public BusinessException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
        this.data = null;
    }
    
    public BusinessException(ErrorCode errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
        this.data = null;
    }
    
    public BusinessException(ErrorCode errorCode, Object data) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
        this.data = data;
    }
    
    public BusinessException(ErrorCode errorCode, String message, Object data) {
        super(message);
        this.errorCode = errorCode;
        this.data = data;
    }
    
    public BusinessException(ErrorCode errorCode, String message, Object data, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
        this.data = data;
    }
    
    public ErrorCode getErrorCode() {
        return errorCode;
    }
    
    public Object getData() {
        return data;
    }
    
    /**
     * 创建通用业务异常
     * 各模块应该在自己的异常类中定义具体的业务异常
     */
    public static BusinessException businessError(ErrorCode errorCode) {
        return new BusinessException(errorCode);
    }
    
    public static BusinessException businessError(ErrorCode errorCode, String message) {
        return new BusinessException(errorCode, message);
    }
    
    public static BusinessException businessError(ErrorCode errorCode, Object data) {
        return new BusinessException(errorCode, data);
    }
    
    /**
     * 创建通用异常
     */
    public static BusinessException parameterError(String message) {
        return new BusinessException(CommonErrorCode.PARAMETER_ERROR, message);
    }
    
    public static BusinessException systemError(String message) {
        return new BusinessException(CommonErrorCode.SYSTEM_ERROR, message);
    }
    
    public static BusinessException unauthorized() {
        return new BusinessException(CommonErrorCode.UNAUTHORIZED);
    }
    
    public static BusinessException forbidden() {
        return new BusinessException(CommonErrorCode.FORBIDDEN);
    }
    
    public static BusinessException notFound() {
        return new BusinessException(CommonErrorCode.NOT_FOUND);
    }
} 