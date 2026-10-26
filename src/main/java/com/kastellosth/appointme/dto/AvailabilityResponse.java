package com.kastellosth.appointme.dto;

import java.time.LocalDate;
import java.util.List;

public record AvailabilityResponse(
        Long staffId,
        Long serviceId,
        LocalDate date,
        String timezone,
        int durationMinutes,
        int stepMinutes,
        List<SlotResponse> slots) {
}