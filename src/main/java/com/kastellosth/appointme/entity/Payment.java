package com.kastellosth.appointme.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Money received for an invoice. Append-only: no setters, and the database refuses
 * UPDATE and DELETE (trg_payments_append_only). A mistake is corrected with a new record
 * (a refund, later), never by editing history.
 */
@Entity
@Table(name = "payments")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private Long tenantId;

    @Column(name = "invoice_id", nullable = false, updatable = false)
    private Long invoiceId;

    @Column(nullable = false, updatable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 20)
    private PaymentMethod method;

    @Column(length = 100, updatable = false)
    private String reference;

    /** What was still owed after this payment. 0.00 means this payment settled the invoice. */
    @Column(name = "balance_after", nullable = false, updatable = false, precision = 12, scale = 2)
    private BigDecimal balanceAfter;

    @Column(name = "received_at", nullable = false, updatable = false)
    private Instant receivedAt;

    public static Payment record(Long tenantId, Long invoiceId, BigDecimal amount, PaymentMethod method,
                                 String reference, BigDecimal balanceAfter, Instant receivedAt) {
        Payment p = new Payment();
        p.tenantId = tenantId;
        p.invoiceId = invoiceId;
        p.amount = amount;
        p.method = method;
        p.reference = reference;
        p.balanceAfter = balanceAfter;
        p.receivedAt = receivedAt;
        return p;
    }

    public boolean settlesInvoice() {
        return balanceAfter.signum() == 0;
    }
}
