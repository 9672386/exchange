package com.exchange.message.api.dto;

import com.exchange.message.api.enums.BusinessType;
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
 * 业务消息配置DTO
 */
@Data
@Schema(description = "业务消息配置")
public class BusinessMessageConfig {
    
    @Schema(description = "配置ID")
    private String configId;
    
    @Schema(description = "业务类型", required = true)
    @NotNull(message = "业务类型不能为空")
    private BusinessType businessType;
    
    @Schema(description = "消息类型", required = true)
    @NotNull(message = "消息类型不能为空")
    private MessageType messageType;
    
    @Schema(description = "平台类型", required = true)
    @NotNull(message = "平台类型不能为空")
    private PlatformType platformType;
    
    @Schema(description = "模板ID", required = true)
    @NotBlank(message = "模板ID不能为空")
    private String templateId;
    
    @Schema(description = "是否启用")
    private Boolean enabled = true;
    
    @Schema(description = "优先级", defaultValue = "NORMAL")
    private String priority = "NORMAL";
    
    @Schema(description = "重试次数", defaultValue = "3")
    private Integer retryCount = 3;
    
    @Schema(description = "重试间隔(秒)", defaultValue = "60")
    private Integer retryInterval = 60;
    
    @Schema(description = "超时时间(秒)", defaultValue = "30")
    private Integer timeout = 30;
    
    @Schema(description = "条件表达式")
    private String conditionExpression;
    
    @Schema(description = "扩展参数")
    private Map<String, Object> extraParams;
    
    @Schema(description = "创建时间")
    private LocalDateTime createTime;
    
    @Schema(description = "更新时间")
    private LocalDateTime updateTime;
} 