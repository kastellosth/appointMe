package com.kastellosth.appointme.service;

import com.kastellosth.appointme.dto.InvoicePaymentsResponse;
import com.kastellosth.appointme.dto.PaymentRequest;
import com.kastellosth.appointme.dto.PaymentResponse;
import com.kastellosth.appointme.entity.AuditAction;
import com.kastellosth.appointme.entity.Invoice;
import com.kastellosth.appointme.entity.InvoiceStatus;
import com.kastellosth.appointme.entity.Payment;
import com.kastellosth.appointme.exception.ConflictException;
import com.kastellosth.appointme.exception.NotFoundException;
import com.kastellosth.appointme.exception.UnprocessableException;
import com.kastellosth.appointme.repository.InvoiceRepository;
import com.kastellosth.appointme.repository.PaymentRepository;
import com.kastellosth.appointme.support.Money;
import com.kastellosth.appointme.support.RequestHash;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

/**
 * Records payments. Two different dangers, two different defences:
 *
 *  1. The SAME request arrives twice (timeout + retry, double click)  -> Idempotency-Key.
 *  2. Two DIFFERENT payments for one invoice at the same moment
 *     ("read balance, then insert" would let both through)            -> row lock on the invoice.
 *
 * The database checks both again (primary key on idempotency_keys, trigger on payments).
 */
@Service
public class PaymentService {

    static final String OPERATION = "invoice-payment";

    public record Result(PaymentResponse payment, boolean replayed) {
    }

    private final InvoiceRepository invoices;
    private final PaymentRepository payments;
    private final IdempotencyService idempotency;
    private final AuditService audit;

    public PaymentService(InvoiceRepository invoices, PaymentRepository payments, IdempotencyService idempotency,
                          AuditService audit) {
        this.invoices = invoices;
        this.payments = payments;
        this.idempotency = idempotency;
        this.audit = audit;
    }

    @Transactional
    public Result record(Long tenantId, Long invoiceId, String key, PaymentRequest request) {
        IdempotencyService.requireValidKey(key);
        BigDecimal amount = Money.exactCents(request.amount());
        String reference = (request.reference() == null || request.reference().isBlank())
                ? null : request.reference().strip();
        // 10, 10.0 and 10.00 are the same amount, so they must give the same fingerprint
        String hash = RequestHash.of(String.valueOf(invoiceId), amount.stripTrailingZeros().toPlainString(),
                request.method().name(), reference);

        // 1) Claim the key FIRST. A concurrent duplicate waits here until we commit or roll back.
        var existing = idempotency.claim(tenantId, key, OPERATION, hash);
        if (existing.isPresent()) {
            return replay(tenantId, key, hash, existing.get());
        }

        // 2) Lock the invoice: other payments for it wait until we commit.
        Invoice invoice = invoices.findForUpdate(invoiceId, tenantId)
                .orElseThrow(() -> new NotFoundException("Invoice " + invoiceId + " not found"));
        if (invoice.getStatus() != InvoiceStatus.ISSUED) {
            throw new ConflictException("Only an ISSUED invoice can receive payments (this one is "
                    + invoice.getStatus() + ")");
        }

        // 3) Under the lock, the balance cannot change behind our back.
        BigDecimal balance = invoice.getTotal().subtract(paidSoFar(tenantId, invoiceId));
        if (amount.compareTo(balance) > 0) {
            throw new ConflictException("Payment of " + amount + " is more than the open balance " + balance);
        }

        // 4) Payment first (the database trigger wants the invoice still ISSUED), then PAID if settled.
        Payment payment = payments.saveAndFlush(Payment.record(tenantId, invoiceId, amount, request.method(),
                reference, balance.subtract(amount), Instant.now()));
        if (payment.settlesInvoice()) {
            invoice.markPaid(payment.getReceivedAt());
            invoices.saveAndFlush(invoice);
        }

        // Only a real payment reaches this line. A replay returned above, so a retry adds no history.
        // The reference is left out on purpose: it is free text and could hold personal data.
        audit.record(tenantId, AuditAction.PAYMENT_RECORDED, invoiceId, "payment " + payment.getId() + ": "
                + amount + " " + invoice.getCurrency() + " " + request.method() + ", balance " + payment.getBalanceAfter());
        if (payment.settlesInvoice()) {
            audit.record(tenantId, AuditAction.INVOICE_PAID, invoiceId, "ISSUED -> PAID");
        }

        // 5) Remember the result under the key. Commits together with the payment, or not at all.
        idempotency.complete(tenantId, key, payment.getId());
        return new Result(PaymentResponse.from(payment), false);
    }

    @Transactional(readOnly = true)
    public InvoicePaymentsResponse list(Long tenantId, Long invoiceId) {
        Invoice invoice = invoices.findByIdAndTenantId(invoiceId, tenantId)
                .orElseThrow(() -> new NotFoundException("Invoice " + invoiceId + " not found"));
        BigDecimal paid = paidSoFar(tenantId, invoiceId);
        var list = payments.findByTenantIdAndInvoiceIdOrderByIdAsc(tenantId, invoiceId).stream()
                .map(PaymentResponse::from).toList();
        return new InvoicePaymentsResponse(invoiceId, invoice.getStatus(), invoice.getTotal(), paid,
                invoice.getTotal().subtract(paid), list);
    }

    private Result replay(Long tenantId, String key, String hash, IdempotencyService.StoredKey stored) {
        if (!OPERATION.equals(stored.operation()) || !hash.equals(stored.requestHash())) {
            throw new UnprocessableException("Idempotency-Key '" + key
                    + "' was already used for a different request. Use a new key for a new payment.");
        }
        if (stored.paymentId() == null) {
            // Cannot happen: the key and its payment are committed in the same transaction.
            throw new IllegalStateException("Idempotency key " + key + " has no result");
        }
        Payment payment = payments.findByIdAndTenantId(stored.paymentId(), tenantId)
                .orElseThrow(() -> new IllegalStateException("Payment " + stored.paymentId() + " is missing"));
        return new Result(PaymentResponse.from(payment), true);
    }

    private BigDecimal paidSoFar(Long tenantId, Long invoiceId) {
        return Objects.requireNonNullElse(payments.sumForInvoice(tenantId, invoiceId), Money.ZERO);
    }
}
