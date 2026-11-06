package com.kastellosth.appointme;

import com.kastellosth.appointme.service.IdempotencyService;
import com.kastellosth.appointme.service.ReminderMessage;
import com.kastellosth.appointme.service.ReminderRunner;
import com.kastellosth.appointme.service.ReminderSender;
import com.kastellosth.appointme.service.ReminderService;
import com.kastellosth.appointme.support.DatabaseTestBase;
import com.kastellosth.appointme.support.ParallelRunner;
import com.kastellosth.appointme.support.RaceTestData;
import com.kastellosth.appointme.support.RaceTestData.World;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(properties = {
        "spring.datasource.hikari.maximum-pool-size=30",
        "spring.datasource.hikari.minimum-idle=2"
})
@Import(ReminderJobTest.FakeSenderConfig.class)
class ReminderJobTest extends DatabaseTestBase {

    static class FakeSender implements ReminderSender {
        final Queue<ReminderMessage> sent = new ConcurrentLinkedQueue<>();
        volatile boolean failing;

        @Override
        public void send(ReminderMessage message) {
            if (failing) {
                throw new IllegalStateException("secret@example.com is unreachable");
            }
            sent.add(message);
        }
    }

    @TestConfiguration
    static class FakeSenderConfig {
        @Bean
        @Primary
        FakeSender fakeSender() {
            return new FakeSender();
        }
    }

    @Autowired
    private ReminderRunner runner;
    @Autowired
    private ReminderService service;
    @Autowired
    private IdempotencyService idempotency;
    @Autowired
    private FakeSender sender;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private PasswordEncoder passwordEncoder;

    private World world;

    @BeforeEach
    void setUp() {
        world = RaceTestData.create(jdbc, passwordEncoder);
        sender.sent.clear();
        sender.failing = false;
    }

    @AfterEach
    void cleanUp() {
        RaceTestData.delete(jdbc, world);
    }

    @Test
    void aDueAppointmentGetsExactlyOneReminder() {
        Instant now = Instant.now();
        Long appointment = appointmentAt(now.plus(Duration.ofHours(3)));

        runner.runOnce(now);
        runner.runOnce(now);

        assertEquals(1, sentFor(appointment));
        assertEquals("SENT", statusOf(appointment));
        assertEquals(1, count("SELECT count(*) FROM reminders WHERE appointment_id = ?", appointment));
    }

    @Test
    void anAppointmentOutsideTheWindowGetsNone() {
        Instant now = Instant.now();
        Long tooFar = appointmentAt(now.plus(Duration.ofHours(25)));
        Long past = appointmentAt(now.minus(Duration.ofHours(1)));

        runner.runOnce(now);

        assertEquals(0, count("SELECT count(*) FROM reminders WHERE appointment_id IN (?, ?)", tooFar, past));
    }

    @Test
    void aCancelledAppointmentIsSkipped() {
        Instant now = Instant.now();
        Long appointment = appointmentAt(now.plus(Duration.ofHours(3)));
        service.createDue(now);
        jdbc.update("UPDATE appointments SET status = 'CANCELLED' WHERE id = ?", appointment);

        runner.runOnce(now);

        assertEquals(0, sentFor(appointment));
        assertEquals("SKIPPED", statusOf(appointment));
        assertEquals("appointment is CANCELLED", reasonOf(appointment));
    }

    @Test
    void aRescheduledAppointmentGetsAReminderForTheNewTime() {
        Instant now = Instant.now();
        Long appointment = appointmentAt(now.plus(Duration.ofHours(3)));
        runner.runOnce(now);

        moveAppointment(appointment, now.plus(Duration.ofHours(5)));
        runner.runOnce(now);

        assertEquals(2, sentFor(appointment));
        assertEquals(2, count("SELECT count(*) FROM reminders WHERE appointment_id = ? AND status = 'SENT'", appointment));
    }

    @Test
    void aPendingReminderForTheOldTimeIsSkippedAfterAReschedule() {
        Instant now = Instant.now();
        Long appointment = appointmentAt(now.plus(Duration.ofHours(3)));
        service.createDue(now);

        moveAppointment(appointment, now.plus(Duration.ofHours(5)));
        runner.runOnce(now);

        assertEquals(1, sentFor(appointment));
        assertEquals(1, count("SELECT count(*) FROM reminders WHERE appointment_id = ? AND status = 'SKIPPED'"
                + " AND reason = 'appointment was rescheduled'", appointment));
    }

    @Test
    void aCustomerWithoutEmailIsSkipped() {
        Instant now = Instant.now();
        jdbc.update("UPDATE customers SET email = NULL WHERE id = ?", world.customerId());
        Long appointment = appointmentAt(now.plus(Duration.ofHours(3)));

        runner.runOnce(now);

        assertEquals("SKIPPED", statusOf(appointment));
        assertEquals("customer has no email", reasonOf(appointment));
    }

    @Test
    void theMessageShowsTheTimeAtTheBusiness() {
        jdbc.update("UPDATE tenants SET timezone = 'Asia/Tokyo' WHERE id = ?", world.tenantId());
        Instant now = Instant.now();
        Instant start = now.plus(Duration.ofHours(3));
        Long appointment = appointmentAt(start);

        runner.runOnce(now);

        ReminderMessage message = messagesFor(appointment).getFirst();
        String expected = start.atZone(java.time.ZoneId.of("Asia/Tokyo"))
                .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
        assertEquals(expected, message.localStartTime());
        assertEquals("Race Cut", message.serviceName());
    }

    @Test
    void aFailedSendIsRetriedLaterWithoutLeakingTheErrorText() {
        Instant now = Instant.now();
        Long appointment = appointmentAt(now.plus(Duration.ofHours(3)));
        sender.failing = true;

        runner.runOnce(now);

        assertEquals("PENDING", statusOf(appointment));
        assertEquals("IllegalStateException", reasonOf(appointment));

        sender.failing = false;
        runner.runOnce(now.plus(Duration.ofMinutes(4)));
        assertEquals(0, sentFor(appointment), "the retry is not due yet");

        runner.runOnce(now.plus(Duration.ofMinutes(5)));
        assertEquals(1, sentFor(appointment));
        assertEquals("SENT", statusOf(appointment));
        assertNull(reasonOf(appointment));
    }

    @Test
    void afterFiveFailuresItStopsAndTheLogSaysSo() {
        Instant now = Instant.now();
        Long appointment = appointmentAt(now.plus(Duration.ofHours(23)));
        sender.failing = true;

        Instant clock = now;
        for (int attempt = 1; attempt <= 5; attempt++) {
            runner.runOnce(clock);
            clock = clock.plus(Duration.ofHours(1));
        }

        assertEquals("FAILED", statusOf(appointment));
        sender.failing = false;
        runner.runOnce(clock);
        assertEquals(0, sentFor(appointment));
        assertEquals(1, auditCount(appointment, "REMINDER_FAILED"));
    }

    @Test
    void aReminderLeftBehindByACrashIsPickedUpAgain() {
        Instant now = Instant.now();
        Long appointment = appointmentAt(now.plus(Duration.ofHours(3)));
        service.createDue(now);
        service.claim(now, 1000);

        runner.runOnce(now.plus(Duration.ofMinutes(9)));
        assertEquals(0, sentFor(appointment), "the claim is still valid");

        runner.runOnce(now.plus(Duration.ofMinutes(11)));
        assertEquals(1, sentFor(appointment));
        assertEquals(2, count("SELECT attempts FROM reminders WHERE appointment_id = ?", appointment));
    }

    @Test
    void aReminderThatKeepsCrashingStopsAfterTheLimit() {
        Instant now = Instant.now();
        Long appointment = appointmentAt(now.plus(Duration.ofHours(3)));
        service.createDue(now);
        jdbc.update("UPDATE reminders SET status = 'SENDING', attempts = 5, claimed_at = ? WHERE appointment_id = ?",
                Timestamp.from(now.minus(Duration.ofHours(1))), appointment);

        runner.runOnce(now);

        assertEquals(0, sentFor(appointment));
        assertEquals("FAILED", statusOf(appointment));
    }

    @Test
    void aClaimRespectsItsBatchLimit() {
        Instant now = Instant.now();
        appointmentAt(now.plus(Duration.ofHours(3)));
        appointmentAt(now.plus(Duration.ofHours(4)));
        appointmentAt(now.plus(Duration.ofHours(5)));
        service.createDue(now);

        assertEquals(1, service.claim(now, 1).size());
        assertEquals(1, count("SELECT count(*) FROM reminders WHERE tenant_id = ? AND status = 'SENDING'", world.tenantId()));
    }

    @Test
    void fiveInstancesAtOnceSendEachReminderOnce() throws Exception {
        Instant now = Instant.now();
        List<Long> appointmentIds = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            appointmentIds.add(appointmentAt(now.plus(Duration.ofMinutes(60 + i * 30L))));
        }

        List<String> outcomes = ParallelRunner.runAtOnce(5, () -> {
            try {
                runner.runOnce(now);
                return "OK";
            } catch (Exception e) {
                return "UNEXPECTED " + e;
            }
        });

        assertTrue(outcomes.stream().allMatch("OK"::equals), outcomes.toString());
        Map<Long, Long> perReminder = sender.sent.stream()
                .collect(Collectors.groupingBy(ReminderMessage::reminderId, Collectors.counting()));
        for (Long appointment : appointmentIds) {
            assertEquals("SENT", statusOf(appointment));
            assertEquals(1, sentFor(appointment));
        }
        assertFalse(perReminder.values().stream().anyMatch(n -> n > 1), perReminder.toString());
    }

    @Test
    void theAuditLogShowsTheSystemDidIt() {
        Instant now = Instant.now();
        Long appointment = appointmentAt(now.plus(Duration.ofHours(3)));

        runner.runOnce(now);

        Map<String, Object> event = jdbc.queryForMap("SELECT actor_user_id, details FROM audit_events "
                + "WHERE tenant_id = ? AND entity_id = ? AND action = 'REMINDER_SENT'", world.tenantId(), appointment);
        assertNull(event.get("actor_user_id"));
        assertFalse(event.get("details").toString().contains("@"), event.toString());
    }

    @Test
    void aLateMarkSentChangesNothingAndAddsNoSecondEvent() {
        Instant now = Instant.now();
        Long appointment = appointmentAt(now.plus(Duration.ofHours(3)));
        runner.runOnce(now);
        Long reminderId = jdbc.queryForObject("SELECT id FROM reminders WHERE appointment_id = ?", Long.class, appointment);

        service.markSent(reminderId, now.plus(Duration.ofMinutes(1)));

        assertEquals(1, auditCount(appointment, "REMINDER_SENT"));
    }

    @Test
    void oldIdempotencyKeysAreDeletedAndRecentOnesStay() {
        Instant now = Instant.now();
        String oldKey = "old-" + UUID.randomUUID();
        String newKey = "new-" + UUID.randomUUID();
        insertKey(oldKey, now.minus(Duration.ofDays(8)));
        insertKey(newKey, now.minus(Duration.ofDays(6)));

        int deleted = idempotency.deleteOlderThan(now.minus(Duration.ofDays(7)), 1000);

        assertTrue(deleted >= 1);
        assertEquals(0, count("SELECT count(*) FROM idempotency_keys WHERE key = ?", oldKey));
        assertEquals(1, count("SELECT count(*) FROM idempotency_keys WHERE key = ?", newKey));
    }

    @Test
    void theCleanupRespectsItsBatchLimit() {
        Instant old = Instant.now().minus(Duration.ofDays(30));
        insertKey("old-" + UUID.randomUUID(), old);
        insertKey("old-" + UUID.randomUUID(), old);

        assertEquals(1, idempotency.deleteOlderThan(Instant.now().minus(Duration.ofDays(7)), 1));
    }

    private Long appointmentAt(Instant start) {
        return jdbc.queryForObject("""
                INSERT INTO appointments (tenant_id, staff_id, customer_id, service_id, starts_at, ends_at)
                VALUES (?, ?, ?, ?, ?, ?) RETURNING id
                """, Long.class, world.tenantId(), world.staffId(), world.customerId(), world.serviceId(),
                Timestamp.from(start), Timestamp.from(start.plus(Duration.ofMinutes(30))));
    }

    private void moveAppointment(Long appointment, Instant start) {
        jdbc.update("UPDATE appointments SET starts_at = ?, ends_at = ? WHERE id = ?", Timestamp.from(start),
                Timestamp.from(start.plus(Duration.ofMinutes(30))), appointment);
    }

    private void insertKey(String key, Instant createdAt) {
        jdbc.update("""
                INSERT INTO idempotency_keys (tenant_id, key, operation, request_hash, created_at)
                VALUES (?, ?, 'test', 'hash', ?)
                """, world.tenantId(), key, Timestamp.from(createdAt));
    }

    private List<ReminderMessage> messagesFor(Long appointment) {
        List<Long> ids = jdbc.queryForList("SELECT id FROM reminders WHERE appointment_id = ?", Long.class, appointment);
        return sender.sent.stream().filter(m -> ids.contains(m.reminderId())).toList();
    }

    private int sentFor(Long appointment) {
        return messagesFor(appointment).size();
    }

    private String statusOf(Long appointment) {
        return jdbc.queryForObject("SELECT status FROM reminders WHERE appointment_id = ?", String.class, appointment);
    }

    private String reasonOf(Long appointment) {
        return jdbc.queryForObject("SELECT reason FROM reminders WHERE appointment_id = ?", String.class, appointment);
    }

    private int auditCount(Long appointment, String action) {
        return count("SELECT count(*) FROM audit_events WHERE entity_type = 'APPOINTMENT' AND entity_id = ? AND action = '"
                + action + "'", appointment);
    }

    private int count(String sql, Object... args) {
        return jdbc.queryForObject(sql, Integer.class, args);
    }
}
