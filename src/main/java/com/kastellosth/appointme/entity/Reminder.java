package com.kastellosth.appointme.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;

import java.time.Instant;

@Entity
@Immutable
@Table(name = "reminders")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Reminder {

    @Id
    private Long id;

    @Column(name = "tenant_id")
    private Long tenantId;

    @Column(name = "appointment_id")
    private Long appointmentId;

    @Column(name = "appointment_starts_at")
    private Instant appointmentStartsAt;

    @Enumerated(EnumType.STRING)
    private ReminderStatus status;

    private int attempts;

    @Column(name = "next_attempt_at")
    private Instant nextAttemptAt;

    @Column(name = "claimed_at")
    private Instant claimedAt;

    @Column(name = "sent_at")
    private Instant sentAt;

    private String reason;

    @Column(name = "created_at")
    private Instant createdAt;
}
