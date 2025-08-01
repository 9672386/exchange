package com.exchange.message.api.dto;

import com.exchange.message.api.enums.MessageType;
import com.exchange.message.api.enums.PlatformType;
import com.exchange.message.api.enums.BusinessType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.Map;

/**
 * 消息发送请求DTO
 */
@Data
@Schema(description = "消息发送请求")
public class MessageSendRequest {
    
    @Schema(description = "消息类型", required = true)
    @NotNull(message = "消息类型不能为空")
    private MessageType messageType;
    
    @Schema(description = "平台类型", required = true)
    @NotNull(message = "平台类型不能为空")
    private PlatformType platformType;
    
    @Schema(description = "业务类型", required = true)
    @NotNull(message = "业务类型不能为空")
    private BusinessType businessType;
    
    @Schema(description = "模板ID")
    private String templateId;
    
    @Schema(description = "模板参数")
    private Map<String, Object> templateParams;
    
    @Schema(description = "接收者", required = true)
    @NotBlank(message = "接收者不能为空")
    private String receiver;
    
    @Schema(description = "消息内容")
    private String content;
    
    @Schema(description = "消息标题")
    private String subject;
    
    @Schema(description = "业务标识")
    private String businessId;
    
    @Schema(description = "优先级", defaultValue = "NORMAL")
    private String priority = "NORMAL";
    
    @Schema(description = "延迟发送时间(毫秒)")
    private Long delayTime;
} 