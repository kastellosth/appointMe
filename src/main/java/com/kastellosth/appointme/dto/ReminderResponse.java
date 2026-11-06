package com.kastellosth.appointme.dto;

import com.kastellosth.appointme.entity.Reminder;
import com.kastellosth.appointme.entity.ReminderStatus;

import java.time.Instant;

public record ReminderResponse(
        Long id,
        Long appointmentId,
        Instant appointmentStartsAt,
        ReminderStatus status,
        int attempts,
        Instant nextAttemptAt,
        Instant sentAt,
        String reason,
        Instant createdAt
) {
    public static ReminderResponse from(Reminder r) {
        return new ReminderResponse(r.getId(), r.getAppointmentId(), r.getAppointmentStartsAt(), r.getStatus(),
                r.getAttempts(), r.getNextAttemptAt(), r.getSentAt(), r.getReason(), r.getCreatedAt());
    }
}
