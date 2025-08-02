package com.exchange.message.exception;

import com.exchange.common.exception.BusinessException;
import com.exchange.message.error.MessageErrorCode;

/**
 * 消息模块业务异常类
 */
public class MessageBusinessException extends BusinessException {
    
    public MessageBusinessException(MessageErrorCode errorCode) {
        super(errorCode);
    }
    
    public MessageBusinessException(MessageErrorCode errorCode, String message) {
        super(errorCode, message);
    }
    
    public MessageBusinessException(MessageErrorCode errorCode, Object data) {
        super(errorCode, data);
    }
    
    // 消息相关异常方法
    public static MessageBusinessException messageSendFailed() {
        return new MessageBusinessException(MessageErrorCode.MESSAGE_SEND_FAILED);
    }
    
    public static MessageBusinessException messageTemplateNotFound() {
        return new MessageBusinessException(MessageErrorCode.MESSAGE_TEMPLATE_NOT_FOUND);
    }
    
    public static MessageBusinessException messagePlatformError() {
        return new MessageBusinessException(MessageErrorCode.MESSAGE_PLATFORM_ERROR);
    }
    
    public static MessageBusinessException messageRateLimit() {
        return new MessageBusinessException(MessageErrorCode.MESSAGE_RATE_LIMIT);
    }
    
    public static MessageBusinessException messageContentInvalid() {
        return new MessageBusinessException(MessageErrorCode.MESSAGE_CONTENT_INVALID);
    }
    
    public static MessageBusinessException messageRecipientInvalid() {
        return new MessageBusinessException(MessageErrorCode.MESSAGE_RECIPIENT_INVALID);
    }
    
    public static MessageBusinessException messageChannelNotSupported() {
        return new MessageBusinessException(MessageErrorCode.MESSAGE_CHANNEL_NOT_SUPPORTED);
    }
    
    public static MessageBusinessException messageTemplateParseError() {
        return new MessageBusinessException(MessageErrorCode.MESSAGE_TEMPLATE_PARSE_ERROR);
    }
    
    public static MessageBusinessException messageVariableMissing() {
        return new MessageBusinessException(MessageErrorCode.MESSAGE_VARIABLE_MISSING);
    }
    
    public static MessageBusinessException messageEncodingError() {
        return new MessageBusinessException(MessageErrorCode.MESSAGE_ENCODING_ERROR);
    }
    
    public static MessageBusinessException messageDeliveryFailed() {
        return new MessageBusinessException(MessageErrorCode.MESSAGE_DELIVERY_FAILED);
    }
    
    public static MessageBusinessException messageSystemBusy() {
        return new MessageBusinessException(MessageErrorCode.MESSAGE_SYSTEM_BUSY);
    }
    
    public static MessageBusinessException messageBatchSendFailed() {
        return new MessageBusinessException(MessageErrorCode.MESSAGE_BATCH_SEND_FAILED);
    }
    
    public static MessageBusinessException messageScheduleFailed() {
        return new MessageBusinessException(MessageErrorCode.MESSAGE_SCHEDULE_FAILED);
    }
    
    public static MessageBusinessException messageCancelFailed() {
        return new MessageBusinessException(MessageErrorCode.MESSAGE_CANCEL_FAILED);
    }
    
    public static MessageBusinessException messageRetryFailed() {
        return new MessageBusinessException(MessageErrorCode.MESSAGE_RETRY_FAILED);
    }
    
    public static MessageBusinessException messageQueueFull() {
        return new MessageBusinessException(MessageErrorCode.MESSAGE_QUEUE_FULL);
    }
    
    public static MessageBusinessException messageProcessingTimeout() {
        return new MessageBusinessException(MessageErrorCode.MESSAGE_PROCESSING_TIMEOUT);
    }
} 