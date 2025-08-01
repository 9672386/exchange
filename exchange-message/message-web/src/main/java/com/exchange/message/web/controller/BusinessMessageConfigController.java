package com.exchange.message.web.controller;

import com.exchange.message.api.dto.BusinessMessageConfig;
import com.exchange.message.api.enums.BusinessType;
import com.exchange.message.core.service.BusinessMessageConfigService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 业务消息配置控制器
 */
@RestController
@RequestMapping("/api/business-config")
@Tag(name = "业务消息配置", description = "业务消息配置管理相关接口")
public class BusinessMessageConfigController {
    
    @Autowired
    private BusinessMessageConfigService businessMessageConfigService;
    
    @PostMapping
    @Operation(summary = "创建业务消息配置", description = "创建新的业务消息配置")
    public String createConfig(@RequestBody BusinessMessageConfig config) {
        return businessMessageConfigService.createConfig(config);
    }
    
    @GetMapping("/{configId}")
    @Operation(summary = "获取业务消息配置", description = "根据配置ID获取配置信息")
    public BusinessMessageConfig getConfig(@PathVariable String configId) {
        return businessMessageConfigService.getConfig(configId);
    }
    
    @GetMapping("/business/{businessType}")
    @Operation(summary = "获取业务类型配置列表", description = "根据业务类型获取配置列表")
    public List<BusinessMessageConfig> getConfigsByBusinessType(@PathVariable BusinessType businessType) {
        return businessMessageConfigService.getConfigsByBusinessType(businessType);
    }
    
    @GetMapping("/enabled")
    @Operation(summary = "获取所有启用的配置", description = "获取所有启用的业务消息配置")
    public List<BusinessMessageConfig> getAllEnabledConfigs() {
        return businessMessageConfigService.getAllEnabledConfigs();
    }
    
    @PutMapping("/{configId}")
    @Operation(summary = "更新业务消息配置", description = "更新指定配置")
    public boolean updateConfig(@PathVariable String configId, @RequestBody BusinessMessageConfig config) {
        return businessMessageConfigService.updateConfig(configId, config);
    }
    
    @DeleteMapping("/{configId}")
    @Operation(summary = "删除业务消息配置", description = "删除指定配置")
    public boolean deleteConfig(@PathVariable String configId) {
        return businessMessageConfigService.deleteConfig(configId);
    }
    
    @PostMapping("/{configId}/enable")
    @Operation(summary = "启用配置", description = "启用指定配置")
    public boolean enableConfig(@PathVariable String configId) {
        return businessMessageConfigService.enableConfig(configId);
    }
    
    @PostMapping("/{configId}/disable")
    @Operation(summary = "禁用配置", description = "禁用指定配置")
    public boolean disableConfig(@PathVariable String configId) {
        return businessMessageConfigService.disableConfig(configId);
    }
} 