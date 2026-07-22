package com.exchange.account.core.cluster;

import io.aeron.archive.Archive;
import io.aeron.archive.ArchiveThreadingMode;
import io.aeron.archive.client.AeronArchive;
import io.aeron.cluster.ClusteredMediaDriver;
import io.aeron.cluster.ConsensusModule;
import io.aeron.cluster.service.ClusteredServiceContainer;
import io.aeron.driver.MediaDriver;
import io.aeron.driver.ThreadingMode;
import lombok.extern.slf4j.Slf4j;

import java.io.File;

/**
 * 资产账户 Aeron Cluster 节点引导类。
 *
 * <p>与 {@code MatchClusterNode} 结构完全一致，但使用独立的端口范围
 * 避免与撮合引擎 Cluster 冲突（两个 Cluster 在不同进程，但同一内网需要不同端口）。
 *
 * <h3>端口约定</h3>
 * <pre>
 *   20140 = Asset Cluster ingress（Order Service / Forwarder 连接入口）
 *   20250 = Member-to-member
 *   20360 = Raft log 复制
 *   8020  = Archive 控制端口
 * </pre>
 *
 * <h3>单节点 → 三节点扩展</h3>
 * <p>与 Match Cluster 完全相同——只改环境变量，代码不变。
 */
@Slf4j
public class AssetClusterNode implements AutoCloseable {

    private ClusteredMediaDriver      clusteredMediaDriver;
    private ClusteredServiceContainer serviceContainer;

    /** 启动后可读取，供 AeronArchiveEventPublisher 连接使用 */
    private String resolvedAeronDir;
    private String resolvedArchiveControlChannel;

    public String getAeronDir()                { return resolvedAeronDir; }
    public String getArchiveControlChannel()   { return resolvedArchiveControlChannel; }

    public void start(AssetClusteredService service) {
        final int    nodeId         = intEnv("ASSET_CLUSTER_NODE_ID",  0);
        final String clusterMembers = strEnv("ASSET_CLUSTER_MEMBERS",
                "0,localhost:20140,localhost:20250,localhost:20360,localhost:0,localhost:8020");
        final String aeronDir       = strEnv("ASSET_AERON_DIR",    "aeron-asset-driver");
        final String archiveDir     = strEnv("ASSET_ARCHIVE_DIR",  "aeron-asset-archive");
        final String clusterDir     = strEnv("ASSET_CLUSTER_DIR",  "aeron-asset-cluster");

        log.info("[AssetClusterNode] Starting — nodeId={}, members={}", nodeId, clusterMembers);

        MediaDriver.Context driverCtx = new MediaDriver.Context()
                .aeronDirectoryName(aeronDir)
                .threadingMode(ThreadingMode.DEDICATED)
                .termBufferSparseFile(false)
                .dirDeleteOnStart(true)
                .dirDeleteOnShutdown(true);

        Archive.Context archiveCtx = new Archive.Context()
                .archiveDir(new File(archiveDir))
                .aeronDirectoryName(aeronDir)
                .controlChannel("aeron:udp?endpoint=localhost:8020")
                .replicationChannel("aeron:udp?endpoint=localhost:0")
                .threadingMode(ArchiveThreadingMode.SHARED)
                .deleteArchiveOnStart(false);

        ConsensusModule.Context consensusCtx = new ConsensusModule.Context()
                .clusterMemberId(nodeId)
                .clusterMembers(clusterMembers)
                .clusterDir(new File(clusterDir))
                .aeronDirectoryName(aeronDir)
                // archiveContext 需客户端 AeronArchive.Context（连接本地嵌入式 Archive），非服务端 Archive.Context
                .archiveContext(new AeronArchive.Context()
                        .controlRequestChannel("aeron:udp?endpoint=localhost:8020")
                        .controlResponseChannel("aeron:udp?endpoint=localhost:0")
                        .aeronDirectoryName(aeronDir))
                .ingressChannel("aeron:udp?term-length=64k")
                .logChannel("aeron:udp?term-length=256k|reliable=true");

        // 保存供外部（AeronArchiveEventPublisher）使用
        resolvedAeronDir             = aeronDir;
        resolvedArchiveControlChannel = archiveCtx.controlChannel();

        clusteredMediaDriver = ClusteredMediaDriver.launch(driverCtx, archiveCtx, consensusCtx);
        log.info("[AssetClusterNode] ClusteredMediaDriver launched");

        serviceContainer = ClusteredServiceContainer.launch(
                new ClusteredServiceContainer.Context()
                        .aeronDirectoryName(aeronDir)
                        .archiveContext(new AeronArchive.Context()
                                .controlRequestChannel("aeron:udp?endpoint=localhost:8020")
                                .controlResponseChannel("aeron:udp?endpoint=localhost:0")
                                .aeronDirectoryName(aeronDir))
                        .clusterDir(new File(clusterDir))
                        .clusteredService(service));

        log.info("[AssetClusterNode] Asset Cluster node is ready");
    }

    @Override
    public void close() {
        log.info("[AssetClusterNode] Shutting down");
        if (serviceContainer     != null) serviceContainer.close();
        if (clusteredMediaDriver != null) clusteredMediaDriver.close();
        log.info("[AssetClusterNode] Shutdown complete");
    }

    private static String strEnv(String name, String defaultValue) {
        String v = System.getenv(name);
        return (v != null && !v.isBlank()) ? v : defaultValue;
    }

    private static int intEnv(String name, int defaultValue) {
        try {
            String v = System.getenv(name);
            return (v != null && !v.isBlank()) ? Integer.parseInt(v.trim()) : defaultValue;
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
}
