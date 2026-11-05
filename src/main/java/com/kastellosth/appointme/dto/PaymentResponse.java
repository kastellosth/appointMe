package com.kastellosth.appointme.dto;

import com.kastellosth.appointme.entity.Payment;
import com.kastellosth.appointme.entity.PaymentMethod;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Built ONLY from the stored payment row, so a replay (same Idempotency-Key) returns
 * exactly the same body as the first response, even if more payments came in since.
 */
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
