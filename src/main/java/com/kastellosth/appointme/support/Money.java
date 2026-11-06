package com.kastellosth.appointme.support;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** The one place that decides rounding: tax per line, half-up to cents. See docs/invoices.md. */
public final class Money {

    public static final int SCALE = 2;
    public static final RoundingMode ROUNDING = RoundingMode.HALF_UP;
    public static final int RATE_SCALE = 4;

    public static final BigDecimal ZERO = BigDecimal.ZERO.setScale(SCALE);

    private Money() {
    }

    public static BigDecimal taxOf(BigDecimal net, BigDecimal rate) {
        return net.multiply(rate).setScale(SCALE, ROUNDING);
    }

    public static BigDecimal exactCents(BigDecimal amount) {
        return amount.setScale(SCALE, RoundingMode.UNNECESSARY);
    }

    public static BigDecimal exactRate(BigDecimal rate) {
        return rate.setScale(RATE_SCALE, RoundingMode.UNNECESSARY);
    }
}
