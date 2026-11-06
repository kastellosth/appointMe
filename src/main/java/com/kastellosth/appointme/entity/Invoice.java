package com.kastellosth.appointme.entity;

import com.kastellosth.appointme.exception.ConflictException;
import com.kastellosth.appointme.exception.IllegalStateTransitionException;
import com.kastellosth.appointme.support.Money;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Entity
@Table(name = "invoices")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Invoice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private Long tenantId;

    @Column(name = "customer_id", nullable = false, updatable = false)
    private Long customerId;

    @Column(name = "appointment_id", updatable = false)
    private Long appointmentId;

    @Column(length = 30)
    private String number;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private InvoiceStatus status = InvoiceStatus.DRAFT;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal subtotal = new BigDecimal("0.00");

    @Column(name = "tax_total", nullable = false, precision = 12, scale = 2)
    private BigDecimal taxTotal = new BigDecimal("0.00");

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal total = new BigDecimal("0.00");

    @Column(name = "issued_at")
    private Instant issuedAt;

    @Column(name = "due_at")
    private Instant dueAt;

    @Column(name = "paid_at")
    private Instant paidAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Version
    private long version;

    @OneToMany(mappedBy = "invoice", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("lineNo")
    private List<InvoiceLine> lines = new ArrayList<>();

    public static Invoice draft(Long tenantId, Long customerId, Long appointmentId, String currency) {
        Invoice invoice = new Invoice();
        invoice.tenantId = tenantId;
        invoice.customerId = customerId;
        invoice.appointmentId = appointmentId;
        invoice.currency = currency;
        return invoice;
    }

    public void replaceLines(List<InvoiceLine> newLines) {
        requireDraft("edited");
        lines.clear();
        int lineNo = 1;
        for (InvoiceLine line : newLines) {
            line.attachTo(this, lineNo++);
            lines.add(line);
        }
        recalculate();
    }

    private void recalculate() {
        BigDecimal sub = BigDecimal.ZERO;
        BigDecimal tax = BigDecimal.ZERO;
        for (InvoiceLine line : lines) {
            sub = sub.add(line.getLineTotal());
            tax = tax.add(line.getTaxAmount());
        }
        this.subtotal = sub.setScale(Money.SCALE);
        this.taxTotal = tax.setScale(Money.SCALE);
        this.total = this.subtotal.add(this.taxTotal);
    }

    public void checkIssuable() {
        requireTransition(InvoiceStatus.ISSUED);
        if (lines.isEmpty()) {
            throw new ConflictException("An invoice without lines cannot be issued");
        }
    }

    public void issue(String number, Instant now, Duration paymentTerm) {
        checkIssuable();
        this.status = InvoiceStatus.ISSUED;
        this.number = number;
        this.issuedAt = now;
        this.dueAt = now.plus(paymentTerm);
    }

    public void markPaid(Instant now) {
        requireTransition(InvoiceStatus.PAID);
        this.status = InvoiceStatus.PAID;
        this.paidAt = now;
    }

    public void voidInvoice() {
        requireTransition(InvoiceStatus.VOID);
        this.status = InvoiceStatus.VOID;
    }

    private void requireTransition(InvoiceStatus target) {
        if (!status.canTransitionTo(target)) {
            throw new IllegalStateTransitionException(
                    "Invoice cannot go from " + status + " to " + target);
        }
    }

    private void requireDraft(String what) {
        if (!status.isEditable()) {
            throw new ConflictException("Only a DRAFT invoice can be " + what
                    + " (this one is " + status + ")");
        }
    }

    public List<InvoiceLine> getLines() {
        return Collections.unmodifiableList(lines);
    }
}
