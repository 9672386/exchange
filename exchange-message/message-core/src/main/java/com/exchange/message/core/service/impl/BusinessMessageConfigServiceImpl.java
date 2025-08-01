package com.exchange.message.core.service.impl;

import com.exchange.message.api.dto.BusinessMessageConfig;
import com.exchange.message.api.enums.BusinessType;
import com.exchange.message.api.enums.MessageType;
import com.exchange.message.api.enums.PlatformType;
import com.exchange.message.core.service.BusinessMessageConfigService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 业务消息配置服务实现
 */
@Service
public class BusinessMessageConfigServiceImpl implements BusinessMessageConfigService {
    
    private static final Logger log = LoggerFactory.getLogger(BusinessMessageConfigServiceImpl.class);
    
    // 使用内存存储，实际项目中应该使用数据库
    private final Map<String, BusinessMessageConfig> configMap = new ConcurrentHashMap<>();
    
    @Override
    public String createConfig(BusinessMessageConfig config) {
        String configId = generateConfigId();
        config.setConfigId(configId);
        config.setCreateTime(LocalDateTime.now());
        config.setUpdateTime(LocalDateTime.now());
        configMap.put(configId, config);
        log.info("创建业务消息配置: configId={}, businessType={}", configId, config.getBusinessType());
        return configId;
    }
    
    @Override
    public boolean updateConfig(String configId, BusinessMessageConfig config) {
        if (!configMap.containsKey(configId)) {
            log.warn("配置不存在: configId={}", configId);
            return false;
        }
        config.setConfigId(configId);
        config.setUpdateTime(LocalDateTime.now());
        configMap.put(configId, config);
        log.info("更新业务消息配置: configId={}", configId);
        return true;
    }
    
    @Override
    public boolean deleteConfig(String configId) {
        BusinessMessageConfig removed = configMap.remove(configId);
        if (removed != null) {
            log.info("删除业务消息配置: configId={}", configId);
            return true;
        }
        log.warn("配置不存在，删除失败: configId={}", configId);
        return false;
    }
    
    @Override
    public BusinessMessageConfig getConfig(String configId) {
        return configMap.get(configId);
    }
    
    @Override
    public List<BusinessMessageConfig> getConfigsByBusinessType(BusinessType businessType) {
        return configMap.values().stream()
                .filter(config -> businessType.equals(config.getBusinessType()))
                .collect(Collectors.toList());
    }
    
    @Override
    public BusinessMessageConfig getConfigByBusinessAndMessageType(BusinessType businessType, MessageType messageType) {
        return configMap.values().stream()
                .filter(config -> businessType.equals(config.getBusinessType()) && messageType.equals(config.getMessageType()))
                .findFirst()
                .orElse(null);
    }
    
    @Override
    public BusinessMessageConfig getConfigByBusinessMessageAndPlatform(BusinessType businessType, MessageType messageType, PlatformType platformType) {
        return configMap.values().stream()
                .filter(config -> businessType.equals(config.getBusinessType()) 
                        && messageType.equals(config.getMessageType())
                        && platformType.equals(config.getPlatformType()))
                .findFirst()
                .orElse(null);
    }
    
    @Override
    public boolean enableConfig(String configId) {
        BusinessMessageConfig config = configMap.get(configId);
        if (config != null) {
            config.setEnabled(true);
            config.setUpdateTime(LocalDateTime.now());
            log.info("启用业务消息配置: configId={}", configId);
            return true;
        }
        log.warn("配置不存在，启用失败: configId={}", configId);
        return false;
    }
    
    @Override
    public boolean disableConfig(String configId) {
        BusinessMessageConfig config = configMap.get(configId);
        if (config != null) {
            config.setEnabled(false);
            config.setUpdateTime(LocalDateTime.now());
            log.info("禁用业务消息配置: configId={}", configId);
            return true;
        }
        log.warn("配置不存在，禁用失败: configId={}", configId);
        return false;
    }
    
    @Override
    public List<BusinessMessageConfig> getAllEnabledConfigs() {
        return configMap.values().stream()
                .filter(BusinessMessageConfig::getEnabled)
                .collect(Collectors.toList());
    }
    
    private String generateConfigId() {
        return "CONFIG_" + System.currentTimeMillis() + "_" + (int)(Math.random() * 1000);
    }
} 