package com.exchange.common.math;

import java.util.Map;
import java.util.TreeMap;

/**
 * 资产精度(scale)注册表:asset → 小数位数。
 *
 * <p>定点 long 的 raw 值本身不含精度信息,它代表多少真实金额<b>完全</b>取决于该资产的 scale。
 * 因此本注册表是资金/撮合状态机正确性的根基,必须满足:
 *
 * <ol>
 *   <li><b>不可变</b>:构造后只读,运行期不改(见 {@code Map.copyOf})。</li>
 *   <li><b>各副本一致</b>:scale 表随部署制品分发,不依赖运行期外部查询;
 *       任何副本间的 scale 差异都会导致同一 raw 代表不同金额 → 状态分裂。</li>
 *   <li><b>fail-closed</b>:{@link #scaleOf} 遇未登记资产直接抛异常,
 *       绝不用默认值兜底——默认值会让不同配置的副本静默产生不同 raw。</li>
 *   <li><b>可检测</b>:{@link #fingerprint} 写入快照,加载时比对,冲突则拒绝启动。</li>
 * </ol>
 */
public final class AssetScaleRegistry {

    /** 不可变 asset → scale(uniform 模式下为空)。 */
    private final Map<String, Integer> scales;

    /** uniform 模式:任意资产统一 scale;>=0 表示启用,-1 表示按 {@link #scales} 查表。 */
    private final int uniformScale;

    /** 指纹缓存(排序后 asset:scale 拼接的 hash),用于快照一致性检测。 */
    private final String fingerprint;

    public AssetScaleRegistry(Map<String, Integer> scales) {
        if (scales == null || scales.isEmpty()) {
            throw new IllegalArgumentException("AssetScaleRegistry must be non-empty");
        }
        for (Map.Entry<String, Integer> e : scales.entrySet()) {
            Integer s = e.getValue();
            if (s == null || s < 0 || s > 18) {
                throw new IllegalArgumentException(
                        "Illegal scale for asset " + e.getKey() + ": " + s + " (must be 0..18)");
            }
        }
        this.scales = Map.copyOf(scales);
        this.uniformScale = -1;
        this.fingerprint = computeFingerprint(this.scales);
    }

    private AssetScaleRegistry(int uniformScale) {
        if (uniformScale < 0 || uniformScale > 18) {
            throw new IllegalArgumentException("uniform scale must be 0..18: " + uniformScale);
        }
        this.scales = Map.of();
        this.uniformScale = uniformScale;
        this.fingerprint = "uniform:" + uniformScale;
    }

    /**
     * 统一 scale 注册表:任意资产都用同一精度。
     *
     * <p>这是"单一全局 scale"方案的合法实现(如全平台 1e-8),也用于测试便捷构造。
     * 它<b>不</b> fail-closed(任意资产恒有 scale),仅当业务确实使用统一精度时选用。
     */
    public static AssetScaleRegistry uniform(int scale) {
        return new AssetScaleRegistry(scale);
    }

    /**
     * 取资产 scale。查表模式下未登记 → 抛异常(fail-closed);uniform 模式恒返回统一值。
     */
    public int scaleOf(String asset) {
        if (uniformScale >= 0) {
            return uniformScale;
        }
        Integer s = scales.get(asset);
        if (s == null) {
            throw new IllegalStateException(
                    "No scale registered for asset='" + asset + "' (fail-closed; register it in AssetScaleRegistry)");
        }
        return s;
    }

    public boolean has(String asset) {
        return uniformScale >= 0 || scales.containsKey(asset);
    }

    /** 只读视图。 */
    public Map<String, Integer> asMap() {
        return scales;
    }

    /**
     * scale 表指纹。写入快照,加载时与本地注册表比对;不一致说明副本/版本 scale 配置分歧,
     * 必须拒绝启动而非静默错账。
     */
    public String fingerprint() {
        return fingerprint;
    }

    private static String computeFingerprint(Map<String, Integer> scales) {
        // 排序保证与插入顺序无关,确定性
        StringBuilder sb = new StringBuilder();
        new TreeMap<>(scales).forEach((asset, scale) -> sb.append(asset).append(':').append(scale).append(';'));
        return Integer.toHexString(sb.toString().hashCode()) + "#" + scales.size();
    }
}
