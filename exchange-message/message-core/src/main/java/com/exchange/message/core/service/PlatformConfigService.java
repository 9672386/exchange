package com.exchange.message.core.service;

import com.exchange.message.api.dto.PlatformConfig;
import com.exchange.message.api.dto.PlatformSelectionRequest;
import com.exchange.message.api.dto.PlatformSelectionResult;
import com.exchange.message.api.dto.RegionConfig;
import com.exchange.message.api.enums.MessageType;
import com.exchange.message.api.enums.PlatformType;

import java.util.List;

/**
 * 平台配置服务接口
 */
public interface PlatformConfigService {
    
    /**
     * 创建平台配置
     * @param config 平台配置
     * @return 配置ID
     */
    String createPlatformConfig(PlatformConfig config);
    
    /**
     * 更新平台配置
     * @param configId 配置ID
     * @param config 平台配置
     * @return 是否成功
     */
    boolean updatePlatformConfig(String configId, PlatformConfig config);
    
    /**
     * 删除平台配置
     * @param configId 配置ID
     * @return 是否成功
     */
    boolean deletePlatformConfig(String configId);
    
    /**
     * 获取平台配置
     * @param configId 配置ID
     * @return 平台配置
     */
    PlatformConfig getPlatformConfig(String configId);
    
    /**
     * 根据消息类型获取平台配置列表
     * @param messageType 消息类型
     * @return 平台配置列表
     */
    List<PlatformConfig> getPlatformConfigsByMessageType(MessageType messageType);
    
    /**
     * 根据平台类型获取平台配置
     * @param platformType 平台类型
     * @return 平台配置
     */
    PlatformConfig getPlatformConfigByPlatformType(PlatformType platformType);
    
    /**
     * 获取所有启用的平台配置
     * @return 平台配置列表
     */
    List<PlatformConfig> getAllEnabledPlatformConfigs();
    
    /**
     * 启用平台配置
     * @param configId 配置ID
     * @return 是否成功
     */
    boolean enablePlatformConfig(String configId);
    
    /**
     * 禁用平台配置
     * @param configId 配置ID
     * @return 是否成功
     */
    boolean disablePlatformConfig(String configId);
    
    /**
     * 创建地区配置
     * @param config 地区配置
     * @return 配置ID
     */
    String createRegionConfig(RegionConfig config);
    
    /**
     * 更新地区配置
     * @param configId 配置ID
     * @param config 地区配置
     * @return 是否成功
     */
    boolean updateRegionConfig(String configId, RegionConfig config);
    
    /**
     * 删除地区配置
     * @param configId 配置ID
     * @return 是否成功
     */
    boolean deleteRegionConfig(String configId);
    
    /**
     * 获取地区配置
     * @param configId 配置ID
     * @return 地区配置
     */
    RegionConfig getRegionConfig(String configId);
    
    /**
     * 根据地区代码获取地区配置
     * @param regionCode 地区代码
     * @param messageType 消息类型
     * @return 地区配置
     */
    RegionConfig getRegionConfigByRegionCode(String regionCode, MessageType messageType);
    
    /**
     * 根据电话区号获取地区配置
     * @param areaCode 电话区号
     * @param messageType 消息类型
     * @return 地区配置
     */
    RegionConfig getRegionConfigByAreaCode(String areaCode, MessageType messageType);
    
    /**
     * 获取所有启用的地区配置
     * @return 地区配置列表
     */
    List<RegionConfig> getAllEnabledRegionConfigs();
    
    /**
     * 选择最佳平台
     * @param request 平台选择请求
     * @return 平台选择结果
     */
    PlatformSelectionResult selectBestPlatform(PlatformSelectionRequest request);
    
    /**
     * 检查平台是否可用
     * @param platformType 平台类型
     * @param regionCode 地区代码
     * @param areaCode 电话区号
     * @return 是否可用
     */
    boolean isPlatformAvailable(PlatformType platformType, String regionCode, String areaCode);
    
    /**
     * 获取可用平台列表
     * @param messageType 消息类型
     * @param regionCode 地区代码
     * @param areaCode 电话区号
     * @return 可用平台列表
     */
    List<PlatformType> getAvailablePlatforms(MessageType messageType, String regionCode, String areaCode);
} 