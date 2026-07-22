package com.exchange.framework.web.config;

import com.exchange.framework.web.exception.GlobalExceptionHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/**
 * Web 层公共自动配置。
 *
 * <p>通过 {@code META-INF/spring/AutoConfiguration.imports} 自动装配，
 * 业务模块引入 {@code framework-web} 依赖即可获得以下能力：
 * <ul>
 *   <li>全局异常处理（{@link GlobalExceptionHandler}）</li>
 *   <li>Jackson 统一配置（Java 8 时间类型、禁用时间戳序列化）</li>
 * </ul>
 *
 * <h3>开关</h3>
 * <pre>
 *   framework.web.enabled=false   # 完全关闭此 starter（极少用）
 * </pre>
 *
 * <h3>扩展</h3>
 * <p>业务模块可通过 {@code @ConditionalOnMissingBean} 覆盖任意 bean，
 * 例如自定义 {@code ObjectMapper} 配置只需在业务模块中声明同类型 bean 即可。
 */
@Configuration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnProperty(name = "framework.web.enabled", havingValue = "true", matchIfMissing = true)
@Import(GlobalExceptionHandler.class)
public class WebAutoConfiguration {

    /**
     * 统一 Jackson 配置：
     * <ul>
     *   <li>注册 {@code JavaTimeModule}，支持 {@code LocalDateTime} 等 Java 8 时间类型</li>
     *   <li>禁用 {@code WRITE_DATES_AS_TIMESTAMPS}，日期序列化为 ISO-8601 字符串</li>
     * </ul>
     * <p>业务模块已有自定义 {@code ObjectMapper} bean 时，此配置自动跳过。
     */
    @Bean
    @ConditionalOnMissingBean(ObjectMapper.class)
    public ObjectMapper objectMapper() {
        return new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }
}
