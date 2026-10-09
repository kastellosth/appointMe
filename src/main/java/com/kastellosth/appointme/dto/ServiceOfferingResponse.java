package com.kastellosth.appointme.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record ServiceOfferingResponse(
        Long id, String name, int durationMinutes,
        BigDecimal price, boolean active, Instant createdAt
) {}