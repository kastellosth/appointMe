package com.kastellosth.appointme.controller;

import com.kastellosth.appointme.dto.InvoicePaymentsResponse;
import com.kastellosth.appointme.dto.PaymentRequest;
import com.kastellosth.appointme.dto.PaymentResponse;
import com.kastellosth.appointme.security.CurrentUser;
import com.kastellosth.appointme.service.PaymentService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/invoices/{invoiceId}/payments")
@PreAuthorize("hasRole('OWNER')")
public class PaymentController {

    public static final String IDEMPOTENCY_KEY = "Idempotency-Key";
    public static final String REPLAYED = "Idempotent-Replayed";

    private final PaymentService service;
    private final CurrentUser currentUser;

    public PaymentController(PaymentService service, CurrentUser currentUser) {
        this.service = service;
        this.currentUser = currentUser;
    }

    /**
     * The key format is checked in the service: @Pattern on this parameter next to @Valid would switch
     * Spring to method validation and lose the per-field "errors" map.
     */
    @PostMapping
    public ResponseEntity<PaymentResponse> record(@PathVariable Long invoiceId,
                                                  @RequestHeader(IDEMPOTENCY_KEY) String key,
                                                  @Valid @RequestBody PaymentRequest request) {
        PaymentService.Result result = service.record(currentUser.tenantId(), invoiceId, key, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .header(REPLAYED, String.valueOf(result.replayed()))
                .body(result.payment());
    }

    @GetMapping
    public InvoicePaymentsResponse list(@PathVariable Long invoiceId) {
        return service.list(currentUser.tenantId(), invoiceId);
    }
}
