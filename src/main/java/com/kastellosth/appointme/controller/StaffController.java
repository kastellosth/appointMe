package com.kastellosth.appointme.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
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

@Tag(name = "3. Staff", description = "Team members of the current business.")
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