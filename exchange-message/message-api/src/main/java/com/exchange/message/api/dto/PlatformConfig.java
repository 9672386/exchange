package com.exchange.message.api.dto;

import com.exchange.message.api.enums.MessageType;
import com.exchange.message.api.enums.PlatformType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 平台配置DTO
 */
@Data
@Schema(description = "平台配置")
public class PlatformConfig {
    
    @Schema(description = "配置ID")
    private String configId;
    
    @Schema(description = "消息类型", required = true)
    @NotNull(message = "消息类型不能为空")
    private MessageType messageType;
    
    @Schema(description = "平台类型", required = true)
    @NotNull(message = "平台类型不能为空")
    private PlatformType platformType;
    
    @Schema(description = "平台名称", required = true)
    @NotBlank(message = "平台名称不能为空")
    private String platformName;
    
    @Schema(description = "平台描述")
    private String description;
    
    @Schema(description = "是否启用", defaultValue = "true")
    private Boolean enabled = true;
    
    @Schema(description = "优先级", defaultValue = "1")
    private Integer priority = 1;
    
    @Schema(description = "权重", defaultValue = "100")
    private Integer weight = 100;
    
    @Schema(description = "平台配置参数")
    private Map<String, Object> platformParams;
    
    @Schema(description = "支持的国家/地区列表")
    private List<String> supportedRegions;
    
    @Schema(description = "排除的国家/地区列表")
    private List<String> excludedRegions;
    
    @Schema(description = "支持的电话区号列表")
    private List<String> supportedAreaCodes;
    
    @Schema(description = "排除的电话区号列表")
    private List<String> excludedAreaCodes;
    
    @Schema(description = "每日发送限制", defaultValue = "10000")
    private Integer dailyLimit = 10000;
    
    @Schema(description = "每分钟发送限制", defaultValue = "100")
    private Integer rateLimit = 100;
    
    @Schema(description = "重试次数", defaultValue = "3")
    private Integer retryCount = 3;
    
    @Schema(description = "重试间隔(秒)", defaultValue = "60")
    private Integer retryInterval = 60;
    
    @Schema(description = "超时时间(秒)", defaultValue = "30")
    private Integer timeout = 30;
    
    @Schema(description = "成本(分/条)", defaultValue = "0")
    private Integer costPerMessage = 0;
    
    @Schema(description = "创建时间")
    private LocalDateTime createTime;
    
    @Schema(description = "更新时间")
    private LocalDateTime updateTime;
} 