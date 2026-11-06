package com.kastellosth.appointme.service;

import com.kastellosth.appointme.dto.InvoicePaymentsResponse;
import com.kastellosth.appointme.dto.PaymentRequest;
import com.kastellosth.appointme.entity.AuditAction;
import com.kastellosth.appointme.entity.Invoice;
import com.kastellosth.appointme.entity.InvoiceLine;
import com.kastellosth.appointme.entity.InvoiceStatus;
import com.kastellosth.appointme.entity.Payment;
import com.kastellosth.appointme.entity.PaymentMethod;
import com.kastellosth.appointme.exception.BadRequestException;
import com.kastellosth.appointme.exception.ConflictException;
import com.kastellosth.appointme.exception.NotFoundException;
import com.kastellosth.appointme.exception.UnprocessableException;
import com.kastellosth.appointme.repository.InvoiceRepository;
import com.kastellosth.appointme.repository.PaymentRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    private static final long TENANT = 1L;
    private static final long INVOICE = 10L;
    private static final long PAYMENT = 77L;
    private static final String KEY = "key-12345";

    @Mock
    private InvoiceRepository invoices;

    @Mock
    private PaymentRepository payments;

    @Mock
    private IdempotencyService idempotency;

    @Mock
    private AuditService audit;

    @InjectMocks
    private PaymentService service;

    private static BigDecimal d(String s) {
        return new BigDecimal(s);
    }

    private static PaymentRequest cash(String amount) {
        return new PaymentRequest(d(amount), PaymentMethod.CASH, null);
    }

    /** Total 124.00. A real object, not a mock: its state machine is part of what is being tested. */
    private static Invoice draftInvoice() {
        Invoice invoice = Invoice.draft(TENANT, 1L, null, "EUR");
        invoice.replaceLines(List.of(InvoiceLine.of("Haircut", 1, d("100.00"), d("0.24"))));
        return invoice;
    }

    private static Invoice issuedInvoice() {
        Invoice invoice = draftInvoice();
        invoice.issue("INV-2026-0001", Instant.now(), Duration.ofDays(14));
        return invoice;
    }

    private void keyIsFree() {
        when(idempotency.claim(eq(TENANT), eq(KEY), eq(PaymentService.OPERATION), anyString()))
                .thenReturn(Optional.empty());
    }

    private void lockedInvoiceIs(Invoice invoice) {
        when(invoices.findForUpdate(INVOICE, TENANT)).thenReturn(Optional.of(invoice));
    }

    /** The database would give the new row an id, and complete() needs one. */
    private void savingAssignsAnId() {
        when(payments.saveAndFlush(any(Payment.class))).thenAnswer(call -> {
            Payment payment = call.getArgument(0);
            ReflectionTestUtils.setField(payment, "id", PAYMENT);
            return payment;
        });
    }

    private void paidSoFar(String amount) {
        when(payments.sumForInvoice(TENANT, INVOICE)).thenReturn(d(amount));
    }

    @Test
    void partialPaymentStoresTheBalanceAndLeavesTheInvoiceIssued() {
        Invoice invoice = issuedInvoice();
        keyIsFree();
        lockedInvoiceIs(invoice);
        savingAssignsAnId();

        PaymentService.Result result = service.record(TENANT, INVOICE, KEY, cash("24.00"));

        ArgumentCaptor<Payment> saved = ArgumentCaptor.forClass(Payment.class);
        verify(payments).saveAndFlush(saved.capture());
        assertEquals(d("24.00"), saved.getValue().getAmount());
        assertEquals(d("100.00"), saved.getValue().getBalanceAfter());
        assertEquals(TENANT, saved.getValue().getTenantId());

        assertFalse(result.replayed());
        assertFalse(result.payment().invoicePaid());
        assertEquals(InvoiceStatus.ISSUED, invoice.getStatus());
        verify(invoices, never()).saveAndFlush(any());
        verify(audit, never()).record(any(), eq(AuditAction.INVOICE_PAID), any(), any());
        verify(idempotency).complete(TENANT, KEY, PAYMENT);
    }

    @Test
    void paymentOfTheExactBalanceMarksTheInvoicePaid() {
        Invoice invoice = issuedInvoice();
        keyIsFree();
        lockedInvoiceIs(invoice);
        paidSoFar("100.00");
        savingAssignsAnId();

        PaymentService.Result result = service.record(TENANT, INVOICE, KEY, cash("24.00"));

        assertTrue(result.payment().invoicePaid());
        assertEquals(InvoiceStatus.PAID, invoice.getStatus());
        assertEquals(result.payment().receivedAt(), invoice.getPaidAt());
        verify(audit).record(eq(TENANT), eq(AuditAction.PAYMENT_RECORDED), eq(INVOICE), any());
        verify(audit).record(TENANT, AuditAction.INVOICE_PAID, INVOICE, "ISSUED -> PAID");
    }

    @Test
    void stepsHappenInTheOrderTheLocksAndTriggersNeed() {
        keyIsFree();
        lockedInvoiceIs(issuedInvoice());
        savingAssignsAnId();

        service.record(TENANT, INVOICE, KEY, cash("124.00"));

        InOrder order = inOrder(idempotency, invoices, payments);
        order.verify(idempotency).claim(eq(TENANT), eq(KEY), any(), any());
        order.verify(invoices).findForUpdate(INVOICE, TENANT);
        order.verify(payments).saveAndFlush(any(Payment.class));
        order.verify(invoices).saveAndFlush(any(Invoice.class));
        order.verify(idempotency).complete(TENANT, KEY, PAYMENT);
    }

    @Test
    void oneCentOverTheBalanceIsRefused() {
        keyIsFree();
        lockedInvoiceIs(issuedInvoice());
        paidSoFar("100.00");

        assertThrows(ConflictException.class, () -> service.record(TENANT, INVOICE, KEY, cash("24.01")));

        verify(payments, never()).saveAndFlush(any());
        verify(idempotency, never()).complete(anyLong(), any(), anyLong());
        verifyNoInteractions(audit);
    }

    @Test
    void draftInvoiceCannotReceiveAPayment() {
        keyIsFree();
        lockedInvoiceIs(draftInvoice());

        assertThrows(ConflictException.class, () -> service.record(TENANT, INVOICE, KEY, cash("1.00")));

        verify(payments, never()).saveAndFlush(any());
    }

    @Test
    void invoiceOfAnotherTenantIsNotFound() {
        keyIsFree();
        when(invoices.findForUpdate(INVOICE, TENANT)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> service.record(TENANT, INVOICE, KEY, cash("1.00")));
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"short", "has spaces in it", "semi;colon;key"})
    void invalidKeyIsRefusedBeforeAnythingHappens(String key) {
        assertThrows(BadRequestException.class, () -> service.record(TENANT, INVOICE, key, cash("1.00")));

        verifyNoInteractions(idempotency, invoices, payments, audit);
    }

    @Test
    void sameKeyAndSameRequestReturnsTheStoredPaymentAndChangesNothing() {
        when(idempotency.claim(eq(TENANT), eq(KEY), eq(PaymentService.OPERATION), anyString())).thenAnswer(call ->
                Optional.of(new IdempotencyService.StoredKey(PaymentService.OPERATION, call.getArgument(3), PAYMENT)));
        Payment stored = Payment.record(TENANT, INVOICE, d("24.00"), PaymentMethod.CASH, null, d("100.00"), Instant.now());
        when(payments.findByIdAndTenantId(PAYMENT, TENANT)).thenReturn(Optional.of(stored));

        PaymentService.Result result = service.record(TENANT, INVOICE, KEY, cash("24.00"));

        assertTrue(result.replayed());
        assertEquals(d("100.00"), result.payment().balanceAfter());
        verifyNoInteractions(invoices, audit);
        verify(payments, never()).saveAndFlush(any());
        verify(idempotency, never()).complete(anyLong(), any(), anyLong());
    }

    @Test
    void sameKeyWithADifferentRequestIsRefused() {
        when(idempotency.claim(eq(TENANT), eq(KEY), eq(PaymentService.OPERATION), anyString()))
                .thenReturn(Optional.of(new IdempotencyService.StoredKey(PaymentService.OPERATION, "another-hash", PAYMENT)));

        assertThrows(UnprocessableException.class, () -> service.record(TENANT, INVOICE, KEY, cash("24.00")));

        verifyNoInteractions(invoices, audit);
    }

    @Test
    void sameKeyUsedByAnotherOperationIsRefused() {
        when(idempotency.claim(eq(TENANT), eq(KEY), eq(PaymentService.OPERATION), anyString())).thenAnswer(call ->
                Optional.of(new IdempotencyService.StoredKey("refund", call.getArgument(3), PAYMENT)));

        assertThrows(UnprocessableException.class, () -> service.record(TENANT, INVOICE, KEY, cash("24.00")));
    }

    @Test
    void amountFormatAndBlankReferenceDoNotChangeTheRequestHash() {
        keyIsFree();
        lockedInvoiceIs(issuedInvoice());
        savingAssignsAnId();

        service.record(TENANT, INVOICE, KEY, new PaymentRequest(d("24.5"), PaymentMethod.CASH, null));
        service.record(TENANT, INVOICE, KEY, new PaymentRequest(d("24.50"), PaymentMethod.CASH, "   "));

        ArgumentCaptor<String> hash = ArgumentCaptor.forClass(String.class);
        verify(idempotency, times(2)).claim(eq(TENANT), eq(KEY), any(), hash.capture());
        assertEquals(hash.getAllValues().get(0), hash.getAllValues().get(1));
    }

    @Test
    void blankReferenceIsStoredAsNull() {
        keyIsFree();
        lockedInvoiceIs(issuedInvoice());
        savingAssignsAnId();

        PaymentService.Result result = service.record(TENANT, INVOICE, KEY,
                new PaymentRequest(d("1.00"), PaymentMethod.CASH, "   "));

        assertNull(result.payment().reference());
    }

    @Test
    void invoiceWithoutPaymentsHasTheWholeTotalOpen() {
        when(invoices.findByIdAndTenantId(INVOICE, TENANT)).thenReturn(Optional.of(issuedInvoice()));
        when(payments.sumForInvoice(TENANT, INVOICE)).thenReturn(null);
        when(payments.findByTenantIdAndInvoiceIdOrderByIdAsc(TENANT, INVOICE)).thenReturn(List.of());

        InvoicePaymentsResponse response = service.list(TENANT, INVOICE);

        assertEquals(d("0.00"), response.paid());
        assertEquals(d("124.00"), response.balance());
    }
}
