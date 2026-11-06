package com.kastellosth.appointme.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequest(
        @Schema(example = "owner@example.com") @NotBlank @Size(max = 255) String email,
        @Schema(example = "correct-horse-1") @NotBlank @Size(max = 72) String password
) {
    @Override
    public String toString() {
        return "LoginRequest[email=" + email + "]";
    }
}