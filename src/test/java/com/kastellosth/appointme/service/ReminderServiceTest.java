package com.kastellosth.appointme.service;

import com.kastellosth.appointme.entity.Appointment;
import com.kastellosth.appointme.entity.AppointmentStatus;
import com.kastellosth.appointme.entity.AuditAction;
import com.kastellosth.appointme.entity.Customer;
import com.kastellosth.appointme.entity.Reminder;
import com.kastellosth.appointme.entity.ServiceOffering;
import com.kastellosth.appointme.entity.Tenant;
import com.kastellosth.appointme.repository.AppointmentRepository;
import com.kastellosth.appointme.repository.ReminderRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.BeanUtils;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReminderServiceTest {

    private static final long ID = 7L;
    private static final long TENANT = 1L;
    private static final long APPOINTMENT = 40L;
    private static final Instant NOW = Instant.parse("2026-07-01T09:00:00Z");
    private static final Instant STARTS = Instant.parse("2026-07-02T07:00:00Z");

    @Mock
    private ReminderRepository repository;

    @Mock
    private AppointmentRepository appointments;

    @Mock
    private AuditService audit;

    @Mock
    private JdbcTemplate jdbc;

    @InjectMocks
    private ReminderService service;

    private void reminderExists(int attempts) {
        Reminder reminder = BeanUtils.instantiateClass(Reminder.class);
        ReflectionTestUtils.setField(reminder, "id", ID);
        ReflectionTestUtils.setField(reminder, "tenantId", TENANT);
        ReflectionTestUtils.setField(reminder, "appointmentId", APPOINTMENT);
        ReflectionTestUtils.setField(reminder, "appointmentStartsAt", STARTS);
        ReflectionTestUtils.setField(reminder, "attempts", attempts);
        when(repository.findById(ID)).thenReturn(Optional.of(reminder));
    }

    private Appointment appointmentExists() {
        Tenant tenant = new Tenant();
        tenant.setName("Test Barber");
        tenant.setTimezone("Europe/Athens");
        Customer customer = new Customer();
        customer.setEmail("ann@test.com");
        ServiceOffering haircut = new ServiceOffering();
        haircut.setName("Haircut");

        Appointment a = new Appointment();
        a.setTenant(tenant);
        a.setCustomer(customer);
        a.setService(haircut);
        a.setStartsAt(STARTS);
        when(appointments.findByIdAndTenantId(APPOINTMENT, TENANT)).thenReturn(Optional.of(a));
        return a;
    }

    private void verifyClosedAs(String status, String reason) {
        verify(jdbc).update(contains("status = ?"), eq(status), eq(reason), eq(ID));
    }

    @ParameterizedTest(name = "attempt {0} waits {1} minutes")
    @CsvSource({"1,5", "2,10", "3,20", "4,40"})
    void waitsTwiceAsLongAfterEachFailure(int attempts, int minutes) {
        reminderExists(attempts);

        service.markFailed(ID, "MailException", NOW);

        Timestamp retryAt = Timestamp.from(NOW.plus(Duration.ofMinutes(minutes)));
        verify(jdbc).update(contains("status = 'PENDING'"), eq(retryAt), eq("MailException"), eq(ID));
        verifyNoInteractions(audit);
    }

    @Test
    void fifthFailureClosesTheReminderAndAuditsIt() {
        reminderExists(ReminderService.MAX_ATTEMPTS);
        when(jdbc.update(contains("status = ?"), eq("FAILED"), eq("MailException"), eq(ID))).thenReturn(1);

        service.markFailed(ID, "MailException", NOW);

        verify(audit).record(TENANT, AuditAction.REMINDER_FAILED, APPOINTMENT,
                "reminder 7, attempts 5, MailException");
    }

    @Test
    void noAuditWhenAnotherWorkerAlreadyClosedTheReminder() {
        reminderExists(ReminderService.MAX_ATTEMPTS);
        when(jdbc.update(contains("status = ?"), eq("FAILED"), eq("MailException"), eq(ID))).thenReturn(0);

        service.markFailed(ID, "MailException", NOW);

        verifyNoInteractions(audit);
    }

    @Test
    void markSentAuditsWhenItChangedTheRow() {
        reminderExists(2);
        when(jdbc.update(contains("status = 'SENT'"), any(Timestamp.class), eq(ID))).thenReturn(1);

        service.markSent(ID, NOW);

        verify(audit).record(TENANT, AuditAction.REMINDER_SENT, APPOINTMENT, "reminder 7, attempt 2");
    }

    @Test
    void markSentDoesNotAuditWhenTheRowHadAlreadyMoved() {
        when(jdbc.update(contains("status = 'SENT'"), any(Timestamp.class), eq(ID))).thenReturn(0);

        service.markSent(ID, NOW);

        verifyNoInteractions(audit, repository);
    }

    @Test
    void prepareBuildsTheMessageInTheBusinessTimeZone() {
        reminderExists(1);
        appointmentExists();

        ReminderMessage message = service.prepare(ID, NOW).orElseThrow();

        assertEquals(new ReminderMessage(ID, "ann@test.com", "Test Barber", "Haircut", "2026-07-02 10:00"), message);
        verifyNoInteractions(jdbc);
    }

    @Test
    void cancelledAppointmentIsSkipped() {
        reminderExists(1);
        appointmentExists().setStatus(AppointmentStatus.CANCELLED);

        assertTrue(service.prepare(ID, NOW).isEmpty());
        verifyClosedAs("SKIPPED", "appointment is CANCELLED");
    }

    @Test
    void rescheduledAppointmentIsSkipped() {
        reminderExists(1);
        appointmentExists().setStartsAt(STARTS.plus(Duration.ofDays(1)));

        assertTrue(service.prepare(ID, NOW).isEmpty());
        verifyClosedAs("SKIPPED", "appointment was rescheduled");
    }

    @Test
    void appointmentThatAlreadyStartedIsSkipped() {
        reminderExists(1);
        appointmentExists();

        assertTrue(service.prepare(ID, STARTS).isEmpty());
        verifyClosedAs("SKIPPED", "appointment has already started");
    }

    @Test
    void customerWithoutEmailIsSkipped() {
        reminderExists(1);
        appointmentExists().getCustomer().setEmail(null);

        assertTrue(service.prepare(ID, NOW).isEmpty());
        verifyClosedAs("SKIPPED", "customer has no email");
    }

    @Test
    void reminderThatKeepsCrashingTheWorkerIsFailedWithoutSending() {
        reminderExists(ReminderService.MAX_ATTEMPTS + 1);
        when(jdbc.update(contains("status = ?"), eq("FAILED"), eq("crashed while sending"), eq(ID))).thenReturn(1);

        assertTrue(service.prepare(ID, NOW).isEmpty());

        verifyNoInteractions(appointments);
        verify(audit).record(TENANT, AuditAction.REMINDER_FAILED, APPOINTMENT,
                "reminder 7, attempts 6, crashed while sending");
    }
}
