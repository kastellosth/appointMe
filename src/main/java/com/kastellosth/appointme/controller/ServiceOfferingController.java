package com.kastellosth.appointme.controller;

import com.kastellosth.appointme.dto.ServiceOfferingRequest;
import com.kastellosth.appointme.dto.ServiceOfferingResponse;
import com.kastellosth.appointme.security.CurrentUser;
import com.kastellosth.appointme.service.ServiceOfferingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
@RestController
@RequestMapping("/services")
@RequiredArgsConstructor
public class ServiceOfferingController {

    private final ServiceOfferingService service;
    private final CurrentUser currentUser;

    @PostMapping
    public ResponseEntity<ServiceOfferingResponse> create(
            @Valid @RequestBody ServiceOfferingRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(service.create(currentUser.tenantId(), req));
    }

    @GetMapping
    public List<ServiceOfferingResponse> list() {
        return service.list(currentUser.tenantId());
    }

    @GetMapping("/{id}")
    public ServiceOfferingResponse get(@PathVariable Long id) {
        return service.get(currentUser.tenantId(), id);
    }

    @PutMapping("/{id}")
    public ServiceOfferingResponse update(@PathVariable Long id,
                                          @Valid @RequestBody ServiceOfferingRequest req) {
        return service.update(currentUser.tenantId(), id, req);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.deactivate(currentUser.tenantId(), id);
        return ResponseEntity.noContent().build();
    }
}