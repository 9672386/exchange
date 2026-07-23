package com.exchange.common.math;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 定点数(scaled integer)运算工具。
 *
 * <p>金额/数量用 {@code long raw = round(realValue × 10^scale)} 表示。
 * 同 scale 的两个 raw 加减比较是纯 long 运算;跨 scale 的乘除(如 amount = price × qty)
 * 通过 128 位中间量避免溢出,见 {@link #mulDiv}。
 *
 * <h3>确定性</h3>
 * <p>本类为纯函数、无状态、无挂钟、无随机,同输入必得同输出,可安全用于 Raft 状态机。
 *
 * <h3>fail-closed</h3>
 * <p>任何溢出 long 有效范围的结果一律抛 {@link ArithmeticException},绝不静默回绕——
 * 资金宁可拒绝也不可错账。
 */
public final class FixedPoint {

    private FixedPoint() {}

    /** 10 的幂查表(0..18),超范围抛异常。 */
    private static final long[] POW10 = new long[19];
    static {
        long p = 1L;
        for (int i = 0; i < POW10.length; i++) { POW10[i] = p; if (i < 18) p *= 10L; }
    }

    /** {@code 10^n},n∈[0,18]。 */
    public static long pow10(int n) {
        if (n < 0 || n > 18) throw new IllegalArgumentException("pow10 out of range: " + n);
        return POW10[n];
    }

    // =====================================================================
    // BigDecimal ↔ 定点 long 边界转换
    // =====================================================================

    /**
     * {@code BigDecimal → long raw}(边界入口)。
     *
     * @param v     真实值
     * @param scale 目标资产精度
     * @param rm    多余精度的舍入模式(通常 DOWN 或 HALF_UP,由业务定)
     * @throws ArithmeticException 超出 long 范围(fail-closed)
     */
    public static long fromBigDecimal(BigDecimal v, int scale, RoundingMode rm) {
        if (v == null) throw new IllegalArgumentException("value is null");
        return v.movePointRight(scale).setScale(0, rm).longValueExact();
    }

    /** {@code long raw → BigDecimal}(边界出口),精确无损。 */
    public static BigDecimal toBigDecimal(long raw, int scale) {
        return BigDecimal.valueOf(raw, scale);
    }

    /**
     * 同资产不同 scale 的 raw 换算(升/降精度)。降精度按 {@code rm} 舍入。
     */
    public static long rescale(long raw, int fromScale, int toScale, RoundingMode rm) {
        if (toScale >= fromScale) {
            return Math.multiplyExact(raw, pow10(toScale - fromScale));
        }
        return mulDiv(raw, 1L, pow10(fromScale - toScale), rm);
    }

    /**
     * 跨 scale 定点乘法:{@code round( (a/10^scaleA) × (b/10^scaleB) × 10^scaleResult )}。
     *
     * <p>用于成交金额 {@code amount = price × quantity}:
     * {@code mulScaled(priceRaw, qtyRaw, priceScale, baseScale, quoteScale, HALF_UP)}。
     *
     * <p>令 {@code exp = scaleResult - scaleA - scaleB}:
     * <ul>
     *   <li>{@code exp <= 0}(结果更粗,常见):{@code a*b / 10^(-exp)},走 {@link #mulDiv} 带舍入。</li>
     *   <li>{@code exp > 0}(结果更细):{@code a*b * 10^exp},精确(无需舍入),经 128 位中间量并检查溢出。</li>
     * </ul>
     *
     * @throws ArithmeticException 溢出 long,或 scale 配置过极端({@code |exp|>18})
     */
    public static long mulScaled(long a, long b, int scaleA, int scaleB, int scaleResult, RoundingMode rm) {
        int exp = scaleResult - scaleA - scaleB;
        if (exp <= 0) {
            if (-exp > 18) throw new ArithmeticException("scale config too extreme: exp=" + exp);
            return mulDiv(a, b, pow10(-exp), rm);
        }
        if (exp > 18) throw new ArithmeticException("scale config too extreme: exp=" + exp);
        long factor = pow10(exp);
        // a*b*factor,精确放大;把 factor 折进某一操作数以复用 128 位 mulDiv(_,_,1)
        try {
            return mulDiv(Math.multiplyExact(a, factor), b, 1L, rm);
        } catch (ArithmeticException e) {
            return mulDiv(a, Math.multiplyExact(b, factor), 1L, rm); // 仍溢出则确为越界,抛出
        }
    }

    // =====================================================================
    // 核心:128 位中间量的 a*b/den
    // =====================================================================

    /**
     * 计算 {@code a * b / den},{@code a*b} 用 128 位无符号中间量,按 {@code rm} 舍入,
     * 结果落 long。{@code a}、{@code b} 可为任意符号,{@code den > 0}。
     *
     * <p>用途:
     * <ul>
     *   <li>成交金额 {@code amount = mulDiv(price, qty, 10^(sp+sq-sa), rm)}(当 sp+sq≥sa)</li>
     *   <li>手续费   {@code fee    = mulDiv(amount, feeRateRaw, 10^feeScale, rm)}</li>
     *   <li>均价/PnL 除法 {@code mulDiv(a, b, den, rm)}</li>
     * </ul>
     *
     * @throws ArithmeticException 溢出 long(fail-closed)
     */
    public static long mulDiv(long a, long b, long den, RoundingMode rm) {
        if (den <= 0) throw new IllegalArgumentException("den must be > 0: " + den);
        int sign = 1;
        long ua = a, ub = b;
        if (ua < 0) { sign = -sign; ua = -ua; }
        if (ub < 0) { sign = -sign; ub = -ub; }
        if (ua < 0 || ub < 0) throw new ArithmeticException("operand at Long.MIN not supported");

        // 128 位无符号积 hi:lo = ua * ub(两操作数均非负,multiplyHigh 高位即无符号高位)
        long lo = ua * ub;
        long hi = Math.multiplyHigh(ua, ub);

        // 无符号 128 ÷ 64 → 商(qHi:qLo) + 余数 rem
        long remainder = 0L, qHi = 0L, qLo = 0L;
        for (int i = 127; i >= 0; i--) {
            long bit = (i >= 64) ? ((hi >>> (i - 64)) & 1L) : ((lo >>> i) & 1L);
            remainder = (remainder << 1) | bit;            // rem < den < 2^63 → <<1 后仍 < 2^64
            qHi = (qHi << 1) | (qLo >>> 63);
            qLo = qLo << 1;
            if (Long.compareUnsigned(remainder, den) >= 0) {
                remainder -= den;
                qLo |= 1L;
            }
        }

        // 舍入增量(基于 |商| 与余数)
        long magInc = roundingIncrement(rm, sign, remainder, den, qLo);
        if (magInc != 0) {
            long nQLo = qLo + magInc;
            if (Long.compareUnsigned(nQLo, qLo) < 0) qHi += 1; // 进位
            qLo = nQLo;
        }

        // |商| 必须落 [0, Long.MAX]
        if (qHi != 0 || qLo < 0) throw new ArithmeticException("fixed-point overflow");
        return sign < 0 ? -qLo : qLo;
    }

    /**
     * 舍入增量(作用于商的绝对值 qLo)。rem∈[0,den),den>0。
     * sign 为最终结果符号,用于 FLOOR/CEILING 的方向判定。
     */
    private static long roundingIncrement(RoundingMode rm, int sign, long rem, long den, long qLo) {
        if (rem == 0) return 0;
        switch (rm) {
            case DOWN:      return 0;
            case UP:        return 1;
            case FLOOR:     return sign < 0 ? 1 : 0;   // 向 -∞:负数远离零
            case CEILING:   return sign < 0 ? 0 : 1;   // 向 +∞:正数远离零
            case HALF_UP:   return (rem >= den - rem) ? 1 : 0;       // rem*2 >= den
            case HALF_DOWN: return (rem > den - rem) ? 1 : 0;        // rem*2 >  den
            case HALF_EVEN:
                if (rem > den - rem) return 1;
                if (rem < den - rem) return 0;
                return (qLo & 1L) == 1L ? 1 : 0;       // 恰好一半 → 趋偶
            case UNNECESSARY:
                throw new ArithmeticException("Rounding necessary");
            default:
                throw new IllegalArgumentException("Unsupported RoundingMode: " + rm);
        }
    }
}
