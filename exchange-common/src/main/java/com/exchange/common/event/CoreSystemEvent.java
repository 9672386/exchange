package com.exchange.common.event;

/**
 * 跨模块通用系统事件。
 *
 * <p>业务专属事件请在各自模块定义枚举实现 {@link SystemEvent},不要往这里堆。
 */
public enum CoreSystemEvent implements SystemEvent {

    // ── 请求准入 ─────────────────────────────────────────────────────────────

    /**
     * 请求因超过有效期被拒绝。
     *
     * <p><b>期望恒为 0。</b>持续非零说明合法请求正在被误杀——
     * 通常是 Ingress 排队延迟接近有效期窗口,应考虑调大窗口或排查积压。
     */
    REQUEST_EXPIRED(Severity.WARN, "请求超过有效期被拒绝", true),

    /**
     * 请求参数非法被拒绝。
     *
     * <p>网关侧应已拦截,若在状态机侧出现,说明网关校验有遗漏。
     */
    REQUEST_INVALID(Severity.WARN, "请求参数非法被拒绝", true),

    // ── 幂等 ────────────────────────────────────────────────────────────────

    /**
     * 幂等命中,请求被安全跳过。
     *
     * <p>非零是正常的(上游重试),但突增可能意味着上游重试风暴。
     */
    IDEMPOTENT_HIT(Severity.INFO, "幂等命中,跳过重复请求", false),

    // ── 消息传输 ────────────────────────────────────────────────────────────

    /**
     * 响应回包被丢弃(反压超限或连接不可用)。
     *
     * <p>不影响账本正确性(状态已提交),但调用方会超时。
     */
    EGRESS_DROPPED(Severity.WARN, "Egress 回包丢弃", true),

    /**
     * 状态变更事件发布失败,已丢弃。
     *
     * <p><b>严重:</b>意味着 DB 将落后于内存状态,必须触发对账或按 seq 连续性排查。
     */
    EVENT_PUBLISH_DROPPED(Severity.ERROR, "状态变更事件发布丢弃", true),

    /**
     * 检测到事件序号不连续,存在丢失。
     *
     * <p><b>严重:</b>持久层发现 seq 跳号,该区间的资金变动未落库。
     */
    EVENT_SEQ_GAP(Severity.ERROR, "事件序号不连续,存在丢失", true),

    // ── 快照与恢复 ──────────────────────────────────────────────────────────

    /** 快照生成完成。 */
    SNAPSHOT_TAKEN(Severity.INFO, "快照生成完成", false),

    /** 快照生成失败。 */
    SNAPSHOT_FAILED(Severity.ERROR, "快照生成失败", true),

    /** 从快照恢复完成。 */
    SNAPSHOT_RESTORED(Severity.INFO, "从快照恢复完成", false),

    // ── 集群 ────────────────────────────────────────────────────────────────

    /** 节点角色变更(Leader 切换)。 */
    CLUSTER_ROLE_CHANGED(Severity.WARN, "集群角色变更", false),

    /** 上游连接不可用。 */
    UPSTREAM_UNAVAILABLE(Severity.ERROR, "上游连接不可用", true);

    private final Severity severity;
    private final String   description;
    private final boolean  expectZero;

    CoreSystemEvent(Severity severity, String description, boolean expectZero) {
        this.severity    = severity;
        this.description = description;
        this.expectZero  = expectZero;
    }

    @Override public String   code()        { return name(); }
    @Override public Severity severity()    { return severity; }
    @Override public String   description() { return description; }
    @Override public boolean  expectZero()  { return expectZero; }
}
