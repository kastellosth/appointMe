package com.kastellosth.appointme.dto;

import com.kastellosth.appointme.entity.AppointmentStatus;
import jakarta.validation.constraints.NotNull;

public record StatusUpdateRequest(
        @NotNull AppointmentStatus status
) {}
