package com.kastellosth.appointme.controller;

import com.kastellosth.appointme.dto.AppointmentRequest;
import com.kastellosth.appointme.dto.AppointmentResponse;
import com.kastellosth.appointme.dto.StatusUpdateRequest;
import com.kastellosth.appointme.security.CurrentUser;
import com.kastellosth.appointme.service.AppointmentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
@RestController
@RequestMapping("/appointments")
@RequiredArgsConstructor
public class AppointmentController {

    private final AppointmentService service;
    private final CurrentUser currentUser;

    @PostMapping
    public ResponseEntity<AppointmentResponse> create(
            @Valid @RequestBody AppointmentRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(service.create(currentUser.tenantId(), req));
    }

    @GetMapping
    public List<AppointmentResponse> list(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {
        return service.list(currentUser.tenantId(), from, to);
    }

    @GetMapping("/{id}")
    public AppointmentResponse get(@PathVariable Long id) {
        return service.get(currentUser.tenantId(), id);
    }

    @PatchMapping("/{id}/status")
    public AppointmentResponse updateStatus(@PathVariable Long id,
                                            @Valid @RequestBody StatusUpdateRequest req) {
        return service.updateStatus(currentUser.tenantId(), id, req.status());
    }
}