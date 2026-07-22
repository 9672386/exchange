package com.exchange.transport.aeron.config;

import io.aeron.Aeron;
import io.aeron.driver.MediaDriver;
import io.aeron.driver.ThreadingMode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Aeron Spring Boot 自动配置。
 *
 * <p>依赖方在 classpath 引入 {@code framework-transport} 后自动生效（通过
 * {@code META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports}）。
 *
 * <h3>开关</h3>
 * <pre>
 *   aeron.enabled=true   # 默认 true，设为 false 可完全跳过 Aeron 初始化（保持纯 Kafka 模式）
 * </pre>
 *
 * <h3>MediaDriver 模式</h3>
 * <pre>
 *   aeron.threading-mode=DEDICATED   # 生产推荐：独立线程，最低延迟
 *   aeron.threading-mode=SHARED      # 开发 / 低负载：共享线程，节省资源
 * </pre>
 *
 * <h3>每个进程只能有一个 MediaDriver</h3>
 * <p>多个模块依赖此 jar 时，Spring 容器保证 singleton bean，不会重复启动 driver。
 * 若进程外已有独立 MediaDriver 进程，可设 {@code aeron.embedded-driver=false} 跳过内嵌初始化。
 */
@Slf4j
@Configuration
@ConditionalOnProperty(name = "aeron.enabled", havingValue = "true", matchIfMissing = true)
public class AeronAutoConfiguration {

    /**
     * 内嵌 MediaDriver —— Aeron 的网络 I/O 内核。
     *
     * <p>生命周期由 Spring 管理：应用启动时 {@code launch()}，关闭时 {@code close()}。
     * {@code @ConditionalOnProperty(aeron.embedded-driver)} 为 false 时跳过，
     * 进程将连接外部独立 driver（适合多进程共享同一 driver 的高性能场景）。
     */
    @Bean(destroyMethod = "close")
    @ConditionalOnMissingBean(MediaDriver.class)
    @ConditionalOnProperty(name = "aeron.embedded-driver", havingValue = "true", matchIfMissing = true)
    public MediaDriver mediaDriver() {
        String threadingModeStr = System.getProperty("aeron.threading-mode",
                System.getenv().getOrDefault("AERON_THREADING_MODE", "DEDICATED"));

        ThreadingMode threadingMode;
        try {
            threadingMode = ThreadingMode.valueOf(threadingModeStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            log.warn("[Aeron] Unknown threading-mode '{}', falling back to DEDICATED", threadingModeStr);
            threadingMode = ThreadingMode.DEDICATED;
        }

        MediaDriver.Context ctx = new MediaDriver.Context()
                .dirDeleteOnStart(true)       // 清理上次异常退出残留的 aeron dir
                .dirDeleteOnShutdown(true)
                .threadingMode(threadingMode)
                // 生产建议：绑定到低延迟 CPU（与 Disruptor 线程隔离），此处不强制，由 JVM 参数控制
                .conductorIdleStrategy(new org.agrona.concurrent.BackoffIdleStrategy())
                .senderIdleStrategy(new org.agrona.concurrent.BusySpinIdleStrategy())
                .receiverIdleStrategy(new org.agrona.concurrent.BusySpinIdleStrategy());

        MediaDriver driver = MediaDriver.launch(ctx);
        log.info("[Aeron] MediaDriver launched — dir={}, threadingMode={}",
                driver.aeronDirectoryName(), threadingMode);
        return driver;
    }

    /**
     * Aeron 客户端连接。
     *
     * <p>所有 Publication / Subscription 均通过此 bean 创建，由各组件持有各自的 pub/sub 引用。
     */
    @Bean(destroyMethod = "close")
    @ConditionalOnMissingBean(Aeron.class)
    public Aeron aeron(MediaDriver mediaDriver) {
        Aeron.Context ctx = new Aeron.Context()
                .aeronDirectoryName(mediaDriver.aeronDirectoryName())
                // 错误回调：生产环境可接入 metrics / alert
                .errorHandler(throwable ->
                        log.error("[Aeron] Client error: {}", throwable.getMessage(), throwable));

        Aeron aeron = Aeron.connect(ctx);
        log.info("[Aeron] Client connected to driver dir={}", mediaDriver.aeronDirectoryName());
        return aeron;
    }
}
