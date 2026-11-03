package com.kastellosth.appointme.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record TenantResponse(
        Long id,
        String name,
        String slug,
        String timezone,
        BigDecimal taxRate,
        String currency,
        Instant createdAt
) {
}