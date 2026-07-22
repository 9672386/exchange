package com.exchange.match.core.cluster;

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
 * 撮合引擎 Aeron Cluster 节点引导类。
 *
 * <p>负责启动 {@link ClusteredMediaDriver}（内嵌 MediaDriver + Archive + ConsensusModule）
 * 和 {@link ClusteredServiceContainer}（运行 {@link MatchClusteredService}）。
 *
 * <h3>单节点配置（默认）</h3>
 * <pre>
 *   CLUSTER_NODE_ID=0
 *   CLUSTER_MEMBERS=0,localhost:20110,localhost:20220,localhost:20330,localhost:0,localhost:8010
 * </pre>
 *
 * <h3>三节点扩展（仅改环境变量，不改代码）</h3>
 * <pre>
 *   CLUSTER_NODE_ID=0   （或 1, 2 — 每个进程不同）
 *   CLUSTER_MEMBERS=
 *     0,node0:20110,node0:20220,node0:20330,node0:0,node0:8010|
 *     1,node1:20110,node1:20220,node1:20330,node1:0,node1:8010|
 *     2,node2:20110,node2:20220,node2:20330,node2:0,node2:8010
 * </pre>
 *
 * <h3>端口约定（可通过 env 覆盖）</h3>
 * <pre>
 *   20110 = Cluster ingress（客户端下单入口）
 *   20220 = Member-to-member 通信
 *   20330 = Raft log 复制
 *   8010  = Archive 控制端口
 * </pre>
 */
@Slf4j
public class MatchClusterNode implements AutoCloseable {

    private ClusteredMediaDriver  clusteredMediaDriver;
    private ClusteredServiceContainer serviceContainer;

    /**
     * 启动节点。
     *
     * @param service 已实例化的 {@link MatchClusteredService}（由 Spring 构造并注入依赖）
     */
    public void start(MatchClusteredService service) {
        final int    nodeId         = intEnv("CLUSTER_NODE_ID",  0);
        final String clusterMembers = strEnv("CLUSTER_MEMBERS",
                "0,localhost:20110,localhost:20220,localhost:20330,localhost:0,localhost:8010");
        final String aeronDir       = strEnv("AERON_DIR",        "aeron-match-driver");
        final String archiveDir     = strEnv("ARCHIVE_DIR",      "aeron-match-archive");
        final String clusterDir     = strEnv("CLUSTER_DIR",      "aeron-match-cluster");

        log.info("[MatchClusterNode] Starting — nodeId={}, members={}", nodeId, clusterMembers);
        log.info("[MatchClusterNode] dirs: aeron={}, archive={}, cluster={}",
                aeronDir, archiveDir, clusterDir);

        // ── 1. MediaDriver Context ────────────────────────────────────────────
        MediaDriver.Context driverCtx = new MediaDriver.Context()
                .aeronDirectoryName(aeronDir)
                .threadingMode(ThreadingMode.DEDICATED)
                .termBufferSparseFile(false)
                .dirDeleteOnStart(true)
                .dirDeleteOnShutdown(true);

        // ── 2. Archive Context ────────────────────────────────────────────────
        Archive.Context archiveCtx = new Archive.Context()
                .archiveDir(new File(archiveDir))
                .aeronDirectoryName(aeronDir)
                .controlChannel("aeron:udp?endpoint=localhost:8010")
                .replicationChannel("aeron:udp?endpoint=localhost:0")
                .threadingMode(ArchiveThreadingMode.SHARED)
                .deleteArchiveOnStart(false); // 保留历史录制，用于重启恢复

        // ── 3. ConsensusModule Context ────────────────────────────────────────
        ConsensusModule.Context consensusCtx = new ConsensusModule.Context()
                .clusterMemberId(nodeId)
                .clusterMembers(clusterMembers)
                .clusterDir(new File(clusterDir))
                .aeronDirectoryName(aeronDir)
                // archiveContext 需要客户端 AeronArchive.Context（连接本地嵌入式 Archive），
                // 不是服务端 Archive.Context。controlResponseChannel 用 :0 取临时端口。
                .archiveContext(new AeronArchive.Context()
                        .controlRequestChannel("aeron:udp?endpoint=localhost:8010")
                        .controlResponseChannel("aeron:udp?endpoint=localhost:0")
                        .aeronDirectoryName(aeronDir))
                .ingressChannel("aeron:udp?term-length=64k")
                .logChannel("aeron:udp?term-length=256k|reliable=true");

        // ── 4. 启动 ClusteredMediaDriver（Driver + Archive + ConsensusModule 一体） ──
        clusteredMediaDriver = ClusteredMediaDriver.launch(driverCtx, archiveCtx, consensusCtx);
        log.info("[MatchClusterNode] ClusteredMediaDriver launched");

        // ── 5. ClusteredServiceContainer（运行 MatchClusteredService） ─────────
        serviceContainer = ClusteredServiceContainer.launch(
                new ClusteredServiceContainer.Context()
                        .aeronDirectoryName(aeronDir)
                        .archiveContext(new AeronArchive.Context()
                                .controlRequestChannel("aeron:udp?endpoint=localhost:8010")
                                .controlResponseChannel("aeron:udp?endpoint=localhost:0")
                                .aeronDirectoryName(aeronDir))
                        .clusterDir(new File(clusterDir))
                        .clusteredService(service));

        log.info("[MatchClusterNode] ClusteredServiceContainer launched — node is ready");
    }

    @Override
    public void close() {
        log.info("[MatchClusterNode] Shutting down");
        if (serviceContainer     != null) serviceContainer.close();
        if (clusteredMediaDriver != null) clusteredMediaDriver.close();
        log.info("[MatchClusterNode] Shutdown complete");
    }

    // ── helpers ──────────────────────────────────────────────────────────────

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
