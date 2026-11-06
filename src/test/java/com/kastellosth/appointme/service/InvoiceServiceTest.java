package com.kastellosth.appointme.service;

import com.kastellosth.appointme.dto.InvoiceLineRequest;
import com.kastellosth.appointme.dto.InvoiceRequest;
import com.kastellosth.appointme.dto.InvoiceResponse;
import com.kastellosth.appointme.entity.Appointment;
import com.kastellosth.appointme.entity.AppointmentStatus;
import com.kastellosth.appointme.entity.AuditAction;
import com.kastellosth.appointme.entity.Customer;
import com.kastellosth.appointme.entity.Invoice;
import com.kastellosth.appointme.entity.InvoiceLine;
import com.kastellosth.appointme.entity.InvoiceStatus;
import com.kastellosth.appointme.entity.ServiceOffering;
import com.kastellosth.appointme.entity.Tenant;
import com.kastellosth.appointme.exception.BadRequestException;
import com.kastellosth.appointme.exception.ConflictException;
import com.kastellosth.appointme.exception.NotFoundException;
import com.kastellosth.appointme.repository.AppointmentRepository;
import com.kastellosth.appointme.repository.CustomerRepository;
import com.kastellosth.appointme.repository.InvoiceRepository;
import com.kastellosth.appointme.repository.PaymentRepository;
import com.kastellosth.appointme.repository.TenantRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.AdditionalAnswers.returnsFirstArg;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InvoiceServiceTest {

    private static final long TENANT = 1L;
    private static final long INVOICE = 10L;
    private static final long CUSTOMER = 5L;
    private static final long APPOINTMENT = 40L;

    @Mock
    private InvoiceRepository invoices;

    @Mock
    private CustomerRepository customers;

    @Mock
    private AppointmentRepository appointments;

    @Mock
    private TenantRepository tenants;

    @Mock
    private InvoiceNumberService numbers;

    @Mock
    private PaymentRepository payments;

    @Mock
    private AuditService audit;

    @InjectMocks
    private InvoiceService service;

    private static BigDecimal d(String s) {
        return new BigDecimal(s);
    }

    private static Tenant tenant() {
        Tenant tenant = new Tenant();
        tenant.setId(TENANT);
        tenant.setTimezone("Europe/Athens");
        tenant.setTaxRate(d("0.2400"));
        tenant.setCurrency("EUR");
        return tenant;
    }

    private static Invoice draft(InvoiceLine... lines) {
        Invoice invoice = Invoice.draft(TENANT, CUSTOMER, null, "EUR");
        invoice.replaceLines(List.of(lines));
        return invoice;
    }

    private static InvoiceLine haircut() {
        return InvoiceLine.of("Haircut", 1, d("25.00"), d("0.24"));
    }

    private static Appointment completedAppointment() {
        Customer customer = new Customer();
        customer.setId(CUSTOMER);
        ServiceOffering service = new ServiceOffering();
        service.setName("Haircut");
        service.setPrice(d("25.00"));

        Appointment a = new Appointment();
        a.setId(APPOINTMENT);
        a.setTenant(tenant());
        a.setCustomer(customer);
        a.setService(service);
        a.setStartsAt(Instant.parse("2026-07-01T07:00:00Z"));
        a.setStatus(AppointmentStatus.COMPLETED);
        return a;
    }

    private void invoiceExists(Invoice invoice) {
        when(invoices.findByIdAndTenantId(INVOICE, TENANT)).thenReturn(Optional.of(invoice));
    }

    private void tenantExists() {
        when(tenants.findById(TENANT)).thenReturn(Optional.of(tenant()));
    }

    private void customerExists() {
        when(customers.findByIdAndTenantId(CUSTOMER, TENANT)).thenReturn(Optional.of(new Customer()));
    }

    private void savingReturnsTheInvoice() {
        when(invoices.saveAndFlush(any(Invoice.class))).then(returnsFirstArg());
    }

    @Test
    void issueTakesANumberAndMakesTheInvoiceDueInFourteenDays() {
        invoiceExists(draft(haircut()));
        tenantExists();
        when(numbers.next(eq(TENANT), anyInt())).thenReturn("INV-2026-0001");
        savingReturnsTheInvoice();

        InvoiceResponse response = service.issue(TENANT, INVOICE);

        assertEquals(InvoiceStatus.ISSUED, response.status());
        assertEquals("INV-2026-0001", response.number());
        assertEquals(response.issuedAt().plus(Duration.ofDays(14)), response.dueAt());
        verify(audit).record(TENANT, AuditAction.INVOICE_ISSUED, INVOICE, "number INV-2026-0001, total 31.00");
    }

    @Test
    void refusedIssueNeverUsesUpANumber() {
        invoiceExists(draft());

        assertThrows(ConflictException.class, () -> service.issue(TENANT, INVOICE));

        verifyNoInteractions(numbers, audit);
        verify(invoices, never()).saveAndFlush(any());
    }

    @Test
    void invoiceWithPaymentsCannotBeVoided() {
        Invoice invoice = draft(haircut());
        invoice.issue("INV-2026-0001", Instant.now(), Duration.ofDays(14));
        invoiceExists(invoice);
        when(payments.sumForInvoice(TENANT, INVOICE)).thenReturn(d("1.00"));

        assertThrows(ConflictException.class, () -> service.voidInvoice(TENANT, INVOICE));

        assertEquals(InvoiceStatus.ISSUED, invoice.getStatus());
        verifyNoInteractions(audit);
    }

    @Test
    void invoiceWithoutPaymentsCanBeVoided() {
        invoiceExists(draft(haircut()));
        when(payments.sumForInvoice(TENANT, INVOICE)).thenReturn(null);
        savingReturnsTheInvoice();

        assertEquals(InvoiceStatus.VOID, service.voidInvoice(TENANT, INVOICE).status());
        verify(audit).record(TENANT, AuditAction.INVOICE_VOIDED, INVOICE, "DRAFT -> VOID");
    }

    @Test
    void invoiceOfAnotherTenantIsNotFound() {
        when(invoices.findByIdAndTenantId(INVOICE, TENANT)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> service.issue(TENANT, INVOICE));
    }

    @Test
    void automaticDraftUsesTheServicePriceTheTenantTaxAndLocalTime() {
        savingReturnsTheInvoice();

        Invoice invoice = service.createDraftForCompletedAppointment(completedAppointment());

        assertEquals(d("31.00"), invoice.getTotal());
        assertEquals("EUR", invoice.getCurrency());
        assertEquals(APPOINTMENT, invoice.getAppointmentId());
        InvoiceLine line = invoice.getLines().getFirst();
        assertEquals("Haircut (2026-07-01 10:00)", line.getDescription());
        assertEquals(d("0.2400"), line.getTaxRate());
        verify(audit).record(eq(TENANT), eq(AuditAction.INVOICE_CREATED), any(), any());
    }

    @Test
    void appointmentWithALiveInvoiceGetsNoSecondOne() {
        when(invoices.existsByTenantIdAndAppointmentIdAndStatusNot(TENANT, APPOINTMENT, InvoiceStatus.VOID))
                .thenReturn(true);

        assertNull(service.createDraftForCompletedAppointment(completedAppointment()));

        verify(invoices, never()).saveAndFlush(any());
        verifyNoInteractions(audit);
    }

    @Test
    void onlyACompletedAppointmentIsInvoiced() {
        Appointment appointment = completedAppointment();
        appointment.setStatus(AppointmentStatus.SCHEDULED);

        assertThrows(IllegalStateException.class, () -> service.createDraftForCompletedAppointment(appointment));

        verifyNoInteractions(invoices);
    }

    @Test
    void manualInvoiceFallsBackToTheTenantCurrencyAndTaxRate() {
        tenantExists();
        customerExists();
        savingReturnsTheInvoice();
        var line = new InvoiceLineRequest("Haircut", 1, d("25.00"), null);

        InvoiceResponse response = service.create(TENANT, new InvoiceRequest(CUSTOMER, null, null, List.of(line)));

        assertEquals("EUR", response.currency());
        assertEquals(d("6.00"), response.taxTotal());
    }

    @Test
    void customerOfAnotherTenantIsNotFound() {
        tenantExists();
        when(customers.findByIdAndTenantId(CUSTOMER, TENANT)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class,
                () -> service.create(TENANT, new InvoiceRequest(CUSTOMER, null, null, List.of())));

        verify(invoices, never()).saveAndFlush(any());
    }

    @Test
    void appointmentOfADifferentCustomerIsRefused() {
        tenantExists();
        customerExists();
        Appointment other = completedAppointment();
        other.getCustomer().setId(CUSTOMER + 1);
        when(appointments.findByIdAndTenantId(APPOINTMENT, TENANT)).thenReturn(Optional.of(other));

        assertThrows(BadRequestException.class,
                () -> service.create(TENANT, new InvoiceRequest(CUSTOMER, APPOINTMENT, null, List.of())));
    }

    @Test
    void secondInvoiceForAnAppointmentIsRefused() {
        tenantExists();
        customerExists();
        when(appointments.findByIdAndTenantId(APPOINTMENT, TENANT)).thenReturn(Optional.of(completedAppointment()));
        when(invoices.existsByTenantIdAndAppointmentIdAndStatusNot(TENANT, APPOINTMENT, InvoiceStatus.VOID))
                .thenReturn(true);

        assertThrows(ConflictException.class,
                () -> service.create(TENANT, new InvoiceRequest(CUSTOMER, APPOINTMENT, null, List.of())));

        verify(invoices, never()).saveAndFlush(any());
    }
}
