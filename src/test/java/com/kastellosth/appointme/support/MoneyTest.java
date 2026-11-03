package com.kastellosth.appointme.support;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Plain unit test: no Spring, no database. Runs in milliseconds. */
class MoneyTest {

    private static BigDecimal d(String s) {
        return new BigDecimal(s);
    }

    @Test
    void taxIsRoundedHalfUpToCents() {
        assertEquals(d("6.00"), Money.taxOf(d("25.00"), d("0.2400")));
        assertEquals(d("2.47"), Money.taxOf(d("10.30"), d("0.2400")));   // 2.472  -> 2.47
        assertEquals(d("0.02"), Money.taxOf(d("0.10"), d("0.2400")));    // 0.024  -> 0.02
        assertEquals(d("0.63"), Money.taxOf(d("0.625"), d("1")));        // exactly half a cent -> up
        assertEquals(d("0.00"), Money.taxOf(d("19.99"), d("0")));        // no tax, still 2 decimals
    }

    @Test
    void resultAlwaysHasTwoDecimals() {
        // equals() on BigDecimal compares the scale too: 6.0 is NOT equal to 6.00
        assertEquals(2, Money.taxOf(d("25"), d("0.24")).scale());
    }

    @Test
    void tooManyDecimalsFailLoudlyInsteadOfBeingRounded() {
        assertThrows(ArithmeticException.class, () -> Money.exactCents(d("10.001")));
        assertThrows(ArithmeticException.class, () -> Money.exactRate(d("0.24001")));
        assertEquals(d("10.00"), Money.exactCents(d("10")));
        assertEquals(d("0.2400"), Money.exactRate(d("0.24")));
    }
}
