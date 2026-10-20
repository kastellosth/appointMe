package com.kastellosth.appointme.controller;

import com.kastellosth.appointme.dto.CustomerRequest;
import com.kastellosth.appointme.dto.CustomerResponse;
import com.kastellosth.appointme.dto.PageResponse;
import com.kastellosth.appointme.security.CurrentUser;
import com.kastellosth.appointme.service.CustomerService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
@RestController
@RequestMapping("/customers")
@RequiredArgsConstructor
public class CustomerController {

    private final CustomerService service;
    private final CurrentUser currentUser;

    @PostMapping
    public ResponseEntity<CustomerResponse> create(@Valid @RequestBody CustomerRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(service.create(currentUser.tenantId(), req));
    }

    @GetMapping
    public PageResponse<CustomerResponse> list(
            @RequestParam(required = false) String name,
            @PageableDefault(size = 20, sort = "fullName") Pageable pageable) {
        return service.list(currentUser.tenantId(), name, pageable);
    }

    @GetMapping("/{id}")
    public CustomerResponse get(@PathVariable Long id) {
        return service.get(currentUser.tenantId(), id);
    }

    @PutMapping("/{id}")
    public CustomerResponse update(@PathVariable Long id,
                                   @Valid @RequestBody CustomerRequest req) {
        return service.update(currentUser.tenantId(), id, req);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(currentUser.tenantId(), id);
        return ResponseEntity.noContent().build();
    }
}