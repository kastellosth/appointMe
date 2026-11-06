package com.kastellosth.appointme.controller;

import com.kastellosth.appointme.dto.TenantBillingRequest;
import com.kastellosth.appointme.dto.TenantRequest;
import com.kastellosth.appointme.dto.TenantResponse;
import com.kastellosth.appointme.security.CurrentUser;
import com.kastellosth.appointme.service.TenantService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
@RestController
@RequestMapping("/tenants")
@RequiredArgsConstructor
public class TenantController {

    private final TenantService tenantService;
    private final CurrentUser currentUser;

    @PostMapping
    public ResponseEntity<TenantResponse> create(@Valid @RequestBody TenantRequest request) {
        TenantResponse created = tenantService.create(currentUser.userId(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping("/current")
    public TenantResponse current() {
        return tenantService.getById(currentUser.tenantId());
    }

    @PutMapping("/current/billing")
    @PreAuthorize("hasRole('OWNER')")
    public TenantResponse updateBilling(@Valid @RequestBody TenantBillingRequest request) {
        return tenantService.updateBilling(currentUser.tenantId(), request);
    }
}
