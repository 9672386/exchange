package com.exchange.message.error;

import com.exchange.common.error.ErrorCode;

/**
 * 消息模块错误码枚举
 * 定义消息相关的错误码 (8000-8999)
 */
public enum MessageErrorCode implements ErrorCode {
    
    MESSAGE_SEND_FAILED(8000, "消息发送失败"),
    MESSAGE_TEMPLATE_NOT_FOUND(8001, "消息模板不存在"),
    MESSAGE_PLATFORM_ERROR(8002, "消息平台错误"),
    MESSAGE_RATE_LIMIT(8003, "消息发送频率限制"),
    MESSAGE_CONTENT_INVALID(8004, "消息内容无效"),
    MESSAGE_RECIPIENT_INVALID(8005, "消息接收者无效"),
    MESSAGE_CHANNEL_NOT_SUPPORTED(8006, "不支持的消息渠道"),
    MESSAGE_TEMPLATE_PARSE_ERROR(8007, "消息模板解析错误"),
    MESSAGE_VARIABLE_MISSING(8008, "消息变量缺失"),
    MESSAGE_ENCODING_ERROR(8009, "消息编码错误"),
    MESSAGE_DELIVERY_FAILED(8010, "消息投递失败"),
    MESSAGE_READ_FAILED(8011, "消息读取失败"),
    MESSAGE_DELETE_FAILED(8012, "消息删除失败"),
    MESSAGE_UPDATE_FAILED(8013, "消息更新失败"),
    MESSAGE_BATCH_SEND_FAILED(8014, "批量消息发送失败"),
    MESSAGE_SCHEDULE_FAILED(8015, "消息调度失败"),
    MESSAGE_CANCEL_FAILED(8016, "消息取消失败"),
    MESSAGE_RETRY_FAILED(8017, "消息重试失败"),
    MESSAGE_QUEUE_FULL(8018, "消息队列已满"),
    MESSAGE_PROCESSING_TIMEOUT(8019, "消息处理超时"),
    MESSAGE_SYSTEM_BUSY(8020, "消息系统繁忙");
    
    private final int code;
    private final String message;
    
    MessageErrorCode(int code, String message) {
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
    public static MessageErrorCode fromCode(int code) {
        for (MessageErrorCode errorCode : values()) {
            if (errorCode.getCode() == code) {
                return errorCode;
            }
        }
        return MESSAGE_SEND_FAILED;
    }
} 