package com.exchange.message.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 消息发送响应DTO
 */
@Data
@Schema(description = "消息发送响应")
public class MessageSendResponse {
    
    @Schema(description = "是否成功")
    private Boolean success;
    
    @Schema(description = "消息ID")
    private String messageId;
    
    @Schema(description = "错误码")
    private String errorCode;
    
    @Schema(description = "错误信息")
    private String errorMessage;
    
    @Schema(description = "发送时间")
    private Long sendTime;
    
    public static MessageSendResponse success(String messageId) {
        MessageSendResponse response = new MessageSendResponse();
        response.setSuccess(true);
        response.setMessageId(messageId);
        response.setSendTime(System.currentTimeMillis());
        return response;
    }
    
    public static MessageSendResponse failure(String errorCode, String errorMessage) {
        MessageSendResponse response = new MessageSendResponse();
        response.setSuccess(false);
        response.setErrorCode(errorCode);
        response.setErrorMessage(errorMessage);
        response.setSendTime(System.currentTimeMillis());
        return response;
    }
} 