package com.exchange.common.response;

import com.exchange.common.error.CommonErrorCode;
import com.exchange.common.error.ErrorCode;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 统一API响应对象
 * 所有API接口都使用此格式返回数据
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse<T> {
    
    /**
     * 响应状态码
     */
    private int code;
    
    /**
     * 响应消息
     */
    private String message;
    
    /**
     * 响应数据
     */
    private T data;
    
    /**
     * 响应时间戳
     */
    private String timestamp;
    
    /**
     * 请求追踪ID（用于日志追踪）
     */
    private String traceId;
    
    public ApiResponse() {
        this.timestamp = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
    }
    
    public ApiResponse(ErrorCode errorCode) {
        this();
        this.code = errorCode.getCode();
        this.message = errorCode.getMessage();
    }
    
    public ApiResponse(ErrorCode errorCode, T data) {
        this(errorCode);
        this.data = data;
    }
    
    public ApiResponse(int code, String message) {
        this();
        this.code = code;
        this.message = message;
    }
    
    public ApiResponse(int code, String message, T data) {
        this(code, message);
        this.data = data;
    }
    
    /**
     * 创建成功响应
     */
    public static <T> ApiResponse<T> success() {
        return new ApiResponse<>(CommonErrorCode.SUCCESS);
    }
    
    /**
     * 创建成功响应（带数据）
     */
    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(CommonErrorCode.SUCCESS, data);
    }
    
    /**
     * 创建成功响应（自定义消息）
     */
    public static <T> ApiResponse<T> success(String message, T data) {
        return new ApiResponse<>(CommonErrorCode.SUCCESS.getCode(), message, data);
    }
    
    /**
     * 创建失败响应
     */
    public static <T> ApiResponse<T> error(ErrorCode errorCode) {
        return new ApiResponse<>(errorCode);
    }
    
    /**
     * 创建失败响应（带数据）
     */
    public static <T> ApiResponse<T> error(ErrorCode errorCode, T data) {
        return new ApiResponse<>(errorCode, data);
    }
    
    /**
     * 创建失败响应（自定义消息）
     */
    public static <T> ApiResponse<T> error(int code, String message) {
        return new ApiResponse<>(code, message);
    }
    
    /**
     * 创建失败响应（自定义消息和数据）
     */
    public static <T> ApiResponse<T> error(int code, String message, T data) {
        return new ApiResponse<>(code, message, data);
    }
    
    /**
     * 判断是否为成功响应
     */
    public boolean isSuccess() {
        return CommonErrorCode.SUCCESS.getCode() == this.code;
    }
    
    // Getter and Setter methods
    public int getCode() {
        return code;
    }
    
    public void setCode(int code) {
        this.code = code;
    }
    
    public String getMessage() {
        return message;
    }
    
    public void setMessage(String message) {
        this.message = message;
    }
    
    public T getData() {
        return data;
    }
    
    public void setData(T data) {
        this.data = data;
    }
    
    public String getTimestamp() {
        return timestamp;
    }
    
    public void setTimestamp(String timestamp) {
        this.timestamp = timestamp;
    }
    
    public String getTraceId() {
        return traceId;
    }
    
    public void setTraceId(String traceId) {
        this.traceId = traceId;
    }
    
    @Override
    public String toString() {
        return "ApiResponse{" +
                "code=" + code +
                ", message='" + message + '\'' +
                ", data=" + data +
                ", timestamp='" + timestamp + '\'' +
                ", traceId='" + traceId + '\'' +
                '}';
    }
} 