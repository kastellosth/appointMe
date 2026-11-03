package com.kastellosth.appointme.support;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * The ONE place that decides how money is rounded. See docs/invoices.md, "Rounding".
 *
 * Strategy: tax is calculated PER LINE and rounded half-up to cents; the invoice tax total is the
 * sum of the already-rounded line taxes. So every number printed on the invoice adds up exactly.
 */
public final class Money {

    /** Cents. */
    public static final int SCALE = 2;
    /** 0.005 -> 0.01. Amounts here are never negative, so HALF_UP and "half away from zero" agree. */
    public static final RoundingMode ROUNDING = RoundingMode.HALF_UP;
    /** Tax rates are fractions with 4 decimals: 0.2400 = 24 %. */
    public static final int RATE_SCALE = 4;

    public static final BigDecimal ZERO = BigDecimal.ZERO.setScale(SCALE);

    private Money() {
    }

    /** Tax on a net amount, rounded to cents. taxOf(10.30, 0.24) = 2.47 (2.472). */
    public static BigDecimal taxOf(BigDecimal net, BigDecimal rate) {
        return net.multiply(rate).setScale(SCALE, ROUNDING);
    }

    /**
     * A price that must already be in cents. Too many decimals is a BUG upstream (the API validates it),
     * so it fails loudly with ArithmeticException instead of being rounded silently.
     */
    public static BigDecimal exactCents(BigDecimal amount) {
        return amount.setScale(SCALE, RoundingMode.UNNECESSARY);
    }

    public static BigDecimal exactRate(BigDecimal rate) {
        return rate.setScale(RATE_SCALE, RoundingMode.UNNECESSARY);
    }
}
