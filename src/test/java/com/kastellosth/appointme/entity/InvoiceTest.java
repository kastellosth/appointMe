package com.kastellosth.appointme.entity;

import com.kastellosth.appointme.exception.ConflictException;
import com.kastellosth.appointme.exception.IllegalStateTransitionException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class InvoiceTest {

    private static final BigDecimal VAT = new BigDecimal("0.24");

    private static BigDecimal d(String s) {
        return new BigDecimal(s);
    }

    private static Invoice draftWith(InvoiceLine... lines) {
        Invoice invoice = Invoice.draft(1L, 1L, null, "EUR");
        invoice.replaceLines(List.of(lines));
        return invoice;
    }

    @Test
    void lineStoresItsTotalAndTax() {
        InvoiceLine line = InvoiceLine.of("Haircut", 2, d("25.00"), VAT);
        assertEquals(d("50.00"), line.getLineTotal());
        assertEquals(d("12.00"), line.getTaxAmount());
        assertEquals(d("0.2400"), line.getTaxRate());
    }

    @Test
    void totalsAreTheSumOfTheLines() {
        Invoice invoice = draftWith(
                InvoiceLine.of("Haircut", 1, d("25.00"), VAT),
                InvoiceLine.of("Beard Trim", 1, d("10.00"), VAT));
        assertEquals(d("35.00"), invoice.getSubtotal());
        assertEquals(d("8.40"), invoice.getTaxTotal());
        assertEquals(d("43.40"), invoice.getTotal());
    }

    @Test
    void taxIsRoundedPerLineNotPerInvoice() {
        Invoice invoice = draftWith(
                InvoiceLine.of("Gel", 1, d("0.10"), VAT),
                InvoiceLine.of("Gel", 1, d("0.10"), VAT),
                InvoiceLine.of("Gel", 1, d("0.10"), VAT));
        // per line: 0.024 -> 0.02, three times = 0.06.  (Per invoice it would be 0.072 -> 0.07.)
        assertEquals(d("0.06"), invoice.getTaxTotal());
        assertEquals(d("0.36"), invoice.getTotal());
        BigDecimal sumOfLineTaxes = invoice.getLines().stream()
                .map(InvoiceLine::getTaxAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        assertEquals(invoice.getTaxTotal(), sumOfLineTaxes);
    }

    @Test
    void linesAreNumberedFromOne() {
        Invoice invoice = draftWith(
                InvoiceLine.of("A", 1, d("1.00"), VAT),
                InvoiceLine.of("B", 1, d("2.00"), VAT));
        assertEquals(1, invoice.getLines().get(0).getLineNo());
        assertEquals(2, invoice.getLines().get(1).getLineNo());
    }

    @Test
    void emptyDraftHasZeroTotalsWithTwoDecimals() {
        Invoice invoice = draftWith();
        assertEquals(d("0.00"), invoice.getTotal());
    }

    @Test
    void issuedInvoiceCannotBeEdited() {
        Invoice invoice = draftWith(InvoiceLine.of("Haircut", 1, d("25.00"), VAT));
        invoice.issue("INV-2026-0001", Instant.now(), Duration.ofDays(14));
        assertThrows(ConflictException.class,
                () -> invoice.replaceLines(List.of(InvoiceLine.of("More", 1, d("1.00"), VAT))));
    }

    @Test
    void emptyInvoiceCannotBeIssued() {
        assertThrows(ConflictException.class, () -> draftWith().checkIssuable());
    }

    @Test
    void draftCannotBePaid() {
        Invoice invoice = draftWith(InvoiceLine.of("Haircut", 1, d("25.00"), VAT));
        assertThrows(IllegalStateTransitionException.class, () -> invoice.markPaid(Instant.now()));
    }
}
