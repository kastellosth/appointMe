package com.kastellosth.appointme.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;

public record ServiceOfferingRequest(
        @Schema(example = "Haircut") @NotBlank @Size(max = 150) String name,
        @Schema(example = "30") @NotNull @Positive Integer durationMinutes,
        @Schema(example = "25.00") @NotNull @DecimalMin("0.00") BigDecimal price,
        Boolean active
) {}