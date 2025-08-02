package com.exchange.message.controller;

import com.exchange.common.response.ApiResponse;
import com.exchange.common.response.ResponseUtils;
import com.exchange.message.api.dto.MessageTemplate;
import com.exchange.message.api.enums.BusinessType;
import com.exchange.message.api.enums.MessageType;
import com.exchange.message.api.enums.PlatformType;
import com.exchange.message.core.service.MultiLanguageTemplateService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 多语言模板控制器
 */
@RestController
@RequestMapping("/api/multi-language-template")
@Tag(name = "多语言模板", description = "多语言模板相关接口")
public class MultiLanguageTemplateController {
    
    @Autowired
    private MultiLanguageTemplateService multiLanguageTemplateService;
    
    @GetMapping("/template/{templateId}")
    @Operation(summary = "获取多语言模板", description = "根据模板ID和语言获取模板")
    public ApiResponse<MessageTemplate> getTemplate(
            @PathVariable String templateId,
            @Parameter(description = "语言代码", example = "zh_CN")
            @RequestParam(defaultValue = "zh_CN") String languageCode) {
        try {
            MessageTemplate template = multiLanguageTemplateService.getTemplate(templateId, languageCode);
            return ResponseUtils.success("获取模板成功", template);
        } catch (Exception e) {
            return ResponseUtils.error(8001, "获取模板失败: " + e.getMessage());
        }
    }
    
    @GetMapping("/template/business")
    @Operation(summary = "根据业务类型获取模板", description = "根据业务类型、消息类型、平台类型和语言获取模板")
    public ApiResponse<MessageTemplate> getTemplateByBusiness(
            @Parameter(description = "业务类型")
            @RequestParam BusinessType businessType,
            @Parameter(description = "消息类型")
            @RequestParam MessageType messageType,
            @Parameter(description = "平台类型")
            @RequestParam PlatformType platformType,
            @Parameter(description = "语言代码", example = "zh_CN")
            @RequestParam(defaultValue = "zh_CN") String languageCode) {
        try {
            MessageTemplate template = multiLanguageTemplateService.getTemplate(businessType, messageType, platformType, languageCode);
            return ResponseUtils.success("获取模板成功", template);
        } catch (Exception e) {
            return ResponseUtils.error(8001, "获取模板失败: " + e.getMessage());
        }
    }
    
    @GetMapping("/template/{templateId}/content")
    @Operation(summary = "获取模板内容", description = "根据模板ID和语言获取模板内容")
    public ApiResponse<String> getTemplateContent(
            @PathVariable String templateId,
            @Parameter(description = "语言代码", example = "zh_CN")
            @RequestParam(defaultValue = "zh_CN") String languageCode) {
        try {
            String content = multiLanguageTemplateService.getTemplateContent(templateId, languageCode);
            return ResponseUtils.success("获取模板内容成功", content);
        } catch (Exception e) {
            return ResponseUtils.error(8001, "获取模板内容失败: " + e.getMessage());
        }
    }
    
    @GetMapping("/template/{templateId}/subject")
    @Operation(summary = "获取模板标题", description = "根据模板ID和语言获取模板标题")
    public ApiResponse<String> getTemplateSubject(
            @PathVariable String templateId,
            @Parameter(description = "语言代码", example = "zh_CN")
            @RequestParam(defaultValue = "zh_CN") String languageCode) {
        try {
            String subject = multiLanguageTemplateService.getTemplateSubject(templateId, languageCode);
            return ResponseUtils.success("获取模板标题成功", subject);
        } catch (Exception e) {
            return ResponseUtils.error(8001, "获取模板标题失败: " + e.getMessage());
        }
    }
    
    @PostMapping("/template/{templateId}/render")
    @Operation(summary = "渲染模板内容", description = "根据模板ID、语言和参数渲染模板内容")
    public ApiResponse<String> renderTemplate(
            @PathVariable String templateId,
            @Parameter(description = "语言代码", example = "zh_CN")
            @RequestParam(defaultValue = "zh_CN") String languageCode,
            @RequestBody Map<String, Object> params) {
        try {
            String renderedContent = multiLanguageTemplateService.renderTemplate(templateId, languageCode, params);
            return ResponseUtils.success("渲染模板成功", renderedContent);
        } catch (Exception e) {
            return ResponseUtils.error(8007, "渲染模板失败: " + e.getMessage());
        }
    }
    
    @PostMapping("/template/{templateId}/render-subject")
    @Operation(summary = "渲染模板标题", description = "根据模板ID、语言和参数渲染模板标题")
    public ApiResponse<String> renderTemplateSubject(
            @PathVariable String templateId,
            @Parameter(description = "语言代码", example = "zh_CN")
            @RequestParam(defaultValue = "zh_CN") String languageCode,
            @RequestBody Map<String, Object> params) {
        try {
            String renderedSubject = multiLanguageTemplateService.renderTemplateSubject(templateId, languageCode, params);
            return ResponseUtils.success("渲染模板标题成功", renderedSubject);
        } catch (Exception e) {
            return ResponseUtils.error(8007, "渲染模板标题失败: " + e.getMessage());
        }
    }
    
    @GetMapping("/template/{templateId}/languages")
    @Operation(summary = "获取支持的语言列表", description = "获取模板支持的语言列表")
    public ApiResponse<List<String>> getSupportedLanguages(@PathVariable String templateId) {
        try {
            List<String> languages = multiLanguageTemplateService.getSupportedLanguages(templateId);
            return ResponseUtils.success("获取支持的语言列表成功", languages);
        } catch (Exception e) {
            return ResponseUtils.error(8001, "获取支持的语言列表失败: " + e.getMessage());
        }
    }
    
    @GetMapping("/template/{templateId}/language/{languageCode}/supported")
    @Operation(summary = "检查语言支持", description = "检查模板是否支持指定语言")
    public ApiResponse<Boolean> isLanguageSupported(
            @PathVariable String templateId,
            @PathVariable String languageCode) {
        try {
            boolean supported = multiLanguageTemplateService.isLanguageSupported(templateId, languageCode);
            return ResponseUtils.success("检查语言支持成功", supported);
        } catch (Exception e) {
            return ResponseUtils.error(8001, "检查语言支持失败: " + e.getMessage());
        }
    }
    
    @GetMapping("/default-language")
    @Operation(summary = "获取默认语言", description = "获取系统默认语言")
    public ApiResponse<String> getDefaultLanguage() {
        try {
            String defaultLanguage = multiLanguageTemplateService.getDefaultLanguage();
            return ResponseUtils.success("获取默认语言成功", defaultLanguage);
        } catch (Exception e) {
            return ResponseUtils.error(8001, "获取默认语言失败: " + e.getMessage());
        }
    }
    
    @PostMapping("/default-language")
    @Operation(summary = "设置默认语言", description = "设置系统默认语言")
    public ApiResponse<Void> setDefaultLanguage(
            @Parameter(description = "语言代码", example = "zh_CN")
            @RequestParam String languageCode) {
        try {
            multiLanguageTemplateService.setDefaultLanguage(languageCode);
            return ResponseUtils.success();
        } catch (Exception e) {
            return ResponseUtils.error(8001, "设置默认语言失败: " + e.getMessage());
        }
    }
} 