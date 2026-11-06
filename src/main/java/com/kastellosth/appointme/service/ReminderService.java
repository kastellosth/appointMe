package com.kastellosth.appointme.service;

import com.kastellosth.appointme.dto.PageResponse;
import com.kastellosth.appointme.dto.ReminderResponse;
import com.kastellosth.appointme.entity.Appointment;
import com.kastellosth.appointme.entity.AppointmentStatus;
import com.kastellosth.appointme.entity.AuditAction;
import com.kastellosth.appointme.entity.Reminder;
import com.kastellosth.appointme.entity.ReminderStatus;
import com.kastellosth.appointme.repository.AppointmentRepository;
import com.kastellosth.appointme.repository.ReminderFilters;
import com.kastellosth.appointme.repository.ReminderRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

@Service
public class ReminderService {

    static final int MAX_ATTEMPTS = 5;
    private static final Duration LEAD_TIME = Duration.ofHours(24);
    private static final Duration CLAIM_EXPIRES_AFTER = Duration.ofMinutes(10);
    private static final Duration FIRST_RETRY_DELAY = Duration.ofMinutes(5);
    private static final DateTimeFormatter LOCAL_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final Sort NEWEST_FIRST = Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"));

    private final ReminderRepository reminders;
    private final AppointmentRepository appointments;
    private final AuditService audit;
    private final JdbcTemplate jdbc;

    public ReminderService(ReminderRepository reminders, AppointmentRepository appointments, AuditService audit,
                           JdbcTemplate jdbc) {
        this.reminders = reminders;
        this.appointments = appointments;
        this.audit = audit;
        this.jdbc = jdbc;
    }

    @Transactional
    public int createDue(Instant now) {
        // ORDER BY: two app instances insert in the same order, so they cannot deadlock on the unique key
        return jdbc.update("""
                INSERT INTO reminders (tenant_id, appointment_id, appointment_starts_at, next_attempt_at)
                SELECT a.tenant_id, a.id, a.starts_at, ?
                  FROM appointments a
                 WHERE a.status = 'SCHEDULED' AND a.starts_at > ? AND a.starts_at <= ?
                 ORDER BY a.id
                ON CONFLICT (appointment_id, appointment_starts_at) DO NOTHING
                """, ts(now), ts(now), ts(now.plus(LEAD_TIME)));
    }

    /** MATERIALIZED: inside "IN (subquery)" PostgreSQL may run the subquery again and exceed the LIMIT. */
    @Transactional
    public List<Long> claim(Instant now, int limit) {
        return jdbc.queryForList("""
                WITH picked AS MATERIALIZED (
                    SELECT id FROM reminders
                     WHERE (status = 'PENDING' AND next_attempt_at <= ?)
                        OR (status = 'SENDING' AND claimed_at < ?)
                     ORDER BY next_attempt_at
                     LIMIT ?
                       FOR UPDATE SKIP LOCKED)
                UPDATE reminders r
                   SET status = 'SENDING', attempts = r.attempts + 1, claimed_at = ?
                  FROM picked
                 WHERE r.id = picked.id
                RETURNING r.id
                """, Long.class, ts(now), ts(now.minus(CLAIM_EXPIRES_AFTER)), limit, ts(now));
    }

    /** Empty means there is nothing to send, and the reminder has already been closed. */
    @Transactional
    public Optional<ReminderMessage> prepare(Long id, Instant now) {
        Reminder reminder = find(id);
        if (reminder.getAttempts() > MAX_ATTEMPTS) {
            fail(reminder, "crashed while sending");
            return Optional.empty();
        }
        Appointment a = appointments.findByIdAndTenantId(reminder.getAppointmentId(), reminder.getTenantId())
                .orElseThrow(() -> new IllegalStateException("Reminder " + id + " has no appointment"));
        String skipReason = skipReason(reminder, a, now);
        if (skipReason != null) {
            close(id, ReminderStatus.SKIPPED, skipReason);
            return Optional.empty();
        }
        String when = a.getStartsAt().atZone(ZoneId.of(a.getTenant().getTimezone())).format(LOCAL_TIME);
        return Optional.of(new ReminderMessage(id, a.getCustomer().getEmail(), a.getTenant().getName(),
                a.getService().getName(), when));
    }

    @Transactional
    public void markSent(Long id, Instant now) {
        int moved = jdbc.update("""
                UPDATE reminders SET status = 'SENT', sent_at = ?, claimed_at = NULL, reason = NULL
                 WHERE id = ? AND status = 'SENDING'
                """, ts(now), id);
        if (moved == 1) {
            Reminder reminder = find(id);
            audit.record(reminder.getTenantId(), AuditAction.REMINDER_SENT, reminder.getAppointmentId(),
                    "reminder " + id + ", attempt " + reminder.getAttempts());
        }
    }

    /** @param error an exception's class name, never its message: messages can contain addresses */
    @Transactional
    public void markFailed(Long id, String error, Instant now) {
        Reminder reminder = find(id);
        if (reminder.getAttempts() >= MAX_ATTEMPTS) {
            fail(reminder, error);
            return;
        }
        Duration delay = FIRST_RETRY_DELAY.multipliedBy(1L << (reminder.getAttempts() - 1));
        jdbc.update("""
                UPDATE reminders SET status = 'PENDING', next_attempt_at = ?, claimed_at = NULL, reason = ?
                 WHERE id = ? AND status = 'SENDING'
                """, ts(now.plus(delay)), error, id);
    }

    @Transactional(readOnly = true)
    public PageResponse<ReminderResponse> list(Long tenantId, ReminderStatus status, Long appointmentId,
                                               int page, int size) {
        var result = reminders.findAll(ReminderFilters.of(tenantId, status, appointmentId),
                PageRequest.of(page, size, NEWEST_FIRST));
        return PageResponse.from(result.map(ReminderResponse::from));
    }

    private String skipReason(Reminder reminder, Appointment a, Instant now) {
        if (a.getStatus() != AppointmentStatus.SCHEDULED) {
            return "appointment is " + a.getStatus();
        }
        if (!a.getStartsAt().equals(reminder.getAppointmentStartsAt())) {
            return "appointment was rescheduled";
        }
        if (!a.getStartsAt().isAfter(now)) {
            return "appointment has already started";
        }
        if (a.getCustomer().getEmail() == null) {
            return "customer has no email";
        }
        return null;
    }

    private void fail(Reminder reminder, String reason) {
        if (close(reminder.getId(), ReminderStatus.FAILED, reason) == 1) {
            audit.record(reminder.getTenantId(), AuditAction.REMINDER_FAILED, reminder.getAppointmentId(),
                    "reminder " + reminder.getId() + ", attempts " + reminder.getAttempts() + ", " + reason);
        }
    }

    private int close(Long id, ReminderStatus status, String reason) {
        return jdbc.update("""
                UPDATE reminders SET status = ?, claimed_at = NULL, reason = ?
                 WHERE id = ? AND status = 'SENDING'
                """, status.name(), reason, id);
    }

    private Reminder find(Long id) {
        return reminders.findById(id).orElseThrow(() -> new IllegalStateException("Reminder " + id + " is missing"));
    }

    private static Timestamp ts(Instant instant) {
        return Timestamp.from(instant);
    }
}
