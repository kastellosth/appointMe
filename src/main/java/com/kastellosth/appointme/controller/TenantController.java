package com.kastellosth.appointme.controller;

import com.kastellosth.appointme.dto.TenantRequest;
import com.kastellosth.appointme.dto.TenantResponse;
import com.kastellosth.appointme.service.TenantService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/tenants")
@RequiredArgsConstructor
public class TenantController {

    private final TenantService tenantService;

    @PostMapping
    public ResponseEntity<TenantResponse> create(@RequestBody TenantRequest request) {
        TenantResponse created = tenantService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping("/{id}")
    public TenantResponse getById(@PathVariable Long id) {
        return tenantService.getById(id);
    }
}