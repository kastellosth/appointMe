package com.kastellosth.appointme.dto;

import java.time.Instant;

public record TenantResponse(
        Long id,
        String name,
        String slug,
        String timezone,
        Instant createdAt
) {
}