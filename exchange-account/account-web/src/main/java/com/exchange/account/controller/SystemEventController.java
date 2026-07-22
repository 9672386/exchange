package com.exchange.account.controller;

import com.exchange.common.event.SystemEventReporter;
import com.exchange.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 系统事件计数查询接口（运维 / 监控用）。
 *
 * <p>暴露 {@link SystemEventReporter} 的累计计数，供 Prometheus 抓取或人工排查。
 * 计数是节点本地的观测数据，重启清零。
 */
@Tag(name = "SystemEvent", description = "系统事件计数（运维）")
@RestController
@RequestMapping("/api/system-event")
@RequiredArgsConstructor
public class SystemEventController {

    private final SystemEventReporter eventReporter;

    /**
     * 查询全部事件计数，按累计值倒序。
     */
    @Operation(summary = "查询系统事件计数")
    @GetMapping("/stats")
    public ApiResponse<List<SystemEventReporter.EventStat>> stats() {
        return ApiResponse.success(eventReporter.stats());
    }

    /**
     * 健康检查：是否出现了「期望恒为 0」的事件。
     *
     * <p>返回 {@code healthy=false} 表示系统出现了不应发生的情况
     * （请求被误杀、事件丢弃、快照失败等），应立即排查。
     * 监控可直接对该字段告警，无需为每个事件单独设阈值。
     */
    @Operation(summary = "异常事件健康检查")
    @GetMapping("/health")
    public ApiResponse<Map<String, Object>> health() {
        boolean hasUnexpected = eventReporter.hasUnexpectedEvents();
        List<SystemEventReporter.EventStat> abnormal = eventReporter.stats().stream()
                .filter(s -> s.expectZero() && s.total() > 0)
                .toList();

        Map<String, Object> body = new HashMap<>();
        body.put("healthy", !hasUnexpected);
        body.put("abnormalEvents", abnormal);
        return ApiResponse.success(body);
    }
}
