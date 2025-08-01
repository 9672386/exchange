# Exchange Trading System

一个完整的交易所系统，采用微服务架构设计。

## 项目结构

```
exchange/
├── pom.xml                           # 根聚合POM文件
├── exchange-parent/                   # 统一版本管理模块
│   └── pom.xml
├── exchange-common/                  # 公共模块
│   ├── pom.xml
│   └── src/main/java/com/exchange/common/
├── exchange-user/                    # 用户服务模块
│   ├── pom.xml                       # 用户模块聚合POM
│   ├── api/                          # API接口定义
│   │   ├── pom.xml
│   │   └── src/main/java/com/exchange/user/api/
│   ├── web/                          # Web层
│   │   ├── pom.xml
│   │   └── src/main/java/com/exchange/user/web/
│   └── core/                         # 核心业务逻辑
│       ├── pom.xml
│       └── src/main/java/com/exchange/user/core/
├── exchange-account/                 # 账户服务模块
│   ├── pom.xml
│   ├── api/
│   ├── web/
│   └── core/
├── exchange-quote/                   # 报价服务模块
│   ├── pom.xml
│   ├── api/
│   ├── web/
│   └── core/
├── exchange-match/                   # 撮合引擎模块
│   ├── pom.xml
│   ├── api/
│   ├── web/
│   └── core/
├── exchange-risk/                    # 风控服务模块
│   ├── pom.xml
│   ├── api/
│   ├── web/
│   └── core/
├── exchange-gateway/                 # API网关
│   ├── pom.xml
│   └── src/main/java/com/exchange/gateway/
└── exchange-admin/                   # 管理后台模块
    ├── pom.xml
    ├── api/
    ├── web/
    └── core/
```

## 模块说明

### 统一版本管理
- **exchange-parent**: 统一管理所有依赖版本、插件版本，不包含具体业务代码

### 公共模块
- **exchange-common**: 公共工具类、统一响应格式、异常处理、常量定义、基础实体类

### 业务模块结构
每个业务模块都包含三个子模块：

#### API模块 (api/)
- 接口定义和DTO对象
- 常量定义
- 枚举类型
- 供其他服务调用的Feign接口
- 不包含具体实现，只做接口和参数定义

#### Web模块 (web/)
- Controller层
- 配置类
- 拦截器
- 过滤器
- 依赖API模块和Core模块
- 包含Spring Boot启动类

#### Core模块 (core/)
- 核心业务逻辑
- Service层
- Repository层
- 实体类
- 依赖API模块
- 不依赖Web模块，保持核心逻辑的独立性

## 技术栈

### 核心框架
- **Spring Boot 3.2.0** - 应用框架
- **Spring Cloud 2023.0.0** - 微服务框架
- **Spring Cloud Alibaba 2022.0.0.0** - 阿里云微服务组件

### 数据库
- **MySQL 8.0.33** - 主数据库
- **MyBatis Plus 3.5.4** - ORM框架
- **Druid 1.2.20** - 数据库连接池

### 缓存
- **Redis 3.2.1** - 缓存数据库
- **Redisson 3.24.3** - Redis分布式锁

### 消息队列
- **RocketMQ 5.1.4** - 消息队列
- **Kafka 3.6.0** - 流处理

### 监控和日志
- **Prometheus 1.12.0** - 监控
- **Micrometer 1.12.0** - 指标收集
- **Logback 1.4.11** - 日志框架

### 安全
- **JWT 0.12.3** - 身份认证
- **BCrypt 0.10.2** - 密码加密

### 工具库
- **Hutool 5.8.22** - 工具类库
- **FastJSON2 2.0.43** - JSON处理
- **Apache Commons 3.13.0** - 通用工具
- **Guava 32.1.3-jre** - Google工具库

### 测试
- **JUnit 5 5.10.0** - 单元测试
- **Mockito 5.7.0** - Mock框架
- **TestContainers 1.19.3** - 集成测试

### 文档
- **Swagger 2.2.0** - API文档
- **Knife4j 4.3.0** - 增强API文档

## 模块职责

### exchange-user (用户服务)
- **api**: 用户相关接口定义、DTO、常量
- **web**: 用户注册登录、用户信息管理、权限控制
- **core**: 用户业务逻辑、数据访问、身份认证

### exchange-account (账户服务)
- **api**: 账户相关接口定义、DTO、常量
- **web**: 账户管理、资金管理、交易记录
- **core**: 账户业务逻辑、资金计算、安全控制

### exchange-quote (报价服务)
- **api**: 报价相关接口定义、DTO、常量
- **web**: 实时价格推送、行情数据管理、WebSocket连接
- **core**: 价格计算、行情数据处理、推送逻辑

### exchange-match (撮合引擎)
- **api**: 撮合相关接口定义、DTO、常量
- **web**: 订单撮合、价格匹配、交易执行
- **core**: 撮合算法、订单状态管理、交易逻辑

### exchange-risk (风控服务)
- **api**: 风控相关接口定义、DTO、常量
- **web**: 风险控制、交易限制、异常监控
- **core**: 风控规则、合规检查、风险计算

### exchange-gateway (API网关)
- 请求路由、负载均衡、安全过滤、限流控制

### exchange-admin (管理后台)
- **api**: 管理相关接口定义、DTO、常量
- **web**: 系统管理、用户管理、交易监控
- **core**: 管理业务逻辑、数据统计、权限管理

## 构建和运行

### 环境要求
- JDK 17+
- Maven 3.8+
- MySQL 8.0+
- Redis 6.0+
- Nacos 2.0+

### 构建项目
```bash
# 构建整个项目
mvn clean install

# 构建特定模块
mvn clean install -pl exchange-user -am

# 构建特定子模块
mvn clean install -pl exchange-user/web
```

### 运行特定模块
```bash
# 运行用户服务Web模块
cd exchange-user/web
mvn spring-boot:run

# 运行网关
cd exchange-gateway
mvn spring-boot:run
```

### 环境配置
项目支持多环境配置：
- `dev` - 开发环境（默认）
- `test` - 测试环境
- `prod` - 生产环境

使用Maven Profile切换环境：
```bash
mvn clean install -Pprod
```

## 版本管理

所有依赖版本统一在 `exchange-parent/pom.xml` 的 `<properties>` 中管理，包括：
- Spring Boot版本
- 数据库版本
- 缓存版本
- 消息队列版本
- 监控版本
- 安全版本
- 工具库版本
- 测试版本
- 文档版本

## 开发规范

1. **代码规范**：遵循阿里巴巴Java开发手册
2. **命名规范**：使用驼峰命名法
3. **注释规范**：类和方法必须有注释
4. **测试规范**：核心业务必须有单元测试
5. **提交规范**：使用约定式提交
6. **模块依赖**：API模块不依赖其他模块，Core模块只依赖API模块，Web模块依赖API和Core模块

## 部署说明

### Docker部署
每个Web模块都支持Docker部署，Dockerfile位于各Web模块根目录。

### Kubernetes部署
提供完整的K8s部署配置文件。

### 监控告警
集成Prometheus + Grafana监控体系。

## 贡献指南

1. Fork项目
2. 创建功能分支
3. 提交代码
4. 创建Pull Request

## 许可证

MIT License 