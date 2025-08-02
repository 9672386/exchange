# 模块错误码架构指南

## 架构设计原则

### 1. 职责分离
- **Common模块**: 只定义ErrorCode接口和通用错误码
- **各业务模块**: 实现自己的错误码枚举和异常类

### 2. 模块独立性
- 每个模块管理自己的错误码
- 模块间通过ErrorCode接口进行交互
- 避免模块间的直接依赖

## Common模块职责

### ErrorCode接口
```java
public interface ErrorCode {
    int getCode();
    String getMessage();
    default boolean isSuccess() { ... }
    default String getModuleName() { ... }
}
```

### CommonErrorCode枚举
```java
public enum CommonErrorCode implements ErrorCode {
    SUCCESS(1000, "操作成功"),
    SYSTEM_ERROR(1001, "系统内部错误"),
    PARAMETER_ERROR(1002, "参数错误"),
    // ... 其他通用错误码
}
```

### BusinessException基类
```java
public class BusinessException extends RuntimeException {
    // 提供通用的业务异常创建方法
    public static BusinessException businessError(ErrorCode errorCode) { ... }
    public static BusinessException businessError(ErrorCode errorCode, String message) { ... }
    public static BusinessException businessError(ErrorCode errorCode, Object data) { ... }
}
```

## 各模块实现示例

### 1. 用户模块 (exchange-user)

#### UserErrorCode.java
```java
package com.exchange.user.error;

import com.exchange.common.error.ErrorCode;

/**
 * 用户模块错误码枚举
 * 定义用户相关的错误码 (2000-2999)
 */
public enum UserErrorCode implements ErrorCode {
    
    USER_NOT_FOUND(2000, "用户不存在"),
    USER_ALREADY_EXISTS(2001, "用户已存在"),
    USER_PASSWORD_ERROR(2002, "密码错误"),
    USER_ACCOUNT_LOCKED(2003, "账户已锁定"),
    USER_ACCOUNT_DISABLED(2004, "账户已禁用"),
    USER_VERIFICATION_FAILED(2005, "用户验证失败"),
    USER_SESSION_EXPIRED(2006, "用户会话已过期"),
    USER_EMAIL_INVALID(2007, "邮箱格式无效"),
    USER_PHONE_INVALID(2008, "手机号格式无效"),
    USER_KYC_REQUIRED(2009, "需要完成KYC认证"),
    USER_KYC_VERIFICATION_PENDING(2010, "KYC认证审核中"),
    USER_KYC_VERIFICATION_FAILED(2011, "KYC认证失败"),
    USER_WITHDRAWAL_LIMIT_EXCEEDED(2012, "提现限额超限"),
    USER_TRADING_LIMIT_EXCEEDED(2013, "交易限额超限"),
    USER_LOGIN_ATTEMPTS_EXCEEDED(2014, "登录尝试次数超限"),
    USER_ACCOUNT_SUSPENDED(2015, "账户已暂停"),
    USER_IP_NOT_ALLOWED(2016, "IP地址不被允许"),
    USER_DEVICE_NOT_AUTHORIZED(2017, "设备未授权"),
    USER_2FA_REQUIRED(2018, "需要两步验证"),
    USER_2FA_INVALID(2019, "两步验证码无效");
    
    private final int code;
    private final String message;
    
    UserErrorCode(int code, String message) {
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
    public static UserErrorCode fromCode(int code) {
        for (UserErrorCode errorCode : values()) {
            if (errorCode.getCode() == code) {
                return errorCode;
            }
        }
        return USER_NOT_FOUND;
    }
}
```

#### UserBusinessException.java
```java
package com.exchange.user.exception;

import com.exchange.common.exception.BusinessException;
import com.exchange.user.error.UserErrorCode;

/**
 * 用户模块业务异常类
 */
public class UserBusinessException extends BusinessException {
    
    public UserBusinessException(UserErrorCode errorCode) {
        super(errorCode);
    }
    
    public UserBusinessException(UserErrorCode errorCode, String message) {
        super(errorCode, message);
    }
    
    public UserBusinessException(UserErrorCode errorCode, Object data) {
        super(errorCode, data);
    }
    
    // 用户相关异常方法
    public static UserBusinessException userNotFound() {
        return new UserBusinessException(UserErrorCode.USER_NOT_FOUND);
    }
    
    public static UserBusinessException userAlreadyExists() {
        return new UserBusinessException(UserErrorCode.USER_ALREADY_EXISTS);
    }
    
    public static UserBusinessException userPasswordError() {
        return new UserBusinessException(UserErrorCode.USER_PASSWORD_ERROR);
    }
    
    public static UserBusinessException userAccountLocked() {
        return new UserBusinessException(UserErrorCode.USER_ACCOUNT_LOCKED);
    }
    
    public static UserBusinessException userAccountDisabled() {
        return new UserBusinessException(UserErrorCode.USER_ACCOUNT_DISABLED);
    }
    
    public static UserBusinessException userEmailInvalid() {
        return new UserBusinessException(UserErrorCode.USER_EMAIL_INVALID);
    }
    
    public static UserBusinessException userPhoneInvalid() {
        return new UserBusinessException(UserErrorCode.USER_PHONE_INVALID);
    }
    
    public static UserBusinessException userKycRequired() {
        return new UserBusinessException(UserErrorCode.USER_KYC_REQUIRED);
    }
    
    public static UserBusinessException user2faRequired() {
        return new UserBusinessException(UserErrorCode.USER_2FA_REQUIRED);
    }
    
    public static UserBusinessException user2faInvalid() {
        return new UserBusinessException(UserErrorCode.USER_2FA_INVALID);
    }
}
```

### 2. 账户模块 (exchange-account)

#### AccountErrorCode.java
```java
package com.exchange.account.error;

import com.exchange.common.error.ErrorCode;

/**
 * 账户模块错误码枚举
 * 定义账户相关的错误码 (3000-3999)
 */
public enum AccountErrorCode implements ErrorCode {
    
    ACCOUNT_NOT_FOUND(3000, "账户不存在"),
    ACCOUNT_BALANCE_INSUFFICIENT(3001, "账户余额不足"),
    ACCOUNT_FROZEN(3002, "账户已冻结"),
    ACCOUNT_TRANSACTION_FAILED(3003, "账户交易失败"),
    ACCOUNT_CURRENCY_NOT_SUPPORTED(3004, "不支持的货币类型"),
    ACCOUNT_DEPOSIT_FAILED(3005, "充值失败"),
    ACCOUNT_WITHDRAWAL_FAILED(3006, "提现失败"),
    ACCOUNT_TRANSFER_FAILED(3007, "转账失败"),
    ACCOUNT_LIMIT_EXCEEDED(3008, "账户限额超限"),
    ACCOUNT_DAILY_LIMIT_EXCEEDED(3009, "日限额超限"),
    ACCOUNT_MONTHLY_LIMIT_EXCEEDED(3010, "月限额超限"),
    ACCOUNT_MINIMUM_BALANCE_NOT_MET(3011, "最低余额要求未满足"),
    ACCOUNT_CURRENCY_PAIR_NOT_SUPPORTED(3012, "不支持的货币对"),
    ACCOUNT_TRADING_SUSPENDED(3013, "交易已暂停"),
    ACCOUNT_WITHDRAWAL_SUSPENDED(3014, "提现已暂停"),
    ACCOUNT_DEPOSIT_SUSPENDED(3015, "充值已暂停"),
    ACCOUNT_MAINTENANCE_MODE(3016, "账户维护模式"),
    ACCOUNT_VERIFICATION_REQUIRED(3017, "需要账户验证"),
    ACCOUNT_VERIFICATION_PENDING(3018, "账户验证审核中"),
    ACCOUNT_VERIFICATION_FAILED(3019, "账户验证失败");
    
    private final int code;
    private final String message;
    
    AccountErrorCode(int code, String message) {
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
    public static AccountErrorCode fromCode(int code) {
        for (AccountErrorCode errorCode : values()) {
            if (errorCode.getCode() == code) {
                return errorCode;
            }
        }
        return ACCOUNT_NOT_FOUND;
    }
}
```

#### AccountBusinessException.java
```java
package com.exchange.account.exception;

import com.exchange.common.exception.BusinessException;
import com.exchange.account.error.AccountErrorCode;

/**
 * 账户模块业务异常类
 */
public class AccountBusinessException extends BusinessException {
    
    public AccountBusinessException(AccountErrorCode errorCode) {
        super(errorCode);
    }
    
    public AccountBusinessException(AccountErrorCode errorCode, String message) {
        super(errorCode, message);
    }
    
    public AccountBusinessException(AccountErrorCode errorCode, Object data) {
        super(errorCode, data);
    }
    
    // 账户相关异常方法
    public static AccountBusinessException accountNotFound() {
        return new AccountBusinessException(AccountErrorCode.ACCOUNT_NOT_FOUND);
    }
    
    public static AccountBusinessException accountBalanceInsufficient() {
        return new AccountBusinessException(AccountErrorCode.ACCOUNT_BALANCE_INSUFFICIENT);
    }
    
    public static AccountBusinessException accountFrozen() {
        return new AccountBusinessException(AccountErrorCode.ACCOUNT_FROZEN);
    }
    
    public static AccountBusinessException accountTransactionFailed() {
        return new AccountBusinessException(AccountErrorCode.ACCOUNT_TRANSACTION_FAILED);
    }
    
    public static AccountBusinessException accountCurrencyNotSupported() {
        return new AccountBusinessException(AccountErrorCode.ACCOUNT_CURRENCY_NOT_SUPPORTED);
    }
    
    public static AccountBusinessException accountDepositFailed() {
        return new AccountBusinessException(AccountErrorCode.ACCOUNT_DEPOSIT_FAILED);
    }
    
    public static AccountBusinessException accountWithdrawalFailed() {
        return new AccountBusinessException(AccountErrorCode.ACCOUNT_WITHDRAWAL_FAILED);
    }
    
    public static AccountBusinessException accountTransferFailed() {
        return new AccountBusinessException(AccountErrorCode.ACCOUNT_TRANSFER_FAILED);
    }
    
    public static AccountBusinessException accountLimitExceeded() {
        return new AccountBusinessException(AccountErrorCode.ACCOUNT_LIMIT_EXCEEDED);
    }
}
```

## 使用示例

### 在用户模块中使用
```java
@Service
public class UserServiceImpl implements UserService {
    
    @Override
    public UserDTO getUser(Long id) {
        User user = userRepository.findById(id);
        if (user == null) {
            throw UserBusinessException.userNotFound();
        }
        return UserDTO.from(user);
    }
    
    @Override
    public UserDTO createUser(CreateUserRequest request) {
        if (userRepository.existsByUsername(request.getUsername())) {
            throw UserBusinessException.userAlreadyExists();
        }
        
        if (!EmailValidator.isValid(request.getEmail())) {
            throw UserBusinessException.userEmailInvalid();
        }
        
        // 创建用户逻辑...
        return UserDTO.from(savedUser);
    }
    
    @Override
    public void enable2FA(Long userId, String code) {
        if (!twoFactorAuthService.verifyCode(code)) {
            throw UserBusinessException.user2faInvalid();
        }
        
        // 启用两步验证逻辑...
    }
}
```

### 在账户模块中使用
```java
@Service
public class AccountServiceImpl implements AccountService {
    
    @Override
    public void transfer(Long fromAccountId, Long toAccountId, BigDecimal amount) {
        Account fromAccount = accountRepository.findById(fromAccountId);
        if (fromAccount == null) {
            throw AccountBusinessException.accountNotFound();
        }
        
        if (fromAccount.getBalance().compareTo(amount) < 0) {
            throw AccountBusinessException.accountBalanceInsufficient();
        }
        
        if (fromAccount.getStatus() == AccountStatus.FROZEN) {
            throw AccountBusinessException.accountFrozen();
        }
        
        // 转账逻辑...
    }
    
    @Override
    public void deposit(Long accountId, BigDecimal amount, String currency) {
        if (!supportedCurrencies.contains(currency)) {
            throw AccountBusinessException.accountCurrencyNotSupported();
        }
        
        // 充值逻辑...
    }
}
```

## 错误码段分配

| 模块 | 错误码段 | 错误码枚举 | 异常类 |
|------|----------|------------|--------|
| 通用 | 1000-1999 | CommonErrorCode | BusinessException |
| 用户 | 2000-2999 | UserErrorCode | UserBusinessException |
| 账户 | 3000-3999 | AccountErrorCode | AccountBusinessException |
| 交易 | 4000-4999 | TradeErrorCode | TradeBusinessException |
| 撮合 | 5000-5999 | MatchErrorCode | MatchBusinessException |
| 行情 | 6000-6999 | QuoteErrorCode | QuoteBusinessException |
| 风控 | 7000-7999 | RiskErrorCode | RiskBusinessException |
| 消息 | 8000-8999 | MessageErrorCode | MessageBusinessException |
| 管理 | 9000-9999 | AdminErrorCode | AdminBusinessException |

## 最佳实践

### 1. 模块独立性
- 每个模块只依赖common模块的ErrorCode接口
- 模块间不直接依赖对方的错误码
- 通过统一的异常处理机制进行交互

### 2. 错误码管理
- 每个模块在自己的包中定义错误码枚举
- 错误码枚举实现ErrorCode接口
- 使用模块前缀避免命名冲突

### 3. 异常类设计
- 每个模块有自己的BusinessException子类
- 提供便捷的静态方法创建异常
- 继承common模块的BusinessException基类

### 4. 扩展步骤
1. 在模块中创建ErrorCode枚举实现
2. 创建模块的BusinessException子类
3. 在业务代码中使用模块特定的异常类
4. 确保错误码段不冲突

## 总结

这种架构设计的优势：

1. **模块独立性**: 每个模块管理自己的错误码，不依赖其他模块
2. **职责分离**: Common模块只提供基础设施，业务模块负责具体实现
3. **可扩展性**: 可以轻松添加新模块，每个模块有独立的错误码段
4. **类型安全**: 使用枚举确保错误码的类型安全
5. **统一接口**: 所有模块通过ErrorCode接口进行交互
6. **易于维护**: 错误码分散在各模块中，便于维护和管理 