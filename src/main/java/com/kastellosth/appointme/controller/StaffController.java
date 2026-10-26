package com.kastellosth.appointme.controller;

import com.kastellosth.appointme.dto.StaffRequest;
import com.kastellosth.appointme.dto.StaffResponse;
import com.kastellosth.appointme.security.CurrentUser;
import com.kastellosth.appointme.service.StaffService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/staff")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('OWNER','STAFF')")
public class StaffController {

    private final StaffService service;
    private final CurrentUser currentUser;

    @GetMapping
    public List<StaffResponse> list() {
        return service.list(currentUser.tenantId());
    }

    @PostMapping
    @PreAuthorize("hasRole('OWNER')")
    public ResponseEntity<StaffResponse> add(@Valid @RequestBody StaffRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(service.add(currentUser.tenantId(), req.email()));
    }
}