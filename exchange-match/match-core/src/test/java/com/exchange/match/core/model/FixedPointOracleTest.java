package com.exchange.match.core.model;

import com.exchange.common.math.FixedPoint;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 定点 ↔ BigDecimal 对拍测试(设计文档 §8.4 收口项)。
 *
 * <p>随机大量用例,分别用「BigDecimal 全精度 + HALF_UP 量化」与「定点 long 路径
 * ({@link FixedPoint#mulDiv}/{@link FixedPoint#mulScaled}、{@link Symbol#calcAmountRaw}/
 * {@link Symbol#calcFeeRaw})」计算,逐笔断言零偏差。这是撮合定点化敢上线的前提。
 */
public class FixedPointOracleTest {

    /** mulDiv(a,b,den,HALF_UP) 必须与 BigInteger(a*b)/den 的 HALF_UP 结果一致。 */
    @Test
    public void mulDiv_matchesBigDecimal_powersOfTenAndArbitraryDen() {
        Random r = new Random(20240711L);
        long[] pow10 = new long[19];
        pow10[0] = 1; for (int i = 1; i < 19; i++) pow10[i] = pow10[i - 1] * 10;

        int tested = 0;
        for (int i = 0; i < 300_000; i++) {
            long a = randMagnitude(r);
            long b = randMagnitude(r);
            if (r.nextBoolean()) a = -a;
            if (r.nextBoolean()) b = -b;
            long den = r.nextBoolean() ? pow10[r.nextInt(19)] : (randMagnitude(r) + 1); // 10 的幂 + 任意正除数
            RoundingMode rm = RoundingMode.HALF_UP;

            BigInteger prod = BigInteger.valueOf(a).multiply(BigInteger.valueOf(b));
            BigInteger expected = new BigDecimal(prod).divide(new BigDecimal(den), 0, rm).toBigIntegerExact();
            if (expected.bitLength() > 63) {
                // 越界:定点实现应抛异常
                final long fa = a, fb = b, fden = den;
                assertThrows(ArithmeticException.class, () -> FixedPoint.mulDiv(fa, fb, fden, rm));
                continue;
            }
            assertEquals(expected.longValueExact(), FixedPoint.mulDiv(a, b, den, rm),
                    () -> "mulDiv mismatch");
            tested++;
        }
        assertTrue(tested > 200_000, "覆盖用例过少: " + tested);
    }

    /** mulScaled 覆盖 exp<=0(缩小) 与 exp>0(放大) 两向。 */
    @Test
    public void mulScaled_matchesBigDecimal() {
        Random r = new Random(7L);
        int tested = 0;
        for (int i = 0; i < 300_000; i++) {
            int sA = r.nextInt(11), sB = r.nextInt(11), sR = r.nextInt(11);
            long a = r.nextInt(1_000_000_000);
            long b = r.nextInt(1_000_000_000);
            BigDecimal expected = new BigDecimal(a).movePointLeft(sA)
                    .multiply(new BigDecimal(b).movePointLeft(sB))
                    .movePointRight(sR)
                    .setScale(0, RoundingMode.HALF_UP);
            if (expected.abs().toBigInteger().bitLength() > 63) continue;
            assertEquals(expected.longValueExact(),
                    FixedPoint.mulScaled(a, b, sA, sB, sR, RoundingMode.HALF_UP));
            tested++;
        }
        assertTrue(tested > 200_000);
    }

    /** Symbol.calcAmountRaw / calcFeeRaw 与 BigDecimal(price*qty, amount*rate) 全等。 */
    @Test
    public void symbolAmountAndFee_matchBigDecimal() {
        Random r = new Random(2024L);
        int tested = 0;
        for (int i = 0; i < 200_000; i++) {
            int pS = r.nextInt(9);          // priceScale 0..8
            int bS = r.nextInt(9);          // baseScale  0..8
            int qS = 2 + r.nextInt(7);      // quoteScale 2..8

            // 生成量化到各自 scale 的价/量
            BigDecimal price = new BigDecimal(1 + r.nextInt(100_000_000)).movePointLeft(r.nextInt(pS + 1))
                    .setScale(pS, RoundingMode.DOWN);
            BigDecimal qty = new BigDecimal(1 + r.nextInt(10_000_000)).movePointLeft(r.nextInt(bS + 1))
                    .setScale(bS, RoundingMode.DOWN);
            BigDecimal feeRate = new BigDecimal(r.nextInt(50_001)).movePointLeft(8); // 0..0.0005

            Symbol sym = new Symbol();
            sym.setPricePrecision(pS);
            sym.setQuantityPrecision(bS);
            sym.setQuoteScale(qS);
            sym.setFeeRate(feeRate);

            long priceRaw = FixedPoint.fromBigDecimal(price, pS, RoundingMode.DOWN);
            long qtyRaw   = FixedPoint.fromBigDecimal(qty, bS, RoundingMode.DOWN);

            long amountRaw;
            try {
                amountRaw = sym.calcAmountRaw(priceRaw, qtyRaw);
            } catch (ArithmeticException overflow) {
                continue;
            }
            long feeRaw = sym.calcFeeRaw(amountRaw);

            BigDecimal amountOracle = price.multiply(qty).setScale(qS, RoundingMode.HALF_UP);
            BigDecimal feeOracle    = amountOracle.multiply(feeRate).setScale(qS, RoundingMode.HALF_UP);

            assertEquals(amountOracle, FixedPoint.toBigDecimal(amountRaw, qS),
                    () -> "amount mismatch price=" + price + " qty=" + qty + " qS=" + qS);
            assertEquals(feeOracle, FixedPoint.toBigDecimal(feeRaw, qS),
                    () -> "fee mismatch");
            tested++;
        }
        assertTrue(tested > 150_000, "覆盖用例过少: " + tested);
    }

    /** 越界结果必须 fail-closed 抛异常,绝不静默回绕。 */
    @Test
    public void overflow_failsClosed() {
        assertThrows(ArithmeticException.class,
                () -> FixedPoint.mulDiv(900_000_000_000_000_000L, 900_000_000_000_000_000L, 1L, RoundingMode.HALF_UP));
    }

    /** 变化量级随机数(0..~1e18)。 */
    private static long randMagnitude(Random r) {
        int mag = r.nextInt(19);
        long base = 1;
        for (int i = 0; i < mag; i++) base *= 10;
        return base <= 1 ? 0 : Math.floorMod(r.nextLong(), base);
    }
}
