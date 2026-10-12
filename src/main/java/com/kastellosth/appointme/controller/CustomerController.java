package com.kastellosth.appointme.controller;

import com.kastellosth.appointme.dto.CustomerRequest;
import com.kastellosth.appointme.dto.CustomerResponse;
import com.kastellosth.appointme.dto.PageResponse;
import com.kastellosth.appointme.service.CustomerService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/tenants/{tenantId}/customers")
@RequiredArgsConstructor
public class CustomerController {

    private final CustomerService service;

    @PostMapping
    public ResponseEntity<CustomerResponse> create(@PathVariable Long tenantId,
                                                   @Valid @RequestBody CustomerRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(tenantId, req));
    }

    @GetMapping
    public PageResponse<CustomerResponse> list(@PathVariable Long tenantId,
                                               @RequestParam(required = false) String name,
                                               @PageableDefault(size = 20, sort = "fullName") Pageable pageable) {
        return service.list(tenantId, name, pageable);
    }

    @GetMapping("/{id}")
    public CustomerResponse get(@PathVariable Long tenantId, @PathVariable Long id) {
        return service.get(tenantId, id);
    }

    @PutMapping("/{id}")
    public CustomerResponse update(@PathVariable Long tenantId, @PathVariable Long id,
                                   @Valid @RequestBody CustomerRequest req) {
        return service.update(tenantId, id, req);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long tenantId, @PathVariable Long id) {
        service.delete(tenantId, id);
        return ResponseEntity.noContent().build();
    }
}
