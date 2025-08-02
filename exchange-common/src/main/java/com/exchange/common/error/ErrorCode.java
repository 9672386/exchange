package com.exchange.common.error;

/**
 * 错误码接口
 * 定义错误码的基本结构，便于各模块扩展自己的错误码
 */
public interface ErrorCode {
    
    /**
     * 获取错误码
     */
    int getCode();
    
    /**
     * 获取错误消息
     */
    String getMessage();
    
    /**
     * 判断是否为成功状态
     */
    default boolean isSuccess() {
        return getCode() == CommonErrorCode.SUCCESS.getCode();
    }
    
    /**
     * 获取错误码所属模块名称
     */
    default String getModuleName() {
        return ErrorCodeSegment.getModuleName(getCode());
    }
} 