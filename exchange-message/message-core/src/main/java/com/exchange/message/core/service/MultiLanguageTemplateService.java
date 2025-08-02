package com.exchange.message.core.service;

import com.exchange.message.api.dto.MessageTemplate;
import com.exchange.message.api.enums.BusinessType;
import com.exchange.message.api.enums.MessageType;
import com.exchange.message.api.enums.PlatformType;

import java.util.List;
import java.util.Map;

/**
 * 多语言模板服务接口
 */
public interface MultiLanguageTemplateService {
    
    /**
     * 根据模板ID和语言获取模板
     * @param templateId 模板ID
     * @param languageCode 语言代码
     * @return 消息模板
     */
    MessageTemplate getTemplate(String templateId, String languageCode);
    
    /**
     * 根据业务类型和语言获取模板
     * @param businessType 业务类型
     * @param messageType 消息类型
     * @param platformType 平台类型
     * @param languageCode 语言代码
     * @return 消息模板
     */
    MessageTemplate getTemplate(BusinessType businessType, MessageType messageType, PlatformType platformType, String languageCode);
    
    /**
     * 获取模板的多语言内容
     * @param templateId 模板ID
     * @param languageCode 语言代码
     * @return 模板内容
     */
    String getTemplateContent(String templateId, String languageCode);
    
    /**
     * 获取模板的多语言标题
     * @param templateId 模板ID
     * @param languageCode 语言代码
     * @return 模板标题
     */
    String getTemplateSubject(String templateId, String languageCode);
    
    /**
     * 渲染模板内容
     * @param templateId 模板ID
     * @param languageCode 语言代码
     * @param params 参数
     * @return 渲染后的内容
     */
    String renderTemplate(String templateId, String languageCode, Map<String, Object> params);
    
    /**
     * 渲染模板标题
     * @param templateId 模板ID
     * @param languageCode 语言代码
     * @param params 参数
     * @return 渲染后的标题
     */
    String renderTemplateSubject(String templateId, String languageCode, Map<String, Object> params);
    
    /**
     * 获取支持的语言列表
     * @param templateId 模板ID
     * @return 支持的语言代码列表
     */
    List<String> getSupportedLanguages(String templateId);
    
    /**
     * 检查模板是否支持指定语言
     * @param templateId 模板ID
     * @param languageCode 语言代码
     * @return 是否支持
     */
    boolean isLanguageSupported(String templateId, String languageCode);
    
    /**
     * 获取默认语言
     * @return 默认语言代码
     */
    String getDefaultLanguage();
    
    /**
     * 设置默认语言
     * @param languageCode 语言代码
     */
    void setDefaultLanguage(String languageCode);
} 