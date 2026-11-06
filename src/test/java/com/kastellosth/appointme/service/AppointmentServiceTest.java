package com.kastellosth.appointme.service;

import com.kastellosth.appointme.dto.AppointmentRequest;
import com.kastellosth.appointme.dto.AppointmentResponse;
import com.kastellosth.appointme.entity.Appointment;
import com.kastellosth.appointme.entity.AppointmentStatus;
import com.kastellosth.appointme.entity.AuditAction;
import com.kastellosth.appointme.entity.Customer;
import com.kastellosth.appointme.entity.Membership;
import com.kastellosth.appointme.entity.ServiceOffering;
import com.kastellosth.appointme.entity.Tenant;
import com.kastellosth.appointme.entity.User;
import com.kastellosth.appointme.exception.BadRequestException;
import com.kastellosth.appointme.exception.ConflictException;
import com.kastellosth.appointme.exception.DatabaseErrors;
import com.kastellosth.appointme.exception.ForbiddenException;
import com.kastellosth.appointme.exception.NotFoundException;
import com.kastellosth.appointme.repository.AppointmentRepository;
import com.kastellosth.appointme.repository.CustomerRepository;
import com.kastellosth.appointme.repository.MembershipRepository;
import com.kastellosth.appointme.repository.ServiceOfferingRepository;
import com.kastellosth.appointme.repository.TenantRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.AdditionalAnswers.returnsFirstArg;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AppointmentServiceTest {

    private static final long TENANT = 1L;
    private static final long STAFF = 2L;
    private static final long OTHER_STAFF = 3L;
    private static final long CUSTOMER = 5L;
    private static final long SERVICE = 6L;
    private static final long APPOINTMENT = 40L;

    /** The service calls Instant.now(), so these dates have to move with the real clock. */
    private static final LocalDate TOMORROW = LocalDate.now(ZoneOffset.UTC).plusDays(1);
    private static final Instant TEN_O_CLOCK = TOMORROW.atTime(10, 0).toInstant(ZoneOffset.UTC);

    @Mock
    private AppointmentRepository appointments;

    @Mock
    private TenantRepository tenants;

    @Mock
    private MembershipRepository memberships;

    @Mock
    private CustomerRepository customers;

    @Mock
    private ServiceOfferingRepository services;

    @Mock
    private WorkingHoursService workingHours;

    @Mock
    private InvoiceService invoiceService;

    @Mock
    private AuditService audit;

    @InjectMocks
    private AppointmentService service;

    private Tenant tenant;
    private ServiceOffering haircut;
    private Customer customer;
    private Membership staff;

    @BeforeEach
    void fixtures() {
        tenant = new Tenant();
        tenant.setId(TENANT);
        tenant.setTimezone("UTC");

        haircut = new ServiceOffering();
        haircut.setId(SERVICE);
        haircut.setName("Haircut");
        haircut.setDurationMinutes(30);

        customer = new Customer();
        customer.setId(CUSTOMER);
        customer.setFullName("Ann");

        User user = new User();
        user.setFullName("Alice");
        staff = new Membership();
        staff.setId(STAFF);
        staff.setUser(user);
    }

    private static AppointmentRequest requestAt(Instant start) {
        return new AppointmentRequest(STAFF, CUSTOMER, SERVICE, start, null);
    }

    private void everythingInTheRequestExists() {
        when(tenants.findById(TENANT)).thenReturn(Optional.of(tenant));
        when(services.findByIdAndTenantId(SERVICE, TENANT)).thenReturn(Optional.of(haircut));
        when(customers.findByIdAndTenantId(CUSTOMER, TENANT)).thenReturn(Optional.of(customer));
        when(memberships.findByIdAndTenantId(STAFF, TENANT)).thenReturn(Optional.of(staff));
    }

    private void staffWorksNineToFiveTomorrow() {
        when(workingHours.windowsFor(TENANT, STAFF, TOMORROW)).thenReturn(List.of(
                new AvailabilityCalculator.LocalWindow(LocalTime.of(9, 0), LocalTime.of(17, 0))));
    }

    private Appointment appointmentExists(Instant start, AppointmentStatus status) {
        Appointment a = new Appointment();
        a.setId(APPOINTMENT);
        a.setTenant(tenant);
        a.setStaff(staff);
        a.setCustomer(customer);
        a.setService(haircut);
        a.setStartsAt(start);
        a.setEndsAt(start.plus(Duration.ofMinutes(30)));
        a.setStatus(status);
        when(appointments.findByIdAndTenantId(APPOINTMENT, TENANT)).thenReturn(Optional.of(a));
        return a;
    }

    private static Instant yesterday() {
        return Instant.now().minus(Duration.ofDays(1));
    }

    private static RuntimeException databaseError(String sqlState) {
        return new DataIntegrityViolationException("database said no", new SQLException("no", sqlState));
    }

    private void savingFailsWith(RuntimeException error) {
        everythingInTheRequestExists();
        staffWorksNineToFiveTomorrow();
        when(appointments.saveAndFlush(any(Appointment.class))).thenThrow(error);
    }

    @Test
    void createCalculatesTheEndFromTheServiceAndAudits() {
        everythingInTheRequestExists();
        staffWorksNineToFiveTomorrow();
        when(appointments.saveAndFlush(any(Appointment.class))).then(returnsFirstArg());

        AppointmentResponse response = service.create(TENANT, null, requestAt(TEN_O_CLOCK));

        assertEquals(TEN_O_CLOCK.plus(Duration.ofMinutes(30)), response.endsAt());
        assertEquals(AppointmentStatus.SCHEDULED, response.status());
        assertEquals("Alice", response.staffName());
        verify(audit).record(eq(TENANT), eq(AuditAction.APPOINTMENT_CREATED), any(), any());
    }

    @Test
    void staffCannotBookForAColleague() {
        assertThrows(ForbiddenException.class,
                () -> service.create(TENANT, OTHER_STAFF, requestAt(TEN_O_CLOCK)));

        verifyNoInteractions(tenants, services, customers, memberships, appointments, audit);
    }

    @Test
    void inactiveServiceIsRefused() {
        when(tenants.findById(TENANT)).thenReturn(Optional.of(tenant));
        when(services.findByIdAndTenantId(SERVICE, TENANT)).thenReturn(Optional.of(haircut));
        haircut.setActive(false);

        assertThrows(BadRequestException.class, () -> service.create(TENANT, null, requestAt(TEN_O_CLOCK)));

        verify(appointments, never()).saveAndFlush(any());
    }

    @Test
    void customerOfAnotherTenantIsNotFound() {
        when(tenants.findById(TENANT)).thenReturn(Optional.of(tenant));
        when(services.findByIdAndTenantId(SERVICE, TENANT)).thenReturn(Optional.of(haircut));
        when(customers.findByIdAndTenantId(CUSTOMER, TENANT)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> service.create(TENANT, null, requestAt(TEN_O_CLOCK)));
    }

    @Test
    void startInThePastIsRefusedEvenWithoutTheRequestValidation() {
        everythingInTheRequestExists();

        assertThrows(BadRequestException.class, () -> service.create(TENANT, null, requestAt(yesterday())));

        verifyNoInteractions(workingHours);
    }

    @Test
    void appointmentMustFitInsideTheWorkingHours() {
        everythingInTheRequestExists();
        staffWorksNineToFiveTomorrow();
        Instant endsAfterFive = TOMORROW.atTime(16, 45).toInstant(ZoneOffset.UTC);

        assertThrows(ConflictException.class, () -> service.create(TENANT, null, requestAt(endsAfterFive)));

        verify(appointments, never()).saveAndFlush(any());
    }

    @Test
    void lastSlotThatEndsExactlyAtClosingIsAllowed() {
        everythingInTheRequestExists();
        staffWorksNineToFiveTomorrow();
        when(appointments.saveAndFlush(any(Appointment.class))).then(returnsFirstArg());

        service.create(TENANT, null, requestAt(TOMORROW.atTime(16, 30).toInstant(ZoneOffset.UTC)));

        verify(appointments).saveAndFlush(any());
    }

    @Test
    void takenSlotBecomesAConflictAndIsNotAudited() {
        savingFailsWith(databaseError("23P01"));

        ConflictException e = assertThrows(ConflictException.class,
                () -> service.create(TENANT, null, requestAt(TEN_O_CLOCK)));

        assertEquals(DatabaseErrors.SLOT_TAKEN_MESSAGE, e.getMessage());
        verifyNoInteractions(audit);
    }

    @Test
    void deadlockBecomesAConflictThatAsksForARetry() {
        savingFailsWith(databaseError("40P01"));

        ConflictException e = assertThrows(ConflictException.class,
                () -> service.create(TENANT, null, requestAt(TEN_O_CLOCK)));

        assertEquals(DatabaseErrors.SLOT_CONTENDED_MESSAGE, e.getMessage());
    }

    @Test
    void otherDatabaseErrorsAreNotHidden() {
        RuntimeException error = databaseError("23505");
        savingFailsWith(error);

        RuntimeException thrown = assertThrows(RuntimeException.class,
                () -> service.create(TENANT, null, requestAt(TEN_O_CLOCK)));

        assertSame(error, thrown);
    }

    @Test
    void staffGetNotFoundForAColleaguesAppointment() {
        appointmentExists(TEN_O_CLOCK, AppointmentStatus.SCHEDULED);

        assertThrows(NotFoundException.class, () -> service.get(TENANT, OTHER_STAFF, APPOINTMENT));
    }

    @Test
    void completingAnAppointmentChangesItsStatusBeforeCreatingTheInvoice() {
        Appointment a = appointmentExists(yesterday(), AppointmentStatus.SCHEDULED);
        when(invoiceService.createDraftForCompletedAppointment(a)).thenAnswer(call -> {
            assertEquals(AppointmentStatus.COMPLETED, a.getStatus());
            return null;
        });

        service.updateStatus(TENANT, null, APPOINTMENT, AppointmentStatus.COMPLETED);

        InOrder order = inOrder(audit, invoiceService);
        order.verify(audit).record(TENANT, AuditAction.APPOINTMENT_STATUS_CHANGED, APPOINTMENT,
                "SCHEDULED -> COMPLETED");
        order.verify(invoiceService).createDraftForCompletedAppointment(a);
    }

    @Test
    void noShowCreatesNoInvoice() {
        appointmentExists(yesterday(), AppointmentStatus.SCHEDULED);

        service.updateStatus(TENANT, null, APPOINTMENT, AppointmentStatus.NO_SHOW);

        verifyNoInteractions(invoiceService);
    }

    @Test
    void futureAppointmentCannotBeCompleted() {
        Appointment a = appointmentExists(TEN_O_CLOCK, AppointmentStatus.SCHEDULED);

        assertThrows(ConflictException.class,
                () -> service.updateStatus(TENANT, null, APPOINTMENT, AppointmentStatus.COMPLETED));

        assertEquals(AppointmentStatus.SCHEDULED, a.getStatus());
        verifyNoInteractions(invoiceService, audit);
    }

    @Test
    void finishedAppointmentCannotChangeStatusAgain() {
        appointmentExists(yesterday(), AppointmentStatus.COMPLETED);

        assertThrows(ConflictException.class,
                () -> service.updateStatus(TENANT, null, APPOINTMENT, AppointmentStatus.CANCELLED));

        verifyNoInteractions(invoiceService, audit);
    }

    @Test
    void cancelChangesTheStatusAndAudits() {
        Appointment a = appointmentExists(TEN_O_CLOCK, AppointmentStatus.SCHEDULED);

        service.cancel(TENANT, null, APPOINTMENT);

        assertEquals(AppointmentStatus.CANCELLED, a.getStatus());
        verify(audit).record(TENANT, AuditAction.APPOINTMENT_CANCELLED, APPOINTMENT, "SCHEDULED -> CANCELLED");
    }

    @Test
    void rescheduleKeepsTheLengthOfTheAppointment() {
        appointmentExists(TEN_O_CLOCK, AppointmentStatus.SCHEDULED);
        when(tenants.findById(TENANT)).thenReturn(Optional.of(tenant));
        staffWorksNineToFiveTomorrow();
        ArgumentCaptor<Appointment> saved = ArgumentCaptor.forClass(Appointment.class);
        when(appointments.saveAndFlush(saved.capture())).then(returnsFirstArg());
        Instant noon = TOMORROW.atTime(12, 0).toInstant(ZoneOffset.UTC);

        service.reschedule(TENANT, null, APPOINTMENT, noon);

        assertEquals(noon, saved.getValue().getStartsAt());
        assertEquals(noon.plus(Duration.ofMinutes(30)), saved.getValue().getEndsAt());
    }

    @Test
    void cancelledAppointmentCannotBeRescheduled() {
        appointmentExists(TEN_O_CLOCK, AppointmentStatus.CANCELLED);

        assertThrows(ConflictException.class, () -> service.reschedule(TENANT, null, APPOINTMENT, TEN_O_CLOCK));

        verifyNoInteractions(tenants, workingHours, audit);
    }
}
