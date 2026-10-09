package com.kastellosth.appointme.dto;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;

public record ServiceOfferingRequest(
        @NotBlank @Size(max = 150) String name,
        @NotNull @Positive Integer durationMinutes,
        @NotNull @DecimalMin("0.00") BigDecimal price,
        Boolean active
) {}