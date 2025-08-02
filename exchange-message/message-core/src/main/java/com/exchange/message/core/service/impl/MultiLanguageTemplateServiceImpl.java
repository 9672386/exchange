package com.exchange.message.core.service.impl;

import com.exchange.message.api.dto.MessageTemplate;
import com.exchange.message.api.enums.BusinessType;
import com.exchange.message.api.enums.MessageType;
import com.exchange.message.api.enums.PlatformType;
import com.exchange.message.core.service.MultiLanguageTemplateService;
import com.exchange.message.core.service.MessageTemplateService;
import com.exchange.message.exception.MessageBusinessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 多语言模板服务实现
 */
@Service
public class MultiLanguageTemplateServiceImpl implements MultiLanguageTemplateService {
    
    private static final Logger log = LoggerFactory.getLogger(MultiLanguageTemplateServiceImpl.class);
    
    @Autowired
    private MessageTemplateService messageTemplateService;
    
    // 默认语言
    private String defaultLanguage = "zh_CN";
    
    // 支持的语言列表
    private static final List<String> SUPPORTED_LANGUAGES = Arrays.asList(
        "zh_CN", "zh_TW", "en_US", "en_GB", "ja_JP", "ko_KR", "fr_FR", "de_DE", "es_ES", "pt_BR"
    );
    
    // 模板参数正则表达式
    private static final Pattern PARAM_PATTERN = Pattern.compile("\\$\\{([^}]+)\\}");
    
    @Override
    public MessageTemplate getTemplate(String templateId, String languageCode) {
        try {
            // 获取基础模板
            MessageTemplate template = messageTemplateService.getTemplate(templateId);
            if (template == null) {
                throw MessageBusinessException.messageTemplateNotFound();
            }
            
            // 如果请求的语言与模板默认语言相同，直接返回
            if (languageCode.equals(template.getLanguageCode())) {
                return template;
            }
            
            // 检查是否支持请求的语言
            if (!isLanguageSupported(templateId, languageCode)) {
                log.warn("模板 {} 不支持语言 {}, 使用默认语言 {}", templateId, languageCode, template.getLanguageCode());
                return template;
            }
            
            // 创建多语言模板副本
            MessageTemplate multiLanguageTemplate = new MessageTemplate();
            multiLanguageTemplate.setTemplateId(template.getTemplateId());
            multiLanguageTemplate.setTemplateName(template.getTemplateName());
            multiLanguageTemplate.setMessageType(template.getMessageType());
            multiLanguageTemplate.setPlatformType(template.getPlatformType());
            multiLanguageTemplate.setBusinessType(template.getBusinessType());
            multiLanguageTemplate.setLanguageCode(languageCode);
            multiLanguageTemplate.setEnabled(template.getEnabled());
            multiLanguageTemplate.setCreateTime(template.getCreateTime());
            multiLanguageTemplate.setUpdateTime(template.getUpdateTime());
            
            // 设置多语言内容
            if (template.getMultiLanguageContent() != null) {
                String content = template.getMultiLanguageContent().get(languageCode);
                if (content != null) {
                    multiLanguageTemplate.setTemplateContent(content);
                } else {
                    multiLanguageTemplate.setTemplateContent(template.getTemplateContent());
                }
            } else {
                multiLanguageTemplate.setTemplateContent(template.getTemplateContent());
            }
            
            // 设置多语言标题
            if (template.getMultiLanguageSubject() != null) {
                String subject = template.getMultiLanguageSubject().get(languageCode);
                if (subject != null) {
                    multiLanguageTemplate.setTemplateSubject(subject);
                } else {
                    multiLanguageTemplate.setTemplateSubject(template.getTemplateSubject());
                }
            } else {
                multiLanguageTemplate.setTemplateSubject(template.getTemplateSubject());
            }
            
            return multiLanguageTemplate;
            
        } catch (Exception e) {
            log.error("获取多语言模板失败: templateId={}, languageCode={}", templateId, languageCode, e);
            throw MessageBusinessException.messageTemplateNotFound();
        }
    }
    
    @Override
    public MessageTemplate getTemplate(BusinessType businessType, MessageType messageType, PlatformType platformType, String languageCode) {
        try {
            // 构建模板ID
            String templateId = String.format("%s_%s_%s", messageType.name(), platformType.name(), businessType.name());
            return getTemplate(templateId, languageCode);
        } catch (Exception e) {
            log.error("根据业务类型获取多语言模板失败: businessType={}, messageType={}, platformType={}, languageCode={}", 
                     businessType, messageType, platformType, languageCode, e);
            throw MessageBusinessException.messageTemplateNotFound();
        }
    }
    
    @Override
    public String getTemplateContent(String templateId, String languageCode) {
        MessageTemplate template = getTemplate(templateId, languageCode);
        return template.getTemplateContent();
    }
    
    @Override
    public String getTemplateSubject(String templateId, String languageCode) {
        MessageTemplate template = getTemplate(templateId, languageCode);
        return template.getTemplateSubject();
    }
    
    @Override
    public String renderTemplate(String templateId, String languageCode, Map<String, Object> params) {
        try {
            String templateContent = getTemplateContent(templateId, languageCode);
            return renderTemplateContent(templateContent, params);
        } catch (Exception e) {
            log.error("渲染模板内容失败: templateId={}, languageCode={}", templateId, languageCode, e);
            throw MessageBusinessException.messageTemplateParseError();
        }
    }
    
    @Override
    public String renderTemplateSubject(String templateId, String languageCode, Map<String, Object> params) {
        try {
            String templateSubject = getTemplateSubject(templateId, languageCode);
            return renderTemplateContent(templateSubject, params);
        } catch (Exception e) {
            log.error("渲染模板标题失败: templateId={}, languageCode={}", templateId, languageCode, e);
            throw MessageBusinessException.messageTemplateParseError();
        }
    }
    
    @Override
    public List<String> getSupportedLanguages(String templateId) {
        try {
            MessageTemplate template = messageTemplateService.getTemplate(templateId);
            if (template == null) {
                return Collections.emptyList();
            }
            
            List<String> languages = new ArrayList<>();
            languages.add(template.getLanguageCode()); // 添加默认语言
            
            // 添加多语言支持的语言
            if (template.getMultiLanguageContent() != null) {
                languages.addAll(template.getMultiLanguageContent().keySet());
            }
            
            return languages;
        } catch (Exception e) {
            log.error("获取支持的语言列表失败: templateId={}", templateId, e);
            return Collections.emptyList();
        }
    }
    
    @Override
    public boolean isLanguageSupported(String templateId, String languageCode) {
        List<String> supportedLanguages = getSupportedLanguages(templateId);
        return supportedLanguages.contains(languageCode);
    }
    
    @Override
    public String getDefaultLanguage() {
        return defaultLanguage;
    }
    
    @Override
    public void setDefaultLanguage(String languageCode) {
        if (SUPPORTED_LANGUAGES.contains(languageCode)) {
            this.defaultLanguage = languageCode;
        } else {
            log.warn("不支持的语言代码: {}", languageCode);
        }
    }
    
    /**
     * 渲染模板内容
     * @param templateContent 模板内容
     * @param params 参数
     * @return 渲染后的内容
     */
    private String renderTemplateContent(String templateContent, Map<String, Object> params) {
        if (templateContent == null || templateContent.isEmpty()) {
            return templateContent;
        }
        
        if (params == null || params.isEmpty()) {
            return templateContent;
        }
        
        String result = templateContent;
        Matcher matcher = PARAM_PATTERN.matcher(templateContent);
        
        while (matcher.find()) {
            String paramName = matcher.group(1);
            Object paramValue = params.get(paramName);
            
            if (paramValue != null) {
                result = result.replace(matcher.group(), paramValue.toString());
            } else {
                log.warn("模板参数未找到: {}", paramName);
                result = result.replace(matcher.group(), "");
            }
        }
        
        return result;
    }
} 