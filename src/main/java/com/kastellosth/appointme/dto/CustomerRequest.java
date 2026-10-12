package com.kastellosth.appointme.dto;

import jakarta.validation.constraints.*;

public record CustomerRequest(
        @NotBlank @Size(max = 100) String fullName,
        @Email @Size(max = 255) String email,
        @Size(max = 30) String phone
) {}
