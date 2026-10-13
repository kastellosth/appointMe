package com.kastellosth.appointme.dto;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public record AppointmentRequest(
        @NotNull Long staffId,
        @NotNull Long customerId,
        @NotNull Long serviceId,
        @NotNull @Future Instant startsAt,
        @Size(max = 1000) String notes
) {}
