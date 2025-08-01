package com.exchange.message.api.dto;

import com.exchange.message.api.enums.MessageType;
import com.exchange.message.api.enums.PlatformType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/**
 * 平台选择结果DTO
 */
@Data
@Schema(description = "平台选择结果")
public class PlatformSelectionResult {
    
    @Schema(description = "是否成功")
    private Boolean success;
    
    @Schema(description = "错误信息")
    private String errorMessage;
    
    @Schema(description = "消息类型")
    private MessageType messageType;
    
    @Schema(description = "选中的平台")
    private PlatformType selectedPlatform;
    
    @Schema(description = "平台配置ID")
    private String platformConfigId;
    
    @Schema(description = "地区配置ID")
    private String regionConfigId;
    
    @Schema(description = "地区代码")
    private String regionCode;
    
    @Schema(description = "电话区号")
    private String areaCode;
    
    @Schema(description = "平台配置")
    private PlatformConfig platformConfig;
    
    @Schema(description = "地区配置")
    private RegionConfig regionConfig;
    
    @Schema(description = "可用平台列表")
    private List<PlatformType> availablePlatforms;
    
    @Schema(description = "排除的平台列表")
    private List<PlatformType> excludedPlatforms;
    
    @Schema(description = "选择原因")
    private String selectionReason;
    
    @Schema(description = "成本(分)")
    private Integer cost;
    
    @Schema(description = "优先级")
    private Integer priority;
    
    public static PlatformSelectionResult success(PlatformType platform, String reason) {
        PlatformSelectionResult result = new PlatformSelectionResult();
        result.setSuccess(true);
        result.setSelectedPlatform(platform);
        result.setSelectionReason(reason);
        return result;
    }
    
    public static PlatformSelectionResult failure(String errorMessage) {
        PlatformSelectionResult result = new PlatformSelectionResult();
        result.setSuccess(false);
        result.setErrorMessage(errorMessage);
        return result;
    }
} 