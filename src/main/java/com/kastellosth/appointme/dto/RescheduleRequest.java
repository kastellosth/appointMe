package com.kastellosth.appointme.dto;

import jakarta.validation.constraints.NotNull;

import java.time.Instant;

public record RescheduleRequest(@NotNull Instant startsAt) {
}