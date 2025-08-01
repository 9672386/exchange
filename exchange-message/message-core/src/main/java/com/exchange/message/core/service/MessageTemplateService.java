package com.exchange.message.core.service;

import com.exchange.message.api.dto.MessageTemplate;

import java.util.List;
import java.util.Map;

/**
 * 消息模板服务接口
 */
public interface MessageTemplateService {
    
    /**
     * 创建模板
     * @param template 模板信息
     * @return 模板ID
     */
    String createTemplate(MessageTemplate template);
    
    /**
     * 获取模板
     * @param templateId 模板ID
     * @return 模板信息
     */
    MessageTemplate getTemplate(String templateId);
    
    /**
     * 获取所有模板
     * @return 模板列表
     */
    List<MessageTemplate> getAllTemplates();
    
    /**
     * 更新模板
     * @param templateId 模板ID
     * @param template 模板信息
     * @return 是否成功
     */
    boolean updateTemplate(String templateId, MessageTemplate template);
    
    /**
     * 删除模板
     * @param templateId 模板ID
     * @return 是否成功
     */
    boolean deleteTemplate(String templateId);
    
    /**
     * 处理模板
     * @param templateId 模板ID
     * @param params 参数
     * @return 处理后的内容
     */
    String processTemplate(String templateId, Map<String, Object> params);
} 