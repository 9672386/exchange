# 统一错误管理系统使用指南

## 概述

本系统提供了统一的错误管理和API响应格式，确保所有模块的API接口都返回标准化的响应格式。

## 核心组件

### 1. ErrorCode - 错误码枚举

定义了所有模块的错误码，每个模块使用一个错误码段：

- **通用错误码 (1000-1999)**: 系统级错误
- **用户模块 (2000-2999)**: 用户相关错误
- **账户模块 (3000-3999)**: 账户相关错误
- **交易模块 (4000-4999)**: 交易相关错误
- **撮合模块 (5000-5999)**: 撮合相关错误
- **行情模块 (6000-6999)**: 行情相关错误
- **风控模块 (7000-7999)**: 风控相关错误
- **消息模块 (8000-8999)**: 消息相关错误
- **管理后台 (9000-9999)**: 管理相关错误

### 2. ApiResponse - 统一响应对象

所有API接口都使用此格式返回数据：

```json
{
  "code": 1000,
  "message": "操作成功",
  "data": {},
  "timestamp": "2024-01-01T12:00:00",
  "traceId": "trace-123"
}
```

### 3. BusinessException - 业务异常类

用于抛出业务相关的异常，提供便捷的静态方法：

```java
// 用户相关异常
throw BusinessException.userNotFound();
throw BusinessException.userAlreadyExists();

// 账户相关异常
throw BusinessException.accountNotFound();
throw BusinessException.accountBalanceInsufficient();

// 通用异常
throw BusinessException.parameterError("参数错误");
throw BusinessException.systemError("系统错误");
```

### 4. GlobalExceptionHandler - 全局异常处理器

自动捕获并处理各种异常，转换为统一响应格式：

- `BusinessException`: 业务异常
- `MethodArgumentNotValidException`: 参数校验异常
- `BindException`: 参数绑定异常
- `ConstraintViolationException`: 约束违反异常
- `NumberFormatException`: 数字格式异常
- `NullPointerException`: 空指针异常
- `RuntimeException`: 运行时异常
- `Exception`: 其他异常

### 5. ResponseUtils - 响应工具类

提供便捷的响应创建方法：

```java
// 成功响应
return ResponseUtils.success();
return ResponseUtils.success(data);
return ResponseUtils.success("自定义消息", data);

// 失败响应
return ResponseUtils.error(ErrorCode.USER_NOT_FOUND);
return ResponseUtils.error(ErrorCode.PARAMETER_ERROR, errorData);
return ResponseUtils.error(1001, "自定义错误消息");
```

## 使用示例

### 1. 控制器中的使用

```java
@RestController
@RequestMapping("/api/user")
public class UserController {
    
    @GetMapping("/{id}")
    public ApiResponse<UserDTO> getUser(@PathVariable Long id) {
        UserDTO user = userService.getUser(id);
        if (user == null) {
            throw BusinessException.userNotFound();
        }
        return ResponseUtils.success(user);
    }
    
    @PostMapping
    public ApiResponse<UserDTO> createUser(@Valid @RequestBody CreateUserRequest request) {
        try {
            UserDTO user = userService.createUser(request);
            return ResponseUtils.success(user);
        } catch (UserExistsException e) {
            throw BusinessException.userAlreadyExists();
        }
    }
    
    @PutMapping("/{id}/balance")
    public ApiResponse<Void> updateBalance(@PathVariable Long id, @RequestParam BigDecimal amount) {
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw BusinessException.parameterError("金额必须大于0");
        }
        
        try {
            userService.updateBalance(id, amount);
            return ResponseUtils.success();
        } catch (InsufficientBalanceException e) {
            throw BusinessException.accountBalanceInsufficient();
        }
    }
}
```

### 2. 服务层中的使用

```java
@Service
public class UserServiceImpl implements UserService {
    
    @Override
    public UserDTO getUser(Long id) {
        User user = userRepository.findById(id);
        if (user == null) {
            throw BusinessException.userNotFound();
        }
        return UserDTO.from(user);
    }
    
    @Override
    public UserDTO createUser(CreateUserRequest request) {
        // 检查用户名是否已存在
        if (userRepository.existsByUsername(request.getUsername())) {
            throw BusinessException.userAlreadyExists();
        }
        
        // 创建用户
        User user = new User();
        user.setUsername(request.getUsername());
        user.setEmail(request.getEmail());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        
        User savedUser = userRepository.save(user);
        return UserDTO.from(savedUser);
    }
}
```

### 3. 自定义错误码

如果需要添加新的错误码，请在 `ErrorCode` 枚举中添加：

```java
// 在ErrorCode枚举中添加新的错误码
USER_EMAIL_INVALID(2007, "邮箱格式无效"),
USER_PHONE_INVALID(2008, "手机号格式无效"),
```

然后在 `BusinessException` 中添加对应的静态方法：

```java
public static BusinessException userEmailInvalid() {
    return new BusinessException(ErrorCode.USER_EMAIL_INVALID);
}

public static BusinessException userPhoneInvalid() {
    return new BusinessException(ErrorCode.USER_PHONE_INVALID);
}
```

## 错误码段分配

| 模块 | 错误码段 | 说明 |
|------|----------|------|
| 通用 | 1000-1999 | 系统级错误，如参数错误、系统错误等 |
| 用户 | 2000-2999 | 用户相关错误，如用户不存在、密码错误等 |
| 账户 | 3000-3999 | 账户相关错误，如余额不足、账户冻结等 |
| 交易 | 4000-4999 | 交易相关错误，如订单无效、价格错误等 |
| 撮合 | 5000-5999 | 撮合相关错误，如撮合引擎错误、市场关闭等 |
| 行情 | 6000-6999 | 行情相关错误，如行情数据不存在、数据过期等 |
| 风控 | 7000-7999 | 风控相关错误，如风控检查失败、超出限制等 |
| 消息 | 8000-8999 | 消息相关错误，如发送失败、模板不存在等 |
| 管理 | 9000-9999 | 管理相关错误，如权限不足、操作失败等 |

## 最佳实践

1. **使用预定义的错误码**: 优先使用 `ErrorCode` 枚举中已定义的错误码
2. **使用便捷方法**: 使用 `BusinessException` 的静态方法创建异常
3. **使用工具类**: 使用 `ResponseUtils` 创建响应对象
4. **添加追踪ID**: 系统会自动添加请求追踪ID，便于日志追踪
5. **记录详细日志**: 在抛出异常前记录详细的错误信息
6. **避免敏感信息**: 不要在错误消息中暴露敏感信息

## 响应格式

### 成功响应
```json
{
  "code": 1000,
  "message": "操作成功",
  "data": {
    "id": 1,
    "username": "test",
    "email": "test@example.com"
  },
  "timestamp": "2024-01-01T12:00:00",
  "traceId": "trace-123"
}
```

### 错误响应
```json
{
  "code": 2000,
  "message": "用户不存在",
  "data": null,
  "timestamp": "2024-01-01T12:00:00",
  "traceId": "trace-123"
}
```

### 带详细信息的错误响应
```json
{
  "code": 2001,
  "message": "用户已存在",
  "data": {
    "field": "username",
    "reason": "用户名已存在",
    "suggestion": "请使用其他用户名"
  },
  "timestamp": "2024-01-01T12:00:00",
  "traceId": "trace-123"
}
``` 