package com.exchange.message.api.dto;

import com.exchange.message.api.enums.MessageType;
import com.exchange.message.api.enums.PlatformType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;

/**
 * 消息模板DTO
 */
@Data
@Schema(description = "消息模板")
public class MessageTemplate {
    
    @Schema(description = "模板ID")
    private String templateId;
    
    @Schema(description = "模板名称", required = true)
    @NotBlank(message = "模板名称不能为空")
    private String templateName;
    
    @Schema(description = "消息类型", required = true)
    @NotNull(message = "消息类型不能为空")
    private MessageType messageType;
    
    @Schema(description = "平台类型", required = true)
    @NotNull(message = "平台类型不能为空")
    private PlatformType platformType;
    
    @Schema(description = "模板内容", required = true)
    @NotBlank(message = "模板内容不能为空")
    private String templateContent;
    
    @Schema(description = "模板标题")
    private String templateSubject;
    
    @Schema(description = "模板参数示例")
    private String parameterExample;
    
    @Schema(description = "是否启用")
    private Boolean enabled = true;
    
    @Schema(description = "创建时间")
    private LocalDateTime createTime;
    
    @Schema(description = "更新时间")
    private LocalDateTime updateTime;
} 