package com.kastellosth.appointme.dto;

import com.kastellosth.appointme.entity.Payment;
import com.kastellosth.appointme.entity.PaymentMethod;

import java.math.BigDecimal;
import java.time.Instant;

/** Built only from the stored row, so a replay returns exactly the first response. */
public record PaymentResponse(
        Long id,
        Long invoiceId,
        BigDecimal amount,
        PaymentMethod method,
        String reference,
        BigDecimal balanceAfter,
        boolean invoicePaid,
        Instant receivedAt
) {
    public static PaymentResponse from(Payment p) {
        return new PaymentResponse(p.getId(), p.getInvoiceId(), p.getAmount(), p.getMethod(),
                p.getReference(), p.getBalanceAfter(), p.settlesInvoice(), p.getReceivedAt());
    }
}
