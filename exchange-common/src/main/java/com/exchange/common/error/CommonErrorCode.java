package com.exchange.common.error;

/**
 * 通用错误码枚举
 * 定义系统级的通用错误码，适用于所有模块
 */
public enum CommonErrorCode implements ErrorCode {
    
    // 通用错误码 (1000-1999)
    SUCCESS(1000, "操作成功"),
    SYSTEM_ERROR(1001, "系统内部错误"),
    PARAMETER_ERROR(1002, "参数错误"),
    UNAUTHORIZED(1003, "未授权访问"),
    FORBIDDEN(1004, "禁止访问"),
    NOT_FOUND(1005, "资源不存在"),
    TIMEOUT(1006, "请求超时"),
    RATE_LIMIT(1007, "请求频率限制"),
    SERVICE_UNAVAILABLE(1008, "服务不可用"),
    VALIDATION_ERROR(1009, "数据验证失败"),
    INTERNAL_SERVER_ERROR(1010, "服务器内部错误"),
    BAD_REQUEST(1011, "请求参数错误"),
    METHOD_NOT_ALLOWED(1012, "请求方法不允许"),
    UNSUPPORTED_MEDIA_TYPE(1013, "不支持的媒体类型"),
    TOO_MANY_REQUESTS(1014, "请求过于频繁");
    
    private final int code;
    private final String message;
    
    CommonErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }
    
    @Override
    public int getCode() {
        return code;
    }
    
    @Override
    public String getMessage() {
        return message;
    }
    
    /**
     * 根据错误码获取错误枚举
     */
    public static CommonErrorCode fromCode(int code) {
        for (CommonErrorCode errorCode : values()) {
            if (errorCode.getCode() == code) {
                return errorCode;
            }
        }
        return SYSTEM_ERROR;
    }
} 