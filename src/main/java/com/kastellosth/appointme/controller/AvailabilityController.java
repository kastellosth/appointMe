package com.kastellosth.appointme.controller;

import com.kastellosth.appointme.dto.AvailabilityResponse;
import com.kastellosth.appointme.entity.MembershipRole;
import com.kastellosth.appointme.security.CurrentUser;
import com.kastellosth.appointme.service.AvailabilityService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/availability")
@PreAuthorize("hasAnyRole('OWNER','STAFF')")
public class AvailabilityController {

    private final AvailabilityService service;
    private final CurrentUser currentUser;

    public AvailabilityController(AvailabilityService service, CurrentUser currentUser) {
        this.service = service;
        this.currentUser = currentUser;
    }

    @GetMapping
    public AvailabilityResponse get(
            @RequestParam Long staffId,
            @RequestParam Long serviceId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(defaultValue = "15") int stepMinutes) {
        return service.find(currentUser.tenantId(), staffScope(), staffId, serviceId, date, stepMinutes);
    }

    // Same helper as in AppointmentController: null for owners, own membership id for staff
    private Long staffScope() {
        return currentUser.role() == MembershipRole.STAFF ? currentUser.membershipId() : null;
    }
}