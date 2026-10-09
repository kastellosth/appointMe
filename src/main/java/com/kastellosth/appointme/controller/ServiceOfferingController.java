package com.kastellosth.appointme.controller;

import com.kastellosth.appointme.dto.ServiceOfferingRequest;
import com.kastellosth.appointme.dto.ServiceOfferingResponse;
import com.kastellosth.appointme.servicie.ServiceOfferingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/tenants/{tenantId}/services")
@RequiredArgsConstructor
public class ServiceOfferingController {

    private final ServiceOfferingService service;

    @PostMapping
    public ResponseEntity<ServiceOfferingResponse> create(
            @PathVariable Long tenantId, @Valid @RequestBody ServiceOfferingRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(tenantId, req));
    }

    @GetMapping
    public List<ServiceOfferingResponse> list(@PathVariable Long tenantId) {
        return service.list(tenantId);
    }

    @GetMapping("/{id}")
    public ServiceOfferingResponse get(@PathVariable Long tenantId, @PathVariable Long id) {
        return service.get(tenantId, id);
    }

    @PutMapping("/{id}")
    public ServiceOfferingResponse update(@PathVariable Long tenantId, @PathVariable Long id,
                                          @Valid @RequestBody ServiceOfferingRequest req) {
        return service.update(tenantId, id, req);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long tenantId, @PathVariable Long id) {
        service.deactivate(tenantId, id);
        return ResponseEntity.noContent().build();
    }
}