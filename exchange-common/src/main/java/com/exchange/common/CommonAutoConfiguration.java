package com.exchange.common;

import com.exchange.common.event.SystemEventProperties;
import com.exchange.common.event.SystemEventReporter;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;

/**
 * Common 模块自动配置类。
 *
 * <p>提供 RegionPhoneService、统一错误管理、系统事件上报器等跨模块组件。
 *
 * <h3>如何被加载</h3>
 * <p>各服务的 {@code @SpringBootApplication} 仅扫描自身包
 * (如 {@code com.exchange.account}),<b>不会</b>扫描到 {@code com.exchange.common}。
 * 因此本类通过 Spring Boot 3 的自动配置机制注册:
 * <pre>
 *   META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports
 * </pre>
 * 只要依赖了 exchange-common,无需任何额外配置即可获得其中的 Bean。
 *
 * <p>注意:不要依赖调用方手工加 {@code @ComponentScan("com.exchange")},
 * 那样会把其他服务的组件一并扫进来。
 */
@AutoConfiguration
@ComponentScan(basePackages = "com.exchange.common")
@EnableConfigurationProperties(SystemEventProperties.class)
public class CommonAutoConfiguration {

    /**
     * 系统事件上报器(全局单例)。
     *
     * <p>被资金状态机、网关、持久层共用。状态机内调用须传 cluster timestamp,
     * 详见 {@link SystemEventReporter} 类注释。
     */
    @Bean
    @ConditionalOnMissingBean
    public SystemEventReporter systemEventReporter(SystemEventProperties props) {
        return new SystemEventReporter(props);
    }
}
