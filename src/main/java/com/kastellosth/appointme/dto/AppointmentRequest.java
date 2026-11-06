package com.kastellosth.appointme.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public record AppointmentRequest(
        @Schema(example = "1") @NotNull Long staffId,
        @Schema(example = "1") @NotNull Long customerId,
        @Schema(example = "1") @NotNull Long serviceId,
        @Schema(example = "2030-01-15T10:00:00Z") @NotNull @Future Instant startsAt,
        @Schema(example = "First visit") @Size(max = 1000) String notes
) {}
