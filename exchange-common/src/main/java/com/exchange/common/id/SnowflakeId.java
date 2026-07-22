package com.exchange.common.id;

import lombok.extern.slf4j.Slf4j;

/**
 * 雪花算法（Snowflake）ID 生成器 & 时间戳解析器。
 *
 * <h3>位结构（64 bit signed long，最高位始终为 0）</h3>
 * <pre>
 *   bit 63    : 0（符号位）
 *   bit 22-62 : 41 bit 毫秒时间戳（相对 EPOCH）→ 支持约 69 年
 *   bit 12-21 : 10 bit 机器 ID（0 – 1023）
 *   bit  0-11 : 12 bit 毫秒内序列号（0 – 4095）
 * </pre>
 *
 * <h3>容量</h3>
 * <ul>
 *   <li>单机每毫秒最大 4 096 个 ID。</li>
 *   <li>时钟回拨：自旋等待直到时钟追上，最多等待 5ms，超时抛异常。</li>
 * </ul>
 *
 * <h3>用途</h3>
 * <ul>
 *   <li>撮合引擎：生成 tradeId（{@link #nextIdStr()}）。</li>
 *   <li>订单服务：生成 orderId（{@link #nextIdLong()}）。</li>
 *   <li>资产服务：从 orderId / tradeId 解析生成时间，做 30min 有效期校验。</li>
 *   <li>撮合服务：从 orderId 解析生成时间，做 10s 超时校验。</li>
 * </ul>
 */
@Slf4j
public final class SnowflakeId {

    /** 自定义纪元：2024-01-01T00:00:00.000Z（毫秒）。 */
    public static final long EPOCH = 1_704_067_200_000L;

    private static final int  MACHINE_BITS   = 10;
    private static final int  SEQUENCE_BITS  = 12;
    private static final long MAX_MACHINE_ID = (1L << MACHINE_BITS) - 1;   // 1023
    private static final long MAX_SEQUENCE   = (1L << SEQUENCE_BITS) - 1;  // 4095

    /** 时间戳左移位数（machine + sequence） */
    private static final int TIMESTAMP_SHIFT = MACHINE_BITS + SEQUENCE_BITS; // 22

    /** 机器 ID 左移位数 */
    private static final int MACHINE_SHIFT   = SEQUENCE_BITS; // 12

    /** 时钟回拨最大容忍（ms），超出则抛异常 */
    private static final long MAX_CLOCK_BACKWARD_MS = 5L;

    // ── 实例状态（synchronized 保护多线程并发生成） ─────────────────────────────
    private final long machineId;
    private long lastTimestamp = -1L;
    private long sequence      = 0L;

    // ── 默认单例（machineId = 1，适合单节点部署） ────────────────────────────────
    private static final SnowflakeId DEFAULT = new SnowflakeId(1);

    /**
     * 创建指定机器 ID 的 Snowflake 生成器。
     *
     * @param machineId 机器 ID，范围 [0, 1023]
     */
    public SnowflakeId(long machineId) {
        if (machineId < 0 || machineId > MAX_MACHINE_ID) {
            throw new IllegalArgumentException(
                    "machineId out of range [0, " + MAX_MACHINE_ID + "]: " + machineId);
        }
        this.machineId = machineId;
    }

    // =========================================================================
    // 生成 ID
    // =========================================================================

    /**
     * 生成下一个 Snowflake ID（线程安全）。
     *
     * @return 全局单调递增的 64 bit long ID
     * @throws IllegalStateException 时钟回拨超出容忍阈值
     */
    public synchronized long nextId() {
        long ts = System.currentTimeMillis();

        if (ts < lastTimestamp) {
            long diff = lastTimestamp - ts;
            if (diff > MAX_CLOCK_BACKWARD_MS) {
                throw new IllegalStateException(
                        "[SnowflakeId] Clock moved backward " + diff + "ms, refusing to generate ID");
            }
            // 小幅回拨：自旋等待
            while (ts < lastTimestamp) {
                ts = System.currentTimeMillis();
            }
        }

        if (ts == lastTimestamp) {
            sequence = (sequence + 1) & MAX_SEQUENCE;
            if (sequence == 0) {
                // 当前毫秒序列用尽，等到下一毫秒
                while (ts <= lastTimestamp) {
                    ts = System.currentTimeMillis();
                }
            }
        } else {
            sequence = 0;
        }

        lastTimestamp = ts;
        return ((ts - EPOCH) << TIMESTAMP_SHIFT)
                | (machineId << MACHINE_SHIFT)
                | sequence;
    }

    // =========================================================================
    // 静态便捷方法（使用默认单例）
    // =========================================================================

    /** 使用默认实例生成 Snowflake ID（long）。 */
    public static long nextIdLong() {
        return DEFAULT.nextId();
    }

    /** 使用默认实例生成 Snowflake ID（String 十进制表示）。 */
    public static String nextIdStr() {
        return String.valueOf(DEFAULT.nextId());
    }

    // =========================================================================
    // 时间戳解析
    // =========================================================================

    /**
     * 从 Snowflake long ID 提取生成时间（毫秒时间戳，UTC）。
     *
     * @param id Snowflake ID
     * @return 毫秒时间戳（epoch ms）
     */
    public static long extractTimestampMs(long id) {
        return (id >>> TIMESTAMP_SHIFT) + EPOCH;
    }

    /**
     * 尝试从字符串 ID 提取 Snowflake 生成时间戳。
     *
     * <p>若字符串无法解析为 long（如 UUID 格式），返回 {@code -1}，
     * 调用方应 fallback 到 wall-clock 或 cluster timestamp。
     *
     * @param idStr ID 字符串
     * @return 毫秒时间戳，或 {@code -1}（无法解析）
     */
    public static long tryExtractTimestampMs(String idStr) {
        return tryExtractTimestampMs(idStr, System.currentTimeMillis());
    }

    /**
     * 尝试从字符串 ID 提取 Snowflake 生成时间戳（确定性版本）。
     *
     * <p><b>Raft 状态机内必须使用本重载</b>，以 cluster timestamp 作为参考时间。
     * 无参数版本内部使用 {@code System.currentTimeMillis()}，在日志重放时
     * 与原始执行的判定可能不同，会造成副本间状态分歧。
     *
     * @param idStr       ID 字符串
     * @param referenceMs 合理性校验的参考时间（状态机内传 cluster timestamp）
     * @return 毫秒时间戳，或 {@code -1}（无法解析 / 超出合理范围）
     */
    public static long tryExtractTimestampMs(String idStr, long referenceMs) {
        if (idStr == null || idStr.isEmpty()) return -1L;
        try {
            long id = Long.parseLong(idStr);
            long ts = extractTimestampMs(id);
            // 合理性校验：时间戳必须在 EPOCH 之后且不超过参考时间 + 1 天
            if (ts < EPOCH || ts > referenceMs + 86_400_000L) return -1L;
            return ts;
        } catch (NumberFormatException e) {
            return -1L;
        }
    }

    /**
     * 检查字符串 ID 是否已过期（基于 Snowflake 嵌入的生成时间）。
     *
     * <p>确定性：内部以 {@code nowMs} 作为唯一时间参考，不读 wall-clock，
     * 可安全用于 Raft 状态机（重放时判定与原始执行一致）。
     *
     * @param idStr    Snowflake ID 字符串
     * @param ttlMs    TTL（毫秒）
     * @param nowMs    当前参考时间（使用 cluster timestamp 保证确定性）
     * @return {@code true} = 已超期；{@code false} = 未超期或无法解析（保守放行）
     */
    public static boolean isExpired(String idStr, long ttlMs, long nowMs) {
        long ts = tryExtractTimestampMs(idStr, nowMs);
        if (ts < 0) return false;  // 非 Snowflake 格式：保守放行
        return nowMs - ts > ttlMs;
    }
}
