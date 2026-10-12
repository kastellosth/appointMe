package com.kastellosth.appointme.dto;

import java.time.Instant;

public record CustomerResponse(
        Long id, String fullName, String email, String phone, Instant createdAt
) {}