package com.exchange.match.core.controller;

import com.exchange.common.event.SystemEventReporter;
import com.exchange.common.response.ApiResponse;
import com.exchange.match.core.cluster.MatchRuntimeStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 撮合状态机运行时视图（运维 / 监控）。
 *
 * <p>与资产侧 {@code /api/cluster/status} 同构。数据来自 {@link MatchRuntimeStatus}
 * 的 volatile 快照，由 Cluster Service Thread 单写，<b>不经过 Raft 日志</b>。
 * 返回本节点视角，多节点分别请求即可发现 Follower 落后。
 */
@Tag(name = "MatchClusterStatus", description = "撮合状态机运行时视图（运维）")
@RestController
@RequestMapping("/api/match/cluster")
@RequiredArgsConstructor
public class MatchClusterStatusController {

    private final MatchRuntimeStatus  status;
    private final SystemEventReporter eventReporter;

    /**
     * 状态机运行时视图。
     *
     * <p>{@code logPosition} 是撮合的"状态机推进度"(Raft 日志已应用位点),
     * 对应资产侧的 seq;{@code totalActiveOrders/positionCount/totalTradeCount}
     * 为抽样刷新的规模统计。
     */
    @Operation(summary = "查询撮合状态机运行时视图（本节点）")
    @GetMapping("/status")
    public ApiResponse<Map<String, Object>> status() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("started",                   status.isStarted());
        m.put("role",                      status.getRole());
        m.put("memberId",                  status.getMemberId());
        m.put("logPosition",               status.getLogPosition());
        m.put("symbolCount",               status.getSymbolCount());
        m.put("orderBookCount",            status.getOrderBookCount());
        m.put("totalActiveOrders",         status.getTotalActiveOrders());
        m.put("positionCount",             status.getPositionCount());
        m.put("totalTradeCount",           status.getTotalTradeCount());
        m.put("lastHotUpdateClusterTime",  status.getLastHotUpdateClusterTime());
        m.put("lastColdUpdateClusterTime", status.getLastColdUpdateClusterTime());
        return ApiResponse.success(m);
    }

    /**
     * 综合健康检查:节点已启动且无"期望恒为 0"的异常事件。
     */
    @Operation(summary = "撮合状态机健康检查")
    @GetMapping("/health")
    public ApiResponse<Map<String, Object>> health() {
        boolean hasUnexpected = eventReporter.hasUnexpectedEvents();
        boolean healthy = status.isStarted() && !hasUnexpected;

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("healthy",             healthy);
        m.put("started",             status.isStarted());
        m.put("role",                status.getRole());
        m.put("logPosition",         status.getLogPosition());
        m.put("hasUnexpectedEvents", hasUnexpected);
        m.put("abnormalEvents",      eventReporter.stats().stream()
                .filter(s -> s.expectZero() && s.total() > 0)
                .toList());
        return ApiResponse.success(m);
    }
}
