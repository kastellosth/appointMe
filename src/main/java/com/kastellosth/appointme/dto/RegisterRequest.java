package com.kastellosth.appointme.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @Schema(example = "owner@example.com") @NotBlank @Email @Size(max = 255) String email,
        @Schema(example = "correct-horse-1") @NotBlank @Size(min = 8, max = 72) String password,
        @Schema(example = "Maria Papadopoulou") @NotBlank @Size(max = 100) String fullName
) {
    @Override
    public String toString() {
        return "RegisterRequest[email=" + email + "]";
    }
}