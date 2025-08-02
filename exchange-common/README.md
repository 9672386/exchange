# Exchange Common Module

交易所系统的公共模块，提供各种共享的工具类、服务接口和实现。

## 模块结构

```
exchange-common/
├── src/main/java/com/exchange/common/
│   ├── region/                    # 地区和国家解析功能
│   │   ├── RegionPhoneService.java    # 地区解析服务接口
│   │   ├── impl/
│   │   │   └── RegionPhoneServiceImpl.java  # 地区解析服务实现
│   │   └── dto/                   # 数据传输对象
│   │       ├── CountryInfo.java       # 国家信息
│   │       ├── RegionInfo.java        # 地区信息
│   │       └── PhoneNumberInfo.java   # 手机号信息
│   └── CommonAutoConfiguration.java   # 自动配置类
└── src/test/java/com/exchange/common/
    └── region/
        └── RegionPhoneServiceTest.java # 测试类
```

## 功能特性

### 地区和国家解析 (Region & Phone Service)

提供完整的手机号解析、国家信息查询、地区信息查询等功能。

#### 主要功能

1. **手机号解析**
   - 验证手机号格式
   - 格式化手机号
   - 提取区号、国家代码、地区代码
   - 获取手机号类型

2. **国家信息查询**
   - 根据区号获取国家信息
   - 根据国家代码获取国家信息
   - 获取所有支持的国家列表

3. **地区信息查询**
   - 根据地区代码获取地区信息
   - 获取所有支持的地区列表

4. **完整信息解析**
   - 解析手机号获取完整信息（国家、地区、格式化号码等）

#### 支持的国家和地区

- **中国** (CN) - 区号: 86, 货币: CNY, 时区: Asia/Shanghai
- **美国** (US) - 区号: 1, 货币: USD, 时区: America/New_York
- **日本** (JP) - 区号: 81, 货币: JPY, 时区: Asia/Tokyo
- **韩国** (KR) - 区号: 82, 货币: KRW, 时区: Asia/Seoul
- **香港** (HK) - 区号: 852, 货币: HKD, 时区: Asia/Hong_Kong
- **台湾** (TW) - 区号: 886, 货币: TWD, 时区: Asia/Taipei
- **新加坡** (SG) - 区号: 65, 货币: SGD, 时区: Asia/Singapore
- **英国** (GB) - 区号: 44, 货币: GBP, 时区: Europe/London
- **德国** (DE) - 区号: 49, 货币: EUR, 时区: Europe/Berlin
- **法国** (FR) - 区号: 33, 货币: EUR, 时区: Europe/Paris

## 使用方式

### 在其他模块中使用

1. **添加依赖**
   ```xml
   <dependency>
       <groupId>com.exchange</groupId>
       <artifactId>exchange-common</artifactId>
       <version>${project.version}</version>
   </dependency>
   ```

2. **注入服务**
   ```java
   @Autowired
   private RegionPhoneService regionPhoneService;
   ```

3. **使用示例**
   ```java
   // 解析手机号
   Optional<PhoneNumberInfo> phoneInfo = regionPhoneService.parsePhoneNumber("+8613800138000");
   
   // 验证手机号
   boolean isValid = regionPhoneService.isValidPhoneNumber("+8613800138000");
   
   // 获取国家信息
   Optional<CountryInfo> country = regionPhoneService.getCountryByCode("CN");
   
   // 获取所有国家
   List<CountryInfo> countries = regionPhoneService.getAllCountries();
   ```

### API接口

如果需要在Web层暴露API，可以参考 `exchange-message` 模块中的 `RegionPhoneController`。

## 技术实现

### 依赖库

- **libphonenumber**: Google的手机号解析库
- **Spring Boot**: 框架支持
- **JUnit 5**: 单元测试

### 核心特性

1. **缓存机制**: 使用ConcurrentHashMap缓存国家和地区信息，提高查询性能
2. **线程安全**: 所有操作都是线程安全的
3. **国际化支持**: 支持中英文国家名称
4. **扩展性**: 易于添加新的国家和地区支持

### 性能优化

- 使用内存缓存减少重复查询
- 延迟初始化，只在首次使用时加载数据
- 线程安全的并发访问

## 测试

运行测试：
```bash
mvn test -pl exchange-common
```

测试覆盖了以下功能：
- 手机号解析和验证
- 区号和国家代码提取
- 国家信息查询
- 地区信息查询
- 格式化功能

## 迁移说明

### 从消息模块迁移

原来的地区解析功能位于 `exchange-message` 模块中，现在已经迁移到 `exchange-common` 模块。

**迁移的好处：**
1. **复用性**: 其他服务（如用户服务、账户服务）可以直接使用
2. **维护性**: 统一维护，避免重复代码
3. **扩展性**: 便于添加新的功能和支持

**迁移步骤：**
1. 在 `exchange-message` 模块中删除原有的 `RegionPhoneService` 和实现类
2. 更新 `MessageSenderManager` 和 `RegionPhoneController` 的导入
3. 确保所有使用地区解析功能的地方都使用common模块中的服务

## 未来扩展

1. **数据库支持**: 将国家和地区信息存储到数据库中，支持动态配置
2. **更多国家**: 添加更多国家和地区的支持
3. **时区处理**: 增强时区相关的功能
4. **货币转换**: 添加货币转换功能
5. **语言支持**: 支持更多语言的国家名称

## 贡献指南

1. 添加新的国家或地区时，请同时更新测试用例
2. 修改核心逻辑时，请确保向后兼容
3. 新增功能时，请添加相应的文档说明 