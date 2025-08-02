# 错误码扩展指南

## 概述

本指南说明如何在各个模块中扩展自己的错误码，利用ErrorCode接口的灵活性。

## 错误码接口设计

### ErrorCode接口
```java
public interface ErrorCode {
    int getCode();
    String getMessage();
    default boolean isSuccess() { ... }
    default String getModuleName() { ... }
}
```

### 模块错误码段分配
- **通用错误码 (1000-1999)**: CommonErrorCode
- **用户模块 (2000-2999)**: UserErrorCode
- **账户模块 (3000-3999)**: AccountErrorCode
- **交易模块 (4000-4999)**: TradeErrorCode
- **撮合模块 (5000-5999)**: MatchErrorCode
- **行情模块 (6000-6999)**: QuoteErrorCode (待创建)
- **风控模块 (7000-7999)**: RiskErrorCode
- **消息模块 (8000-8999)**: MessageErrorCode
- **管理后台 (9000-9999)**: AdminErrorCode (待创建)

## 如何扩展错误码

### 1. 在现有模块中添加错误码

#### 示例：在用户模块中添加新的错误码

```java
// 在UserErrorCode枚举中添加新的错误码
public enum UserErrorCode implements ErrorCode {
    // ... 现有错误码 ...
    USER_EMAIL_VERIFICATION_REQUIRED(2020, "需要邮箱验证"),
    USER_PHONE_VERIFICATION_REQUIRED(2021, "需要手机验证"),
    USER_GOOGLE_AUTH_REQUIRED(2022, "需要Google验证器"),
    USER_GOOGLE_AUTH_INVALID(2023, "Google验证器验证失败");
    
    // ... 其他代码 ...
}
```

#### 在BusinessException中添加对应的静态方法

```java
public class BusinessException extends RuntimeException {
    // ... 现有代码 ...
    
    public static BusinessException userEmailVerificationRequired() {
        return new BusinessException(UserErrorCode.USER_EMAIL_VERIFICATION_REQUIRED);
    }
    
    public static BusinessException userPhoneVerificationRequired() {
        return new BusinessException(UserErrorCode.USER_PHONE_VERIFICATION_REQUIRED);
    }
    
    public static BusinessException userGoogleAuthRequired() {
        return new BusinessException(UserErrorCode.USER_GOOGLE_AUTH_REQUIRED);
    }
    
    public static BusinessException userGoogleAuthInvalid() {
        return new BusinessException(UserErrorCode.USER_GOOGLE_AUTH_INVALID);
    }
}
```

### 2. 创建新模块的错误码

#### 示例：创建行情模块错误码

```java
package com.exchange.common.error;

/**
 * 行情模块错误码枚举
 * 定义行情相关的错误码 (6000-6999)
 */
public enum QuoteErrorCode implements ErrorCode {
    
    QUOTE_NOT_FOUND(6000, "行情数据不存在"),
    QUOTE_EXPIRED(6001, "行情数据已过期"),
    QUOTE_SOURCE_ERROR(6002, "行情数据源错误"),
    QUOTE_FORMAT_ERROR(6003, "行情数据格式错误"),
    QUOTE_UPDATE_FAILED(6004, "行情更新失败"),
    QUOTE_SUBSCRIPTION_FAILED(6005, "行情订阅失败"),
    QUOTE_UNSUBSCRIPTION_FAILED(6006, "行情取消订阅失败"),
    QUOTE_PAIR_NOT_SUPPORTED(6007, "不支持的交易对"),
    QUOTE_INTERVAL_NOT_SUPPORTED(6008, "不支持的时间间隔"),
    QUOTE_HISTORY_NOT_AVAILABLE(6009, "历史数据不可用"),
    QUOTE_REAL_TIME_UNAVAILABLE(6010, "实时数据不可用"),
    QUOTE_CALCULATION_ERROR(6011, "行情计算错误"),
    QUOTE_AGGREGATION_ERROR(6012, "行情聚合错误"),
    QUOTE_NORMALIZATION_ERROR(6013, "行情标准化错误"),
    QUOTE_VALIDATION_ERROR(6014, "行情验证错误"),
    QUOTE_SYNC_ERROR(6015, "行情同步错误"),
    QUOTE_CACHE_ERROR(6016, "行情缓存错误"),
    QUOTE_DB_ERROR(6017, "行情数据库错误"),
    QUOTE_NETWORK_ERROR(6018, "行情网络错误"),
    QUOTE_TIMEOUT_ERROR(6019, "行情超时错误"),
    QUOTE_RATE_LIMIT_ERROR(6020, "行情频率限制");
    
    private final int code;
    private final String message;
    
    QuoteErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }
    
    @Override
    public int getCode() {
        return code;
    }
    
    @Override
    public String getMessage() {
        return message;
    }
    
    /**
     * 根据错误码获取错误枚举
     */
    public static QuoteErrorCode fromCode(int code) {
        for (QuoteErrorCode errorCode : values()) {
            if (errorCode.getCode() == code) {
                return errorCode;
            }
        }
        return QUOTE_NOT_FOUND;
    }
}
```

#### 在BusinessException中添加对应的静态方法

```java
public class BusinessException extends RuntimeException {
    // ... 现有代码 ...
    
    /**
     * 创建行情相关异常
     */
    public static BusinessException quoteNotFound() {
        return new BusinessException(QuoteErrorCode.QUOTE_NOT_FOUND);
    }
    
    public static BusinessException quoteExpired() {
        return new BusinessException(QuoteErrorCode.QUOTE_EXPIRED);
    }
    
    public static BusinessException quoteSourceError() {
        return new BusinessException(QuoteErrorCode.QUOTE_SOURCE_ERROR);
    }
    
    public static BusinessException quoteFormatError() {
        return new BusinessException(QuoteErrorCode.QUOTE_FORMAT_ERROR);
    }
}
```

### 3. 创建管理后台错误码

```java
package com.exchange.common.error;

/**
 * 管理后台错误码枚举
 * 定义管理相关的错误码 (9000-9999)
 */
public enum AdminErrorCode implements ErrorCode {
    
    ADMIN_PERMISSION_DENIED(9000, "管理员权限不足"),
    ADMIN_OPERATION_FAILED(9001, "管理员操作失败"),
    ADMIN_CONFIG_ERROR(9002, "管理员配置错误"),
    ADMIN_AUDIT_FAILED(9003, "管理员审计失败"),
    ADMIN_USER_MANAGEMENT_ERROR(9004, "用户管理错误"),
    ADMIN_ACCOUNT_MANAGEMENT_ERROR(9005, "账户管理错误"),
    ADMIN_TRADE_MANAGEMENT_ERROR(9006, "交易管理错误"),
    ADMIN_RISK_MANAGEMENT_ERROR(9007, "风控管理错误"),
    ADMIN_SYSTEM_MANAGEMENT_ERROR(9008, "系统管理错误"),
    ADMIN_LOG_VIEW_ERROR(9009, "日志查看错误"),
    ADMIN_REPORT_GENERATION_ERROR(9010, "报表生成错误"),
    ADMIN_BACKUP_RESTORE_ERROR(9011, "备份恢复错误"),
    ADMIN_MAINTENANCE_MODE_ERROR(9012, "维护模式错误"),
    ADMIN_FEATURE_TOGGLE_ERROR(9013, "功能开关错误"),
    ADMIN_NOTIFICATION_ERROR(9014, "通知管理错误"),
    ADMIN_SETTINGS_ERROR(9015, "设置管理错误"),
    ADMIN_API_MANAGEMENT_ERROR(9016, "API管理错误"),
    ADMIN_WEBHOOK_MANAGEMENT_ERROR(9017, "Webhook管理错误"),
    ADMIN_INTEGRATION_ERROR(9018, "集成管理错误"),
    ADMIN_MONITORING_ERROR(9019, "监控管理错误"),
    ADMIN_ALERT_MANAGEMENT_ERROR(9020, "告警管理错误");
    
    private final int code;
    private final String message;
    
    AdminErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }
    
    @Override
    public int getCode() {
        return code;
    }
    
    @Override
    public String getMessage() {
        return message;
    }
    
    /**
     * 根据错误码获取错误枚举
     */
    public static AdminErrorCode fromCode(int code) {
        for (AdminErrorCode errorCode : values()) {
            if (errorCode.getCode() == code) {
                return errorCode;
            }
        }
        return ADMIN_PERMISSION_DENIED;
    }
}
```

## 使用示例

### 1. 在控制器中使用

```java
@RestController
@RequestMapping("/api/user")
public class UserController {
    
    @PostMapping("/verify-email")
    public ApiResponse<Void> verifyEmail(@RequestParam String email) {
        if (!emailService.isValidEmail(email)) {
            throw BusinessException.userEmailVerificationRequired();
        }
        
        emailService.sendVerificationEmail(email);
        return ResponseUtils.success("验证邮件已发送");
    }
    
    @PostMapping("/enable-2fa")
    public ApiResponse<String> enable2FA(@RequestParam String code) {
        if (!twoFactorAuthService.verifyCode(code)) {
            throw BusinessException.userGoogleAuthInvalid();
        }
        
        String qrCode = twoFactorAuthService.generateQRCode();
        return ResponseUtils.success("两步验证已启用", qrCode);
    }
}
```

### 2. 在服务层中使用

```java
@Service
public class QuoteServiceImpl implements QuoteService {
    
    @Override
    public QuoteDTO getQuote(String symbol) {
        Quote quote = quoteRepository.findBySymbol(symbol);
        if (quote == null) {
            throw BusinessException.quoteNotFound();
        }
        
        if (quote.isExpired()) {
            throw BusinessException.quoteExpired();
        }
        
        return QuoteDTO.from(quote);
    }
    
    @Override
    public void subscribeQuote(String symbol, String userId) {
        try {
            quoteSubscriptionService.subscribe(symbol, userId);
        } catch (SubscriptionException e) {
            throw BusinessException.quoteSubscriptionFailed();
        }
    }
}
```

## 最佳实践

### 1. 错误码命名规范
- 使用模块前缀，如 `USER_`、`ACCOUNT_`、`TRADE_` 等
- 使用描述性的名称，如 `NOT_FOUND`、`ALREADY_EXISTS`、`INSUFFICIENT_BALANCE` 等
- 使用动词+名词的形式，如 `VERIFICATION_FAILED`、`LIMIT_EXCEEDED` 等

### 2. 错误消息规范
- 使用中文描述，便于用户理解
- 消息要简洁明了
- 避免暴露敏感信息
- 提供具体的错误原因

### 3. 错误码分配规范
- 每个模块使用独立的错误码段
- 预留足够的错误码空间
- 避免错误码冲突
- 记录错误码分配情况

### 4. 扩展步骤
1. 在对应的错误码枚举中添加新的错误码
2. 在BusinessException中添加对应的静态方法
3. 在业务代码中使用新的异常方法
4. 更新文档和测试用例

## 总结

通过ErrorCode接口的设计，我们实现了：

1. **模块化**: 每个模块有独立的错误码枚举
2. **可扩展性**: 可以轻松添加新的错误码
3. **类型安全**: 使用枚举确保错误码的类型安全
4. **统一接口**: 所有错误码都实现相同的接口
5. **易于维护**: 错误码集中管理，便于维护

这种设计使得错误码系统既灵活又规范，为整个项目提供了良好的错误管理基础。 