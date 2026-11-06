package com.kastellosth.appointme.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;

public record TenantRequest(
        @Schema(example = "Athens Barber") @NotBlank @Size(max = 150) String name,
        @Schema(example = "athens-barber") @NotBlank @Pattern(regexp = "^[a-z0-9-]+$") @Size(max = 80) String slug,
        @Schema(example = "Europe/Athens") @Size(max = 50) String timezone
) {
}