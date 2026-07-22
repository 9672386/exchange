package com.exchange.match.lifecycle;

import com.exchange.common.event.SystemEventReporter;
import com.exchange.match.core.cluster.MatchClusterNode;
import com.exchange.match.core.cluster.MatchClusteredService;
import com.exchange.match.core.cluster.MatchRuntimeStatus;
import com.exchange.match.core.memory.MemoryManager;
import com.exchange.match.core.service.MatchEngineService;
import com.exchange.match.core.transport.AeronMatchResultPublisher;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

/**
 * 撮合引擎 Cluster 节点的 Spring 生命周期管理器。
 *
 * <p>在 Spring Boot 启动完成（所有 bean 初始化后）启动 Aeron Cluster 节点；
 * 在 Spring 关闭时优雅停止节点，避免 Raft log 损坏。
 *
 * <h3>关于 {@code phase}</h3>
 * <p>Phase = {@code Integer.MAX_VALUE - 100}，晚于所有普通 bean（phase 0）启动，
 * 早于 Spring 内置基础设施 bean 停止，确保撮合节点是最后启动、最早关闭的业务组件。
 *
 * <h3>关于 Spring 与 ClusteredService 的隔离</h3>
 * <p>{@link MatchClusteredService} 是纯 Java 类（不含 Spring 注解），避免框架在
 * Aeron Service Thread 上触发 Spring AOP / 事务代理，消除潜在的延迟抖动。
 * Spring Bean 依赖通过此 Lifecycle 的构造器注入后以构造参数传递。
 */
@Slf4j
@Component
public class MatchClusterLifecycle implements SmartLifecycle {

    private final MatchEngineService        matchEngineService;
    private final MemoryManager             memoryManager;
    /** Aeron MDC 出站发布者（可为 null：aeron.enabled=false 时未注入） */
    private final AeronMatchResultPublisher aeronPublisher;
    private final SystemEventReporter       eventReporter;
    private final MatchRuntimeStatus        runtimeStatus;

    private MatchClusterNode clusterNode;
    private volatile boolean running = false;

    @Autowired
    public MatchClusterLifecycle(MatchEngineService matchEngineService,
                                 MemoryManager memoryManager,
                                 @Autowired(required = false) AeronMatchResultPublisher aeronPublisher,
                                 SystemEventReporter eventReporter,
                                 MatchRuntimeStatus runtimeStatus) {
        this.matchEngineService = matchEngineService;
        this.memoryManager      = memoryManager;
        this.aeronPublisher     = aeronPublisher;
        this.eventReporter      = eventReporter;
        this.runtimeStatus      = runtimeStatus;
    }

    @Override
    public void start() {
        log.info("[MatchClusterLifecycle] Starting Aeron Cluster node...");

        MatchClusteredService service = new MatchClusteredService(
                matchEngineService, memoryManager, aeronPublisher, eventReporter, runtimeStatus);

        clusterNode = new MatchClusterNode();
        clusterNode.start(service);
        running = true;

        log.info("[MatchClusterLifecycle] Aeron Cluster node started successfully");
    }

    @Override
    public void stop() {
        log.info("[MatchClusterLifecycle] Stopping Aeron Cluster node...");
        if (clusterNode != null) {
            clusterNode.close();
        }
        running = false;
        log.info("[MatchClusterLifecycle] Aeron Cluster node stopped");
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    @Override
    public int getPhase() {
        // 晚启动、早停止：确保所有业务 bean 就绪后再开始接受撮合请求
        return Integer.MAX_VALUE - 100;
    }

    @Override
    public boolean isAutoStartup() {
        return true;
    }
}
