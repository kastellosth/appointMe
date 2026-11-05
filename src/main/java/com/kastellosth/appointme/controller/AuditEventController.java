package com.kastellosth.appointme.controller;

import com.kastellosth.appointme.dto.AuditEventResponse;
import com.kastellosth.appointme.dto.PageResponse;
import com.kastellosth.appointme.entity.AuditAction;
import com.kastellosth.appointme.entity.AuditEntityType;
import com.kastellosth.appointme.security.CurrentUser;
import com.kastellosth.appointme.service.AuditService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

/** Read-only on purpose: there is no way to create, change or delete an event over HTTP. */
@RestController
@RequestMapping("/audit-events")
@PreAuthorize("hasRole('OWNER')")
public class AuditEventController {

    private final AuditService service;
    private final CurrentUser currentUser;

    public AuditEventController(AuditService service, CurrentUser currentUser) {
        this.service = service;
        this.currentUser = currentUser;
    }

    /** Example, the whole story of one invoice: GET /audit-events?entityType=INVOICE&entityId=5 */
    @GetMapping
    public PageResponse<AuditEventResponse> list(
            @RequestParam(required = false) AuditEntityType entityType,
            @RequestParam(required = false) Long entityId,
            @RequestParam(required = false) AuditAction action,
            @RequestParam(required = false) Long actorUserId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return service.list(currentUser.tenantId(), entityType, entityId, action, actorUserId, from, to, page, size);
    }
}
