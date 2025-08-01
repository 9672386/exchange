package com.exchange.message.api.dto;

import com.exchange.message.api.enums.MessageType;
import com.exchange.message.api.enums.PlatformType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 地区配置DTO
 */
@Data
@Schema(description = "地区配置")
public class RegionConfig {
    
    @Schema(description = "配置ID")
    private String configId;
    
    @Schema(description = "地区代码", required = true)
    @NotBlank(message = "地区代码不能为空")
    private String regionCode;
    
    @Schema(description = "地区名称", required = true)
    @NotBlank(message = "地区名称不能为空")
    private String regionName;
    
    @Schema(description = "消息类型", required = true)
    @NotNull(message = "消息类型不能为空")
    private MessageType messageType;
    
    @Schema(description = "主要平台", required = true)
    @NotNull(message = "主要平台不能为空")
    private PlatformType primaryPlatform;
    
    @Schema(description = "备用平台列表")
    private List<PlatformType> backupPlatforms;
    
    @Schema(description = "排除的平台列表")
    private List<PlatformType> excludedPlatforms;
    
    @Schema(description = "电话区号", required = true)
    @NotBlank(message = "电话区号不能为空")
    private String areaCode;
    
    @Schema(description = "是否启用", defaultValue = "true")
    private Boolean enabled = true;
    
    @Schema(description = "优先级", defaultValue = "1")
    private Integer priority = 1;
    
    @Schema(description = "每日发送限制", defaultValue = "10000")
    private Integer dailyLimit = 10000;
    
    @Schema(description = "每分钟发送限制", defaultValue = "100")
    private Integer rateLimit = 100;
    
    @Schema(description = "成本限制(分)", defaultValue = "100000")
    private Integer costLimit = 100000;
    
    @Schema(description = "创建时间")
    private LocalDateTime createTime;
    
    @Schema(description = "更新时间")
    private LocalDateTime updateTime;
} 