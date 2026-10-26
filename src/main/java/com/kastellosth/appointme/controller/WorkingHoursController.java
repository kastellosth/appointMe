package com.kastellosth.appointme.controller;

import com.kastellosth.appointme.dto.WorkingHoursRequest;
import com.kastellosth.appointme.dto.WorkingHoursResponse;
import com.kastellosth.appointme.security.CurrentUser;
import com.kastellosth.appointme.service.WorkingHoursService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/staff/{staffId}/working-hours")
@PreAuthorize("hasRole('OWNER')")
public class WorkingHoursController {

    private final WorkingHoursService service;
    private final CurrentUser currentUser;

    public WorkingHoursController(WorkingHoursService service, CurrentUser currentUser) {
        this.service = service;
        this.currentUser = currentUser;
    }

    @GetMapping
    public WorkingHoursResponse get(@PathVariable Long staffId) {
        return service.get(currentUser.tenantId(), staffId);
    }

    @PutMapping
    public WorkingHoursResponse replace(@PathVariable Long staffId,
                                        @Valid @RequestBody WorkingHoursRequest request) {
        return service.replace(currentUser.tenantId(), staffId, request);
    }
}