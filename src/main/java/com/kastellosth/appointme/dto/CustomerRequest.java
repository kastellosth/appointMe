package com.kastellosth.appointme.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;

public record CustomerRequest(
        @Schema(example = "Nikos Georgiou") @NotBlank @Size(max = 100) String fullName,
        @Schema(example = "nikos@example.com") @Email @Size(max = 255) String email,
        @Schema(example = "+30 210 000 0000") @Size(max = 30) String phone
) {}
