package com.exchange.message.web.controller;

import com.exchange.message.api.dto.PlatformConfig;
import com.exchange.message.api.dto.PlatformSelectionRequest;
import com.exchange.message.api.dto.PlatformSelectionResult;
import com.exchange.message.api.dto.RegionConfig;
import com.exchange.message.api.enums.MessageType;
import com.exchange.message.core.service.PlatformConfigService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 平台配置管理控制器
 */
@RestController
@RequestMapping("/api/platform-config")
@Tag(name = "平台配置管理", description = "平台配置和地区配置管理相关接口")
public class PlatformConfigController {
    
    @Autowired
    private PlatformConfigService platformConfigService;
    
    // ==================== 平台配置管理 ====================
    
    @PostMapping("/platform")
    @Operation(summary = "创建平台配置", description = "创建新的平台配置")
    public String createPlatformConfig(@RequestBody PlatformConfig config) {
        return platformConfigService.createPlatformConfig(config);
    }
    
    @GetMapping("/platform/{configId}")
    @Operation(summary = "获取平台配置", description = "根据配置ID获取平台配置")
    public PlatformConfig getPlatformConfig(@PathVariable String configId) {
        return platformConfigService.getPlatformConfig(configId);
    }
    
    @GetMapping("/platform/message-type/{messageType}")
    @Operation(summary = "获取消息类型平台配置", description = "根据消息类型获取平台配置列表")
    public List<PlatformConfig> getPlatformConfigsByMessageType(@PathVariable MessageType messageType) {
        return platformConfigService.getPlatformConfigsByMessageType(messageType);
    }
    
    @GetMapping("/platform/enabled")
    @Operation(summary = "获取所有启用的平台配置", description = "获取所有启用的平台配置")
    public List<PlatformConfig> getAllEnabledPlatformConfigs() {
        return platformConfigService.getAllEnabledPlatformConfigs();
    }
    
    @PutMapping("/platform/{configId}")
    @Operation(summary = "更新平台配置", description = "更新指定平台配置")
    public boolean updatePlatformConfig(@PathVariable String configId, @RequestBody PlatformConfig config) {
        return platformConfigService.updatePlatformConfig(configId, config);
    }
    
    @DeleteMapping("/platform/{configId}")
    @Operation(summary = "删除平台配置", description = "删除指定平台配置")
    public boolean deletePlatformConfig(@PathVariable String configId) {
        return platformConfigService.deletePlatformConfig(configId);
    }
    
    @PostMapping("/platform/{configId}/enable")
    @Operation(summary = "启用平台配置", description = "启用指定平台配置")
    public boolean enablePlatformConfig(@PathVariable String configId) {
        return platformConfigService.enablePlatformConfig(configId);
    }
    
    @PostMapping("/platform/{configId}/disable")
    @Operation(summary = "禁用平台配置", description = "禁用指定平台配置")
    public boolean disablePlatformConfig(@PathVariable String configId) {
        return platformConfigService.disablePlatformConfig(configId);
    }
    
    // ==================== 地区配置管理 ====================
    
    @PostMapping("/region")
    @Operation(summary = "创建地区配置", description = "创建新的地区配置")
    public String createRegionConfig(@RequestBody RegionConfig config) {
        return platformConfigService.createRegionConfig(config);
    }
    
    @GetMapping("/region/{configId}")
    @Operation(summary = "获取地区配置", description = "根据配置ID获取地区配置")
    public RegionConfig getRegionConfig(@PathVariable String configId) {
        return platformConfigService.getRegionConfig(configId);
    }
    
    @GetMapping("/region/area-code/{areaCode}")
    @Operation(summary = "根据区号获取地区配置", description = "根据电话区号获取地区配置")
    public RegionConfig getRegionConfigByAreaCode(@PathVariable String areaCode, @RequestParam MessageType messageType) {
        return platformConfigService.getRegionConfigByAreaCode(areaCode, messageType);
    }
    
    @GetMapping("/region/enabled")
    @Operation(summary = "获取所有启用的地区配置", description = "获取所有启用的地区配置")
    public List<RegionConfig> getAllEnabledRegionConfigs() {
        return platformConfigService.getAllEnabledRegionConfigs();
    }
    
    @PutMapping("/region/{configId}")
    @Operation(summary = "更新地区配置", description = "更新指定地区配置")
    public boolean updateRegionConfig(@PathVariable String configId, @RequestBody RegionConfig config) {
        return platformConfigService.updateRegionConfig(configId, config);
    }
    
    @DeleteMapping("/region/{configId}")
    @Operation(summary = "删除地区配置", description = "删除指定地区配置")
    public boolean deleteRegionConfig(@PathVariable String configId) {
        return platformConfigService.deleteRegionConfig(configId);
    }
    
    // ==================== 平台选择 ====================
    
    @PostMapping("/select")
    @Operation(summary = "选择最佳平台", description = "根据接收者和消息类型选择最佳平台")
    public PlatformSelectionResult selectBestPlatform(@RequestBody PlatformSelectionRequest request) {
        return platformConfigService.selectBestPlatform(request);
    }
    
    @GetMapping("/available")
    @Operation(summary = "获取可用平台", description = "根据消息类型和地区获取可用平台列表")
    public List<String> getAvailablePlatforms(
            @RequestParam MessageType messageType,
            @RequestParam(required = false) String regionCode,
            @RequestParam(required = false) String areaCode) {
        return platformConfigService.getAvailablePlatforms(messageType, regionCode, areaCode)
                .stream()
                .map(Enum::name)
                .toList();
    }
    
    @GetMapping("/check")
    @Operation(summary = "检查平台可用性", description = "检查指定平台在指定地区是否可用")
    public boolean isPlatformAvailable(
            @RequestParam String platformType,
            @RequestParam(required = false) String regionCode,
            @RequestParam(required = false) String areaCode) {
        return platformConfigService.isPlatformAvailable(
            com.exchange.message.api.enums.PlatformType.valueOf(platformType),
            regionCode,
            areaCode
        );
    }
} 