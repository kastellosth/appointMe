package com.kastellosth.appointme.dto;

import jakarta.validation.constraints.*;


public record TenantRequest(
        @NotBlank @Size(max = 150) String name,
        @NotBlank @Pattern(regexp = "^[a-z0-9-]+$") @Size(max = 80) String slug,
        @Size(max = 50) String timezone
) {
}