package com.exchange.message.core.service;

import com.exchange.message.api.dto.BusinessMessageConfig;
import com.exchange.message.api.enums.BusinessType;
import com.exchange.message.api.enums.MessageType;
import com.exchange.message.api.enums.PlatformType;

import java.util.List;

/**
 * 业务消息配置服务接口
 */
public interface BusinessMessageConfigService {
    
    /**
     * 创建业务消息配置
     * @param config 配置信息
     * @return 配置ID
     */
    String createConfig(BusinessMessageConfig config);
    
    /**
     * 更新业务消息配置
     * @param configId 配置ID
     * @param config 配置信息
     * @return 是否成功
     */
    boolean updateConfig(String configId, BusinessMessageConfig config);
    
    /**
     * 删除业务消息配置
     * @param configId 配置ID
     * @return 是否成功
     */
    boolean deleteConfig(String configId);
    
    /**
     * 获取业务消息配置
     * @param configId 配置ID
     * @return 配置信息
     */
    BusinessMessageConfig getConfig(String configId);
    
    /**
     * 根据业务类型获取配置列表
     * @param businessType 业务类型
     * @return 配置列表
     */
    List<BusinessMessageConfig> getConfigsByBusinessType(BusinessType businessType);
    
    /**
     * 根据业务类型和消息类型获取配置
     * @param businessType 业务类型
     * @param messageType 消息类型
     * @return 配置信息
     */
    BusinessMessageConfig getConfigByBusinessAndMessageType(BusinessType businessType, MessageType messageType);
    
    /**
     * 根据业务类型、消息类型和平台类型获取配置
     * @param businessType 业务类型
     * @param messageType 消息类型
     * @param platformType 平台类型
     * @return 配置信息
     */
    BusinessMessageConfig getConfigByBusinessMessageAndPlatform(BusinessType businessType, MessageType messageType, PlatformType platformType);
    
    /**
     * 启用配置
     * @param configId 配置ID
     * @return 是否成功
     */
    boolean enableConfig(String configId);
    
    /**
     * 禁用配置
     * @param configId 配置ID
     * @return 是否成功
     */
    boolean disableConfig(String configId);
    
    /**
     * 获取所有启用的配置
     * @return 配置列表
     */
    List<BusinessMessageConfig> getAllEnabledConfigs();
} 