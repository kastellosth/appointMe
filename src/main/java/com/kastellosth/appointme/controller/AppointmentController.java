package com.kastellosth.appointme.controller;

import com.kastellosth.appointme.dto.AppointmentRequest;
import com.kastellosth.appointme.dto.AppointmentResponse;
import com.kastellosth.appointme.dto.StatusUpdateRequest;
import com.kastellosth.appointme.servicie.AppointmentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/tenants/{tenantId}/appointments")
@RequiredArgsConstructor
public class AppointmentController {

    private final AppointmentService service;

    @PostMapping
    public ResponseEntity<AppointmentResponse> create(@PathVariable Long tenantId,
                                                      @Valid @RequestBody AppointmentRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(tenantId, req));
    }

    @GetMapping
    public List<AppointmentResponse> list(@PathVariable Long tenantId,
                                          @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
                                          @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {
        return service.list(tenantId, from, to);
    }

    @GetMapping("/{id}")
    public AppointmentResponse get(@PathVariable Long tenantId, @PathVariable Long id) {
        return service.get(tenantId, id);
    }

    @PatchMapping("/{id}/status")
    public AppointmentResponse updateStatus(@PathVariable Long tenantId, @PathVariable Long id,
                                            @Valid @RequestBody StatusUpdateRequest req) {
        return service.updateStatus(tenantId, id, req.status());
    }
}