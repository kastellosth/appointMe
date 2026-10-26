package com.kastellosth.appointme.dto;

import jakarta.validation.constraints.NotNull;

import java.time.DayOfWeek;
import java.time.LocalTime;

public record ScheduleInterval(
        @NotNull DayOfWeek dayOfWeek,
        @NotNull LocalTime start,
        @NotNull LocalTime end) {
}