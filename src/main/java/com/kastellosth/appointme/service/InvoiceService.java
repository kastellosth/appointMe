package com.kastellosth.appointme.service;

import com.kastellosth.appointme.dto.InvoiceLineRequest;
import com.kastellosth.appointme.dto.InvoiceRequest;
import com.kastellosth.appointme.dto.InvoiceResponse;
import com.kastellosth.appointme.dto.InvoiceSummaryResponse;
import com.kastellosth.appointme.dto.PageResponse;
import com.kastellosth.appointme.entity.Appointment;
import com.kastellosth.appointme.entity.AppointmentStatus;
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
import com.kastellosth.appointme.repository.InvoiceFilters;
import com.kastellosth.appointme.repository.InvoiceRepository;
import com.kastellosth.appointme.repository.PaymentRepository;
import com.kastellosth.appointme.repository.TenantRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Every method takes the tenant id from the TOKEN (the controller passes it in) and every lookup
 * uses it, so an invoice of another business is simply "not found" (404), never "forbidden".
 */
@Service
public class InvoiceService {

    private static final Duration PAYMENT_TERM = Duration.ofDays(14);
    private static final DateTimeFormatter LINE_DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    /** Newest first; id breaks ties so paging is stable (no row shows up on two pages). */
    private static final Sort NEWEST_FIRST = Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"));

    private final InvoiceRepository invoices;
    private final CustomerRepository customers;
    private final AppointmentRepository appointments;
    private final TenantRepository tenants;
    private final InvoiceNumberService numbers;
    private final PaymentRepository payments;

    public InvoiceService(InvoiceRepository invoices, CustomerRepository customers,
                          AppointmentRepository appointments, TenantRepository tenants,
                          InvoiceNumberService numbers, PaymentRepository payments) {
        this.invoices = invoices;
        this.customers = customers;
        this.appointments = appointments;
        this.tenants = tenants;
        this.numbers = numbers;
        this.payments = payments;
    }

    // ------------------------------------------------------------- Day 21: automatic draft

    /**
     * Called by AppointmentService when an appointment becomes COMPLETED.
     *
     * MANDATORY: it must run INSIDE the transaction that completes the appointment. Either both
     * happen (appointment COMPLETED + draft invoice) or neither does.
     *
     * Copies the service price NOW. Later price changes do not touch this invoice.
     * If the appointment already has a live invoice (the owner created one by hand), nothing happens.
     *
     * @return the new draft, or null if the appointment was already invoiced
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public Invoice createDraftForCompletedAppointment(Appointment appointment) {
        if (appointment.getStatus() != AppointmentStatus.COMPLETED) {
            throw new IllegalStateException("Only a COMPLETED appointment is invoiced, got " + appointment.getStatus());
        }
        Long tenantId = appointment.getTenant().getId();
        if (invoices.existsByTenantIdAndAppointmentIdAndStatusNot(
                tenantId, appointment.getId(), InvoiceStatus.VOID)) {
            return null;
        }

        Tenant tenant = appointment.getTenant();
        ServiceOffering service = appointment.getService();
        String when = appointment.getStartsAt().atZone(ZoneId.of(tenant.getTimezone())).format(LINE_DATE);

        Invoice invoice = Invoice.draft(tenantId, appointment.getCustomer().getId(), appointment.getId(),
                tenant.getCurrency());
        invoice.replaceLines(List.of(InvoiceLine.of(
                service.getName() + " (" + when + ")", 1, service.getPrice(), tenant.getTaxRate())));
        // saveAndFlush: if the database refuses (for example a second live invoice created at the
        // same moment, uq_invoices_one_live_per_appointment), we find out here, before commit.
        return invoices.saveAndFlush(invoice);
    }

    // ------------------------------------------------------------- by hand (Day 20)

    @Transactional
    public InvoiceResponse create(Long tenantId, InvoiceRequest request) {
        Tenant tenant = tenant(tenantId);
        customers.findByIdAndTenantId(request.customerId(), tenantId)
                .orElseThrow(() -> new NotFoundException("Customer " + request.customerId() + " not found"));

        if (request.appointmentId() != null) {
            Appointment appointment = appointments.findByIdAndTenantId(request.appointmentId(), tenantId)
                    .orElseThrow(() -> new NotFoundException("Appointment " + request.appointmentId() + " not found"));
            if (!appointment.getCustomer().getId().equals(request.customerId())) {
                throw new BadRequestException("That appointment belongs to a different customer");
            }
            if (invoices.existsByTenantIdAndAppointmentIdAndStatusNot(
                    tenantId, request.appointmentId(), InvoiceStatus.VOID)) {
                throw new ConflictException("That appointment already has an invoice");
            }
        }

        // Day 21: defaults come from the business, not from a constant
        String currency = request.currency() == null ? tenant.getCurrency() : request.currency();
        Invoice invoice = Invoice.draft(tenantId, request.customerId(), request.appointmentId(), currency);
        invoice.replaceLines(toLines(request.lines(), tenant));
        return InvoiceResponse.from(invoices.saveAndFlush(invoice));
    }

    @Transactional(readOnly = true)
    public InvoiceResponse get(Long tenantId, Long id) {
        return InvoiceResponse.from(find(tenantId, id));
    }

    /** Day 21. Filters are optional; the tenant is not. Sorting is fixed: newest first. */
    @Transactional(readOnly = true)
    public PageResponse<InvoiceSummaryResponse> list(Long tenantId, InvoiceStatus status, Long customerId,
                                                     Long appointmentId, Instant createdFrom, Instant createdTo,
                                                     int page, int size) {
        if (createdFrom != null && createdTo != null && !createdFrom.isBefore(createdTo)) {
            throw new BadRequestException("'createdFrom' must be before 'createdTo'");
        }
        var spec = InvoiceFilters.of(tenantId, status, customerId, appointmentId, createdFrom, createdTo);
        var result = invoices.findAll(spec, PageRequest.of(page, size, NEWEST_FIRST));
        return PageResponse.from(result.map(InvoiceSummaryResponse::from));
    }

    @Transactional
    public InvoiceResponse replaceLines(Long tenantId, Long id, List<InvoiceLineRequest> lines) {
        Invoice invoice = find(tenantId, id);
        invoice.replaceLines(toLines(lines, tenant(tenantId)));
        return InvoiceResponse.from(invoices.saveAndFlush(invoice));
    }

    @Transactional
    public InvoiceResponse issue(Long tenantId, Long id) {
        Invoice invoice = find(tenantId, id);
        // 1) Check first, so a refused request never uses up a number.
        invoice.checkIssuable();

        // 2) The invoice year is the year at the BUSINESS, not in UTC.
        Tenant tenant = tenant(tenantId);
        Instant now = Instant.now();
        int year = now.atZone(ZoneId.of(tenant.getTimezone())).getYear();

        // 3) Take the number INSIDE this transaction: if anything below fails, the number is given back.
        invoice.issue(numbers.next(tenantId, year), now, PAYMENT_TERM);
        return InvoiceResponse.from(invoices.saveAndFlush(invoice));
    }

    @Transactional
    public InvoiceResponse voidInvoice(Long tenantId, Long id) {
        Invoice invoice = find(tenantId, id);
        // Day 22: money was received, so voiding would hide it. A refund must come first (not built yet).
        if (payments.sumForInvoice(tenantId, id) != null) {
            throw new ConflictException("An invoice that has received payments cannot be voided");
        }
        invoice.voidInvoice();
        return InvoiceResponse.from(invoices.saveAndFlush(invoice));
    }

    private Invoice find(Long tenantId, Long id) {
        return invoices.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new NotFoundException("Invoice " + id + " not found"));
    }

    private Tenant tenant(Long tenantId) {
        return tenants.findById(tenantId)
                .orElseThrow(() -> new NotFoundException("Tenant " + tenantId + " not found"));
    }

    /** A line without a tax rate gets the business's rate (Day 21; on Day 20 it got 0). */
    private List<InvoiceLine> toLines(List<InvoiceLineRequest> requests, Tenant tenant) {
        if (requests == null) {
            return List.of();
        }
        return requests.stream()
                .map(r -> InvoiceLine.of(r.description(), r.quantity(), r.unitPrice(),
                        r.taxRate() == null ? tenant.getTaxRate() : r.taxRate()))
                .toList();
    }
}
