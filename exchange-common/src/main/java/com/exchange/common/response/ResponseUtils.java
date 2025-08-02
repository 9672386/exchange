package com.exchange.common.response;

import com.exchange.common.error.ErrorCode;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import jakarta.servlet.http.HttpServletRequest;

/**
 * 响应工具类
 * 提供便捷的API响应创建方法
 */
public class ResponseUtils {
    
    /**
     * 创建成功响应
     */
    public static <T> ApiResponse<T> success() {
        ApiResponse<T> response = ApiResponse.success();
        response.setTraceId(getTraceId());
        return response;
    }
    
    /**
     * 创建成功响应（带数据）
     */
    public static <T> ApiResponse<T> success(T data) {
        ApiResponse<T> response = ApiResponse.success(data);
        response.setTraceId(getTraceId());
        return response;
    }
    
    /**
     * 创建成功响应（自定义消息）
     */
    public static <T> ApiResponse<T> success(String message, T data) {
        ApiResponse<T> response = ApiResponse.success(message, data);
        response.setTraceId(getTraceId());
        return response;
    }
    
    /**
     * 创建失败响应
     */
    public static <T> ApiResponse<T> error(ErrorCode errorCode) {
        ApiResponse<T> response = ApiResponse.error(errorCode);
        response.setTraceId(getTraceId());
        return response;
    }
    
    /**
     * 创建失败响应（带数据）
     */
    public static <T> ApiResponse<T> error(ErrorCode errorCode, T data) {
        ApiResponse<T> response = ApiResponse.error(errorCode, data);
        response.setTraceId(getTraceId());
        return response;
    }
    
    /**
     * 创建失败响应（自定义消息）
     */
    public static <T> ApiResponse<T> error(int code, String message) {
        ApiResponse<T> response = ApiResponse.error(code, message);
        response.setTraceId(getTraceId());
        return response;
    }
    
    /**
     * 创建失败响应（自定义消息和数据）
     */
    public static <T> ApiResponse<T> error(int code, String message, T data) {
        ApiResponse<T> response = ApiResponse.error(code, message, data);
        response.setTraceId(getTraceId());
        return response;
    }
    
    /**
     * 获取请求追踪ID
     */
    private static String getTraceId() {
        try {
            ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attributes != null) {
                HttpServletRequest request = attributes.getRequest();
                return request.getHeader("X-Trace-Id");
            }
        } catch (Exception e) {
            // 忽略异常，返回null
        }
        return null;
    }
} 