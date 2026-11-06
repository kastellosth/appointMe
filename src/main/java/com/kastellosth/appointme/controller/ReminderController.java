package com.kastellosth.appointme.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import com.kastellosth.appointme.dto.PageResponse;
import com.kastellosth.appointme.dto.ReminderResponse;
import com.kastellosth.appointme.entity.ReminderStatus;
import com.kastellosth.appointme.security.CurrentUser;
import com.kastellosth.appointme.service.ReminderService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "9. Audit and reminders", description = "Read-only history. OWNER only.")
@RestController
@RequestMapping("/reminders")
@PreAuthorize("hasRole('OWNER')")
public class ReminderController {

    private final ReminderService service;
    private final CurrentUser currentUser;

    public ReminderController(ReminderService service, CurrentUser currentUser) {
        this.service = service;
        this.currentUser = currentUser;
    }

    @GetMapping
    public PageResponse<ReminderResponse> list(
            @RequestParam(required = false) ReminderStatus status,
            @RequestParam(required = false) Long appointmentId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return service.list(currentUser.tenantId(), status, appointmentId, page, size);
    }
}
