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
     * The header is REQUIRED: a missing header is 400 (Spring's MissingRequestHeaderException).
     * Its format is checked in the service (BadRequestException), not with @Pattern here: a constraint
     * on a plain parameter switches Spring to "method validation", and then @Valid body errors arrive as
     * a different exception, without the per-field "errors" map our handler builds.
     *
     * A replay answers with the SAME status (201) and body as the first time, plus Idempotent-Replayed: true.
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
