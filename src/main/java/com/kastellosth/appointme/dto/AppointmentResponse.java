package com.kastellosth.appointme.dto;

import com.kastellosth.appointme.entity.AppointmentStatus;

import java.time.Instant;

public record AppointmentResponse(
        Long id,
        Long staffId, String staffName,
        Long customerId, String customerName,
        Long serviceId, String serviceName,
        Instant startsAt, Instant endsAt,
        AppointmentStatus status, String notes,
        Instant createdAt
) {}
