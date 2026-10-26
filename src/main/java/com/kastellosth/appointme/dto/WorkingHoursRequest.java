package com.kastellosth.appointme.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record WorkingHoursRequest(
        @NotNull @Size(max = 50) List<@NotNull @Valid ScheduleInterval> intervals) {
}