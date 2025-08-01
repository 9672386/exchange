package com.exchange.message.controller;

import com.exchange.message.api.dto.MessageTemplate;
import com.exchange.message.core.service.MessageTemplateService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 消息模板控制器
 */
@RestController
@RequestMapping("/api/template")
@Tag(name = "消息模板", description = "消息模板管理相关接口")
public class TemplateController {
    
    @Autowired
    private MessageTemplateService messageTemplateService;
    
    @PostMapping
    @Operation(summary = "创建模板", description = "创建新的消息模板")
    public String createTemplate(@RequestBody MessageTemplate template) {
        return messageTemplateService.createTemplate(template);
    }
    
    @GetMapping("/{templateId}")
    @Operation(summary = "获取模板", description = "根据模板ID获取模板信息")
    public MessageTemplate getTemplate(@PathVariable String templateId) {
        return messageTemplateService.getTemplate(templateId);
    }
    
    @GetMapping("/list")
    @Operation(summary = "获取模板列表", description = "获取所有模板列表")
    public List<MessageTemplate> getTemplateList() {
        return messageTemplateService.getAllTemplates();
    }
    
    @PutMapping("/{templateId}")
    @Operation(summary = "更新模板", description = "更新指定模板")
    public boolean updateTemplate(@PathVariable String templateId, @RequestBody MessageTemplate template) {
        return messageTemplateService.updateTemplate(templateId, template);
    }
    
    @DeleteMapping("/{templateId}")
    @Operation(summary = "删除模板", description = "删除指定模板")
    public boolean deleteTemplate(@PathVariable String templateId) {
        return messageTemplateService.deleteTemplate(templateId);
    }
} 