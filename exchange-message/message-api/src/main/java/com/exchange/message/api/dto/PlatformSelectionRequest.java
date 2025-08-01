package com.exchange.message.api.dto;

import com.exchange.message.api.enums.MessageType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 平台选择请求DTO
 */
@Data
@Schema(description = "平台选择请求")
public class PlatformSelectionRequest {
    
    @Schema(description = "消息类型", required = true)
    @NotNull(message = "消息类型不能为空")
    private MessageType messageType;
    
    @Schema(description = "接收者", required = true)
    @NotBlank(message = "接收者不能为空")
    private String receiver;
    
    @Schema(description = "地区代码")
    private String regionCode;
    
    @Schema(description = "电话区号")
    private String areaCode;
    
    @Schema(description = "国家代码")
    private String countryCode;
    
    @Schema(description = "业务类型")
    private String businessType;
    
    @Schema(description = "优先级")
    private Integer priority;
    
    @Schema(description = "成本限制(分)")
    private Integer costLimit;
    
    @Schema(description = "是否强制使用指定平台")
    private Boolean forcePlatform = false;
    
    @Schema(description = "指定平台类型")
    private String specifiedPlatform;
} 