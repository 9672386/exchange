package com.exchange.match.core.cluster;

import lombok.Getter;
import org.springframework.stereotype.Component;

/**
 * 撮合状态机运行时状态快照（单写多读）。
 *
 * <p>与资产侧 {@code ClusterRuntimeStatus} 同构:由 Cluster Service Thread 单写
 * volatile 字段,HTTP 线程无锁读。<b>不入快照、不参与判定</b>,纯观测。
 *
 * <p>撮合没有资产侧的 seq,其"状态机推进度"由 {@code logPosition}
 * (Raft 日志已应用位点)表征。
 */
@Component
@Getter
public class MatchRuntimeStatus {

    // ── 身份与角色 ────────────────────────────────────────────────
    private volatile String  role     = "UNKNOWN";
    private volatile int     memberId = -1;
    private volatile boolean started  = false;

    // ── 推进度 ────────────────────────────────────────────────────
    /** Raft 日志已应用位点（撮合的"状态机推进度"）。 */
    private volatile long    logPosition = 0L;

    // ── 规模指标（热更新 O(1)）────────────────────────────────────
    private volatile int     symbolCount    = 0;
    private volatile int     orderBookCount = 0;

    // ── 规模指标（冷更新，O(n)，按消息计数抽样）──────────────────
    private volatile long    totalActiveOrders = 0L;
    private volatile int     positionCount     = 0;
    private volatile long    totalTradeCount   = 0L;

    // ── 更新时间（cluster timestamp）──────────────────────────────
    private volatile long    lastHotUpdateClusterTime  = 0L;
    private volatile long    lastColdUpdateClusterTime = 0L;

    // =========================================================================
    // 由 Cluster Service Thread 调用（单写线程）
    // =========================================================================

    public void markStarted(int memberId, String role) {
        this.memberId = memberId;
        this.role     = role;
        this.started  = true;
    }

    public void updateRole(String role) {
        this.role = role;
    }

    /** 热更新：每条消息末尾，仅 O(1) 指标。 */
    public void updateHot(String role, int memberId, long logPosition,
                          int symbolCount, int orderBookCount, long clusterTime) {
        this.role                     = role;
        this.memberId                 = memberId;
        this.logPosition              = logPosition;
        this.symbolCount              = symbolCount;
        this.orderBookCount           = orderBookCount;
        this.lastHotUpdateClusterTime = clusterTime;
    }

    /** 冷更新：抽样触发，O(n) 规模统计。 */
    public void updateCold(long totalActiveOrders, int positionCount, long totalTradeCount,
                           long clusterTime) {
        this.totalActiveOrders         = totalActiveOrders;
        this.positionCount             = positionCount;
        this.totalTradeCount           = totalTradeCount;
        this.lastColdUpdateClusterTime = clusterTime;
    }
}
