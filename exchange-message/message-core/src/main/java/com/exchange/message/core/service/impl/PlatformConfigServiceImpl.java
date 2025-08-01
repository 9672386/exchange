package com.exchange.message.core.service.impl;

import com.exchange.message.api.dto.PlatformConfig;
import com.exchange.message.api.dto.PlatformSelectionRequest;
import com.exchange.message.api.dto.PlatformSelectionResult;
import com.exchange.message.api.dto.RegionConfig;
import com.exchange.message.api.enums.MessageType;
import com.exchange.message.api.enums.PlatformType;
import com.exchange.message.core.service.PlatformConfigService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 平台配置服务实现
 */
@Service
public class PlatformConfigServiceImpl implements PlatformConfigService {
    
    private static final Logger log = LoggerFactory.getLogger(PlatformConfigServiceImpl.class);
    
    // 使用内存存储，实际项目中应该使用数据库
    private final Map<String, PlatformConfig> platformConfigMap = new ConcurrentHashMap<>();
    private final Map<String, RegionConfig> regionConfigMap = new ConcurrentHashMap<>();
    
    @Override
    public String createPlatformConfig(PlatformConfig config) {
        String configId = generateConfigId("PLATFORM");
        config.setConfigId(configId);
        config.setCreateTime(LocalDateTime.now());
        config.setUpdateTime(LocalDateTime.now());
        platformConfigMap.put(configId, config);
        log.info("创建平台配置: configId={}, platformType={}", configId, config.getPlatformType());
        return configId;
    }
    
    @Override
    public boolean updatePlatformConfig(String configId, PlatformConfig config) {
        if (!platformConfigMap.containsKey(configId)) {
            log.warn("平台配置不存在: configId={}", configId);
            return false;
        }
        config.setConfigId(configId);
        config.setUpdateTime(LocalDateTime.now());
        platformConfigMap.put(configId, config);
        log.info("更新平台配置: configId={}", configId);
        return true;
    }
    
    @Override
    public boolean deletePlatformConfig(String configId) {
        PlatformConfig removed = platformConfigMap.remove(configId);
        if (removed != null) {
            log.info("删除平台配置: configId={}", configId);
            return true;
        }
        log.warn("平台配置不存在，删除失败: configId={}", configId);
        return false;
    }
    
    @Override
    public PlatformConfig getPlatformConfig(String configId) {
        return platformConfigMap.get(configId);
    }
    
    @Override
    public List<PlatformConfig> getPlatformConfigsByMessageType(MessageType messageType) {
        return platformConfigMap.values().stream()
                .filter(config -> messageType.equals(config.getMessageType()))
                .filter(PlatformConfig::getEnabled)
                .sorted(Comparator.comparing(PlatformConfig::getPriority))
                .collect(Collectors.toList());
    }
    
    @Override
    public PlatformConfig getPlatformConfigByPlatformType(PlatformType platformType) {
        return platformConfigMap.values().stream()
                .filter(config -> platformType.equals(config.getPlatformType()))
                .filter(PlatformConfig::getEnabled)
                .findFirst()
                .orElse(null);
    }
    
    @Override
    public List<PlatformConfig> getAllEnabledPlatformConfigs() {
        return platformConfigMap.values().stream()
                .filter(PlatformConfig::getEnabled)
                .collect(Collectors.toList());
    }
    
    @Override
    public boolean enablePlatformConfig(String configId) {
        PlatformConfig config = platformConfigMap.get(configId);
        if (config != null) {
            config.setEnabled(true);
            config.setUpdateTime(LocalDateTime.now());
            log.info("启用平台配置: configId={}", configId);
            return true;
        }
        log.warn("平台配置不存在，启用失败: configId={}", configId);
        return false;
    }
    
    @Override
    public boolean disablePlatformConfig(String configId) {
        PlatformConfig config = platformConfigMap.get(configId);
        if (config != null) {
            config.setEnabled(false);
            config.setUpdateTime(LocalDateTime.now());
            log.info("禁用平台配置: configId={}", configId);
            return true;
        }
        log.warn("平台配置不存在，禁用失败: configId={}", configId);
        return false;
    }
    
    @Override
    public String createRegionConfig(RegionConfig config) {
        String configId = generateConfigId("REGION");
        config.setConfigId(configId);
        config.setCreateTime(LocalDateTime.now());
        config.setUpdateTime(LocalDateTime.now());
        regionConfigMap.put(configId, config);
        log.info("创建地区配置: configId={}, regionCode={}", configId, config.getRegionCode());
        return configId;
    }
    
    @Override
    public boolean updateRegionConfig(String configId, RegionConfig config) {
        if (!regionConfigMap.containsKey(configId)) {
            log.warn("地区配置不存在: configId={}", configId);
            return false;
        }
        config.setConfigId(configId);
        config.setUpdateTime(LocalDateTime.now());
        regionConfigMap.put(configId, config);
        log.info("更新地区配置: configId={}", configId);
        return true;
    }
    
    @Override
    public boolean deleteRegionConfig(String configId) {
        RegionConfig removed = regionConfigMap.remove(configId);
        if (removed != null) {
            log.info("删除地区配置: configId={}", configId);
            return true;
        }
        log.warn("地区配置不存在，删除失败: configId={}", configId);
        return false;
    }
    
    @Override
    public RegionConfig getRegionConfig(String configId) {
        return regionConfigMap.get(configId);
    }
    
    @Override
    public RegionConfig getRegionConfigByRegionCode(String regionCode, MessageType messageType) {
        return regionConfigMap.values().stream()
                .filter(config -> regionCode.equals(config.getRegionCode()))
                .filter(config -> messageType.equals(config.getMessageType()))
                .filter(RegionConfig::getEnabled)
                .findFirst()
                .orElse(null);
    }
    
    @Override
    public RegionConfig getRegionConfigByAreaCode(String areaCode, MessageType messageType) {
        return regionConfigMap.values().stream()
                .filter(config -> areaCode.equals(config.getAreaCode()))
                .filter(config -> messageType.equals(config.getMessageType()))
                .filter(RegionConfig::getEnabled)
                .findFirst()
                .orElse(null);
    }
    
    @Override
    public List<RegionConfig> getAllEnabledRegionConfigs() {
        return regionConfigMap.values().stream()
                .filter(RegionConfig::getEnabled)
                .collect(Collectors.toList());
    }
    
    @Override
    public PlatformSelectionResult selectBestPlatform(PlatformSelectionRequest request) {
        try {
            log.info("选择最佳平台: {}", request);
            
            // 1. 获取地区配置
            RegionConfig regionConfig = null;
            if (request.getAreaCode() != null) {
                regionConfig = getRegionConfigByAreaCode(request.getAreaCode(), request.getMessageType());
            } else if (request.getRegionCode() != null) {
                regionConfig = getRegionConfigByRegionCode(request.getRegionCode(), request.getMessageType());
            }
            
            // 2. 获取可用平台列表
            List<PlatformType> availablePlatforms = getAvailablePlatforms(
                request.getMessageType(), 
                request.getRegionCode(), 
                request.getAreaCode()
            );
            
            if (availablePlatforms.isEmpty()) {
                return PlatformSelectionResult.failure("没有可用的平台");
            }
            
            // 3. 选择最佳平台
            PlatformType selectedPlatform = selectBestPlatform(
                availablePlatforms, 
                regionConfig, 
                request
            );
            
            if (selectedPlatform == null) {
                return PlatformSelectionResult.failure("无法选择最佳平台");
            }
            
            // 4. 获取平台配置
            PlatformConfig platformConfig = getPlatformConfigByPlatformType(selectedPlatform);
            
            // 5. 构建结果
            PlatformSelectionResult result = PlatformSelectionResult.success(selectedPlatform, "智能选择");
            result.setMessageType(request.getMessageType());
            result.setPlatformConfig(platformConfig);
            result.setRegionConfig(regionConfig);
            result.setAvailablePlatforms(availablePlatforms);
            result.setRegionCode(request.getRegionCode());
            result.setAreaCode(request.getAreaCode());
            
            if (platformConfig != null) {
                result.setPlatformConfigId(platformConfig.getConfigId());
                result.setCost(platformConfig.getCostPerMessage());
                result.setPriority(platformConfig.getPriority());
            }
            
            if (regionConfig != null) {
                result.setRegionConfigId(regionConfig.getConfigId());
                result.setExcludedPlatforms(regionConfig.getExcludedPlatforms());
            }
            
            log.info("选择平台成功: platform={}, reason={}", selectedPlatform, result.getSelectionReason());
            return result;
            
        } catch (Exception e) {
            log.error("选择最佳平台失败", e);
            return PlatformSelectionResult.failure("选择最佳平台失败: " + e.getMessage());
        }
    }
    
    @Override
    public boolean isPlatformAvailable(PlatformType platformType, String regionCode, String areaCode) {
        // 1. 检查平台配置是否启用
        PlatformConfig platformConfig = getPlatformConfigByPlatformType(platformType);
        if (platformConfig == null || !platformConfig.getEnabled()) {
            return false;
        }
        
        // 2. 检查地区限制
        if (regionCode != null && platformConfig.getSupportedRegions() != null) {
            if (!platformConfig.getSupportedRegions().contains(regionCode)) {
                return false;
            }
        }
        
        if (regionCode != null && platformConfig.getExcludedRegions() != null) {
            if (platformConfig.getExcludedRegions().contains(regionCode)) {
                return false;
            }
        }
        
        // 3. 检查区号限制
        if (areaCode != null && platformConfig.getSupportedAreaCodes() != null) {
            if (!platformConfig.getSupportedAreaCodes().contains(areaCode)) {
                return false;
            }
        }
        
        if (areaCode != null && platformConfig.getExcludedAreaCodes() != null) {
            if (platformConfig.getExcludedAreaCodes().contains(areaCode)) {
                return false;
            }
        }
        
        return true;
    }
    
    @Override
    public List<PlatformType> getAvailablePlatforms(MessageType messageType, String regionCode, String areaCode) {
        return getPlatformConfigsByMessageType(messageType).stream()
                .filter(config -> isPlatformAvailable(config.getPlatformType(), regionCode, areaCode))
                .map(PlatformConfig::getPlatformType)
                .collect(Collectors.toList());
    }
    
    /**
     * 选择最佳平台
     */
    private PlatformType selectBestPlatform(List<PlatformType> availablePlatforms, RegionConfig regionConfig, PlatformSelectionRequest request) {
        // 1. 如果强制指定平台
        if (Boolean.TRUE.equals(request.getForcePlatform()) && request.getSpecifiedPlatform() != null) {
            PlatformType specifiedPlatform = PlatformType.valueOf(request.getSpecifiedPlatform());
            if (availablePlatforms.contains(specifiedPlatform)) {
                return specifiedPlatform;
            }
        }
        
        // 2. 如果有地区配置，优先使用主要平台
        if (regionConfig != null && regionConfig.getPrimaryPlatform() != null) {
            if (availablePlatforms.contains(regionConfig.getPrimaryPlatform())) {
                return regionConfig.getPrimaryPlatform();
            }
        }
        
        // 3. 按优先级和权重选择
        List<PlatformConfig> configs = availablePlatforms.stream()
                .map(this::getPlatformConfigByPlatformType)
                .filter(Objects::nonNull)
                .sorted(Comparator
                    .comparing(PlatformConfig::getPriority)
                    .thenComparing(PlatformConfig::getWeight, Comparator.reverseOrder())
                )
                .collect(Collectors.toList());
        
        if (!configs.isEmpty()) {
            return configs.get(0).getPlatformType();
        }
        
        // 4. 默认选择第一个可用平台
        return availablePlatforms.isEmpty() ? null : availablePlatforms.get(0);
    }
    
    private String generateConfigId(String prefix) {
        return prefix + "_" + System.currentTimeMillis() + "_" + (int)(Math.random() * 1000);
    }
} 