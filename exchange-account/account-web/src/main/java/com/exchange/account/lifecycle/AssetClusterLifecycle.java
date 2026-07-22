package com.exchange.account.lifecycle;

import com.exchange.account.core.cluster.AssetClusterNode;
import com.exchange.account.core.cluster.AssetClusteredService;
import com.exchange.account.core.cluster.event.AeronArchiveEventPublisher;
import com.exchange.account.core.cluster.event.AssetEventPublisher;
import com.exchange.account.core.cluster.ClusterRuntimeStatus;
import com.exchange.account.core.cluster.ledger.BalanceLedger;
import com.exchange.common.event.SystemEventReporter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

/**
 * 资产账户 Cluster 节点的 Spring 生命周期管理器。
 *
 * <p>在 Spring Boot 启动后启动 Aeron Cluster 节点；关闭时优雅停止。
 *
 * <h3>关注点分离</h3>
 * <p>{@link AssetClusteredService} 为纯内存服务。账本变更通过 {@link AssetEventPublisher}
 *（Archive 实现）发布到 Aeron Archive，由独立的 {@code account-persist} 服务回放并落库。
 *
 * <h3>初始化顺序</h3>
 * <p>{@link AeronArchiveEventPublisher} 需要在 {@code clusterNode.start()} 之后才能连接
 * （Archive 此时已随 ClusteredMediaDriver 一起启动）。因此 publisher.connect() 在
 * {@link #start()} 末尾显式调用，而非在 @PostConstruct 中。
 *
 * <h3>AssetEventPublisher 可选</h3>
 * <p>开发/测试环境 publisher 为 null 时 Cluster 仍正常运行（仅不发布事件）。
 */
@Slf4j
@Component
public class AssetClusterLifecycle implements SmartLifecycle {

    private final AssetEventPublisher eventPublisher;

    /**
     * 系统事件上报器，透传给状态机与账本。
     *
     * <p>由 Spring 注入后手工传递——状态机组件不是 Spring Bean，
     * 无法自动装配，必须在此显式串接。
     */
    private final SystemEventReporter eventReporter;

    /** 运行时状态快照，供 ClusterStatusController 读取。 */
    private final ClusterRuntimeStatus runtimeStatus;

    private AssetClusterNode clusterNode;
    private volatile boolean running = false;

    @Autowired
    public AssetClusterLifecycle(
            @Autowired(required = false) AssetEventPublisher eventPublisher,
            SystemEventReporter eventReporter,
            ClusterRuntimeStatus runtimeStatus) {
        this.eventPublisher = eventPublisher;
        this.eventReporter  = eventReporter;
        this.runtimeStatus  = runtimeStatus;
    }

    @Override
    public void start() {
        log.info("[AssetClusterLifecycle] Starting Asset Cluster node...");

        BalanceLedger ledger = new BalanceLedger(eventReporter);
        AssetClusteredService service =
                new AssetClusteredService(ledger, eventPublisher, eventReporter, runtimeStatus);

        clusterNode = new AssetClusterNode();
        clusterNode.start(service);   // Archive 在此步启动

        // Archive 启动后才能连接 publisher（顺序关键）
        if (eventPublisher instanceof AeronArchiveEventPublisher archivePub) {
            archivePub.connect(clusterNode.getAeronDir(), clusterNode.getArchiveControlChannel());
        }

        running = true;
        log.info("[AssetClusterLifecycle] Asset Cluster node started");
    }

    @Override
    public void stop() {
        log.info("[AssetClusterLifecycle] Stopping Asset Cluster node...");
        if (eventPublisher instanceof AeronArchiveEventPublisher archivePub) {
            archivePub.close();
        }
        if (clusterNode != null) {
            clusterNode.close();
        }
        running = false;
        log.info("[AssetClusterLifecycle] Asset Cluster node stopped");
    }

    @Override public boolean isRunning()     { return running; }
    @Override public int     getPhase()      { return Integer.MAX_VALUE - 100; }
    @Override public boolean isAutoStartup() { return true; }
}
