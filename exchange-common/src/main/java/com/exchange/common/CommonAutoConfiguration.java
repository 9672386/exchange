package com.exchange.common;

import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

/**
 * Common模块自动配置类
 * 确保Spring Boot能够扫描到common模块中的组件
 */
@Configuration
@ComponentScan(basePackages = "com.exchange.common")
public class CommonAutoConfiguration {
    
    // 这个类的主要作用是确保Spring Boot能够扫描到common模块中的组件
    // 包括RegionPhoneService等服务类以及统一错误管理相关组件
} 