package com.exchange.account.controller;

import com.exchange.account.core.cluster.ClusterRuntimeStatus;
import com.exchange.common.event.SystemEventReporter;
import com.exchange.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Asset Cluster 状态机运行时视图（运维 / 监控）。
 *
 * <h3>数据来源</h3>
 * <p>读取 {@link ClusterRuntimeStatus} 的 volatile 快照，由 Cluster Service Thread 单写。
 * <b>不经过 Raft 日志</b>——查询是纯读，绝不写入或重放日志。
 *
 * <h3>每节点视角</h3>
 * <p>返回的是"本节点"视角：role 是本节点角色，seq/position 是本节点已应用的值。
 * 对每个节点分别请求，即可发现节点间不一致（如某 Follower 落后）。
 */
@Tag(name = "ClusterStatus", description = "资产状态机运行时视图（运维）")
@RestController
@RequestMapping("/api/cluster")
@RequiredArgsConstructor
public class ClusterStatusController {

    private final ClusterRuntimeStatus  status;
    private final SystemEventReporter    eventReporter;

    /**
     * 状态机运行时视图。
     *
     * <p>字段:
     * <ul>
     *   <li>{@code started/role/memberId} — 节点身份与角色</li>
     *   <li>{@code seq} — 自创世产出的资金流水总数（状态机产出编号）</li>
     *   <li>{@code matchArchivePosition} — 已消费的成交 Archive 位点</li>
     *   <li>{@code userCount/ledgerEntryCount} — 账本规模</li>
     *   <li>{@code idempotentSize/permanentIdempotentSize} — 幂等表大小</li>
     *   <li>{@code lastHot/ColdUpdateClusterTime} — 各指标最近刷新的 cluster 时间，判断新鲜度</li>
     * </ul>
     */
    @Operation(summary = "查询状态机运行时视图（本节点）")
    @GetMapping("/status")
    public ApiResponse<Map<String, Object>> status() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("started",                  status.isStarted());
        m.put("role",                     status.getRole());
        m.put("memberId",                 status.getMemberId());
        m.put("seq",                      status.getSeq());
        m.put("matchArchivePosition",     status.getMatchArchivePosition());
        m.put("userCount",                status.getUserCount());
        m.put("ledgerEntryCount",         status.getLedgerEntryCount());
        m.put("idempotentSize",           status.getIdempotentSize());
        m.put("permanentIdempotentSize",  status.getPermanentIdempotentSize());
        m.put("lastHotUpdateClusterTime", status.getLastHotUpdateClusterTime());
        m.put("lastColdUpdateClusterTime",status.getLastColdUpdateClusterTime());
        return ApiResponse.success(m);
    }

    /**
     * 综合健康检查:合并运行时状态与异常事件。
     *
     * <p>{@code healthy=false} 的判据:节点未启动,或出现了"期望恒为 0"的系统事件
     * (请求被误杀、事件丢弃、快照失败等)。监控可直接对该字段告警。
     */
    @Operation(summary = "状态机健康检查")
    @GetMapping("/health")
    public ApiResponse<Map<String, Object>> health() {
        boolean hasUnexpected = eventReporter.hasUnexpectedEvents();
        boolean healthy = status.isStarted() && !hasUnexpected;

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("healthy",         healthy);
        m.put("started",         status.isStarted());
        m.put("role",            status.getRole());
        m.put("seq",             status.getSeq());
        m.put("hasUnexpectedEvents", hasUnexpected);
        m.put("abnormalEvents",  eventReporter.stats().stream()
                .filter(s -> s.expectZero() && s.total() > 0)
                .toList());
        return ApiResponse.success(m);
    }
}
