package com.exchange.account.core.cluster;

import lombok.Getter;
import org.springframework.stereotype.Component;

/**
 * Asset Cluster 运行时状态快照（单写多读）。
 *
 * <h3>为什么需要它</h3>
 * <p>{@link BalanceLedger} 的状态活在 Cluster Service Thread 上，是普通 {@code HashMap}。
 * HTTP 线程直接读取会产生数据竞争，且查询绝不能走 Raft 日志（那是写操作、会被复制和重放）。
 *
 * <p>因此由 Service Thread 在安全时机（每条消息处理末尾、角色变更、定时器）
 * 把只读指标写入本类的 {@code volatile} 字段，HTTP 线程读取这些字段——
 * 单写单读 + volatile，无锁、无竞争。读到的是"最近一次更新时"的值，
 * 对监控完全够用。
 *
 * <h3>不是状态机状态</h3>
 * <p>本类不入快照、不参与任何判定，纯观测。各副本的值反映各自视角，
 * 正好用来发现节点间不一致（如某 Follower 落后）。
 */
@Component
@Getter
public class ClusterRuntimeStatus {

    // ── 节点身份与角色（每条消息 / 角色变更时更新）──────────────────
    private volatile String  role       = "UNKNOWN";
    private volatile int     memberId   = -1;
    private volatile boolean started    = false;

    // ── 状态机产出与位点（每条消息末尾更新，O(1)）─────────────────
    private volatile long    seq                  = 0L;
    private volatile long    matchArchivePosition = 0L;

    // ── 规模指标 ──────────────────────────────────────────────────
    /** 用户数，O(1)，热更新。 */
    private volatile int     userCount               = 0;
    /** 幂等 TTL 表大小，O(1)，热更新。 */
    private volatile int     idempotentSize          = 0;
    /** 永久幂等表大小，O(1)，热更新。 */
    private volatile int     permanentIdempotentSize = 0;
    /** 账本条目总数 (userId×accountType×asset)，O(n)，仅定时器冷更新。 */
    private volatile long    ledgerEntryCount        = 0L;

    // ── 更新时间（cluster timestamp，便于判断数据新鲜度）──────────
    private volatile long    lastHotUpdateClusterTime  = 0L;
    private volatile long    lastColdUpdateClusterTime = 0L;

    // =========================================================================
    // 由 Cluster Service Thread 调用的写入方法（单写线程）
    // =========================================================================

    public void markStarted(int memberId, String role) {
        this.memberId = memberId;
        this.role     = role;
        this.started  = true;
    }

    public void updateRole(String role) {
        this.role = role;
    }

    /**
     * 热更新：每条消息处理末尾调用，仅写 O(1) 指标。
     */
    public void updateHot(String role, int memberId, long seq, long matchArchivePosition,
                          int userCount, int idempotentSize, int permanentIdempotentSize,
                          long clusterTime) {
        this.role                    = role;
        this.memberId                = memberId;
        this.seq                     = seq;
        this.matchArchivePosition    = matchArchivePosition;
        this.userCount               = userCount;
        this.idempotentSize          = idempotentSize;
        this.permanentIdempotentSize = permanentIdempotentSize;
        this.lastHotUpdateClusterTime = clusterTime;
    }

    /**
     * 冷更新：定时器（默认 60s）调用，计算 O(n) 的账本条目总数。
     */
    public void updateCold(long ledgerEntryCount, long clusterTime) {
        this.ledgerEntryCount          = ledgerEntryCount;
        this.lastColdUpdateClusterTime = clusterTime;
    }
}
