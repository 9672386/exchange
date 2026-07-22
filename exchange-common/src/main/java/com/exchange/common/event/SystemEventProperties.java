package com.exchange.common.event;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 系统事件上报配置。
 *
 * <h3>配置示例</h3>
 * <pre>
 * system-event:
 *   enabled: true
 *   default-policy:
 *     log-every-n: 100          # 每累计 100 次输出一条
 *     log-interval-ms: 60000    # 或距上次输出满 60s 输出一条(两者先到先触发)
 *   policies:
 *     REQUEST_EXPIRED:
 *       log-every-n: 1          # 期望恒为 0 的事件,每次都输出
 *       log-interval-ms: 0
 *     IDEMPOTENT_HIT:
 *       log-every-n: 1000       # 高频事件,粗粒度采样
 *       log-interval-ms: 300000
 *     EVENT_PUBLISH_DROPPED:
 *       log-every-n: 1
 *       log-interval-ms: 0
 * </pre>
 *
 * <h3>阈值语义</h3>
 * <ul>
 *   <li>{@code log-every-n <= 0} —— 关闭"按次数"触发</li>
 *   <li>{@code log-interval-ms <= 0} —— 关闭"按时间"触发</li>
 *   <li>两者都关闭 —— 该事件只计数,永不输出日志</li>
 *   <li>两者都开启 —— 先到先触发,触发后两个计时/计数同时重置</li>
 * </ul>
 *
 * <p>{@code policies} 的 key 为事件 {@link SystemEvent#code()},大小写敏感。
 * 未配置的事件使用 {@code default-policy}。
 */
@Data
@ConfigurationProperties(prefix = "system-event")
public class SystemEventProperties {

    /** 总开关。关闭后 record() 仍计数(开销极低),但不输出任何日志。 */
    private boolean enabled = true;

    /** 默认节流策略。 */
    private Policy defaultPolicy = new Policy();

    /** 按事件 code 覆盖的策略。 */
    private Map<String, Policy> policies = new LinkedHashMap<>();

    /**
     * 解析某事件适用的策略(未配置则回落到默认)。
     */
    public Policy resolve(String code) {
        Policy p = policies.get(code);
        return p != null ? p : defaultPolicy;
    }

    @Data
    public static class Policy {

        /** 每累计 N 次输出一条日志;{@code <= 0} 表示不按次数触发。 */
        private long logEveryN = 100L;

        /** 距上次输出满 T 毫秒输出一条;{@code <= 0} 表示不按时间触发。 */
        private long logIntervalMs = 60_000L;

        public boolean countTriggerEnabled() {
            return logEveryN > 0;
        }

        public boolean timeTriggerEnabled() {
            return logIntervalMs > 0;
        }
    }
}
