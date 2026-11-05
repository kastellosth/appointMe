package com.kastellosth.appointme.controller;

import com.kastellosth.appointme.dto.InvoiceLinesRequest;
import com.kastellosth.appointme.dto.InvoiceRequest;
import com.kastellosth.appointme.dto.InvoiceResponse;
import com.kastellosth.appointme.dto.InvoiceSummaryResponse;
import com.kastellosth.appointme.dto.PageResponse;
import com.kastellosth.appointme.entity.InvoiceStatus;
import com.kastellosth.appointme.security.CurrentUser;
import com.kastellosth.appointme.service.InvoiceService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;

@RestController
@RequestMapping("/invoices")
@PreAuthorize("hasRole('OWNER')")
public class InvoiceController {

    private final InvoiceService service;
    private final CurrentUser currentUser;

    public InvoiceController(InvoiceService service, CurrentUser currentUser) {
        this.service = service;
        this.currentUser = currentUser;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public InvoiceResponse create(@Valid @RequestBody InvoiceRequest request) {
        return service.create(currentUser.tenantId(), request);
    }

    /**
     * Day 21. Every filter is optional. Example:
     *   GET /invoices?status=DRAFT&customerId=1&page=0&size=20
     * Sorting is fixed (newest first) on purpose: a client cannot ask to sort by a column without an index.
     * The @Min/@Max on page and size are checked by Spring's built-in method validation -> 400.
     */
    @GetMapping
    public PageResponse<InvoiceSummaryResponse> list(
            @RequestParam(required = false) InvoiceStatus status,
            @RequestParam(required = false) Long customerId,
            @RequestParam(required = false) Long appointmentId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant createdFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant createdTo,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return service.list(currentUser.tenantId(), status, customerId, appointmentId,
                createdFrom, createdTo, page, size);
    }

    @GetMapping("/{id}")
    public InvoiceResponse get(@PathVariable Long id) {
        return service.get(currentUser.tenantId(), id);
    }

    @PutMapping("/{id}/lines")
    public InvoiceResponse replaceLines(@PathVariable Long id, @Valid @RequestBody InvoiceLinesRequest request) {
        return service.replaceLines(currentUser.tenantId(), id, request.lines());
    }

    @PostMapping("/{id}/issue")
    public InvoiceResponse issue(@PathVariable Long id) {
        return service.issue(currentUser.tenantId(), id);
    }

    @PostMapping("/{id}/void")
    public InvoiceResponse voidInvoice(@PathVariable Long id) {
        return service.voidInvoice(currentUser.tenantId(), id);
    }
}
