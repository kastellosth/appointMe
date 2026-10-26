package com.kastellosth.appointme.service;

import com.kastellosth.appointme.dto.AppointmentRequest;
import com.kastellosth.appointme.dto.AppointmentResponse;
import com.kastellosth.appointme.entity.Appointment;
import com.kastellosth.appointme.entity.AppointmentStatus;
import com.kastellosth.appointme.entity.Customer;
import com.kastellosth.appointme.entity.Membership;
import com.kastellosth.appointme.entity.ServiceOffering;
import com.kastellosth.appointme.entity.Tenant;
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
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AppointmentService {

    private static final Duration MAX_RANGE = Duration.ofDays(62);

    private final AppointmentRepository appointments;
    private final TenantRepository tenants;
    private final MembershipRepository memberships;
    private final CustomerRepository customers;
    private final ServiceOfferingRepository services;
    private final WorkingHoursService workingHours;

    @Transactional
    public AppointmentResponse create(Long tenantId, Long staffScope, AppointmentRequest req) {
        if (staffScope != null && !staffScope.equals(req.staffId())) {
            throw new ForbiddenException("Staff members can only book appointments on their own schedule");
        }

        Tenant tenant = tenants.findById(tenantId)
                .orElseThrow(() -> new NotFoundException("Tenant " + tenantId + " not found"));

        ServiceOffering service = services.findByIdAndTenantId(req.serviceId(), tenantId)
                .orElseThrow(() -> new NotFoundException("Service " + req.serviceId() + " not found"));
        if (!service.isActive()) {
            throw new BadRequestException("Service '" + service.getName() + "' is not active");
        }

        Customer customer = customers.findByIdAndTenantId(req.customerId(), tenantId)
                .orElseThrow(() -> new NotFoundException("Customer " + req.customerId() + " not found"));

        Membership staff = memberships.findByIdAndTenantId(req.staffId(), tenantId)
                .orElseThrow(() -> new NotFoundException("Staff member " + req.staffId() + " not found"));

        Instant startsAt = req.startsAt();
        Instant endsAt = startsAt.plus(Duration.ofMinutes(service.getDurationMinutes()));
        requireBookable(tenant, tenantId, staff.getId(), startsAt, endsAt);

        Appointment a = new Appointment();
        a.setTenant(tenant);
        a.setService(service);
        a.setCustomer(customer);
        a.setStaff(staff);
        a.setStartsAt(startsAt);
        a.setEndsAt(endsAt);
        a.setNotes(req.notes());

        return toResponse(saveOrConflict(a));
    }

    @Transactional(readOnly = true)
    public List<AppointmentResponse> list(Long tenantId, Long staffScope, Instant from, Instant to) {
        if (!from.isBefore(to)) {
            throw new BadRequestException("'from' must be before 'to'");
        }
        if (Duration.between(from, to).compareTo(MAX_RANGE) > 0) {
            throw new BadRequestException("Date range cannot exceed 62 days");
        }
        List<Appointment> found = (staffScope == null)
                ? appointments.findInRange(tenantId, from, to)
                : appointments.findInRangeForStaff(tenantId, staffScope, from, to);
        return found.stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public AppointmentResponse get(Long tenantId, Long staffScope, Long id) {
        return toResponse(find(tenantId, staffScope, id));
    }

    @Transactional
    public AppointmentResponse updateStatus(Long tenantId, Long staffScope, Long id,
                                            AppointmentStatus target) {
        Appointment a = find(tenantId, staffScope, id);
        if (!a.getStatus().canTransitionTo(target)) {
            throw new ConflictException(
                    "Cannot change status from " + a.getStatus() + " to " + target);
        }
        a.setStatus(target);
        return toResponse(a);
    }

    @Transactional
    public AppointmentResponse cancel(Long tenantId, Long staffScope, Long id) {
        Appointment a = find(tenantId, staffScope, id);
        if (!a.getStatus().canTransitionTo(AppointmentStatus.CANCELLED)) {
            throw new ConflictException(
                    "Only SCHEDULED appointments can be cancelled (this one is " + a.getStatus() + ")");
        }
        a.setStatus(AppointmentStatus.CANCELLED);
        return toResponse(a);
    }

    @Transactional
    public AppointmentResponse reschedule(Long tenantId, Long staffScope, Long id, Instant newStart) {
        Appointment a = find(tenantId, staffScope, id);
        if (a.getStatus() != AppointmentStatus.SCHEDULED) {
            throw new ConflictException(
                    "Only SCHEDULED appointments can be rescheduled (this one is " + a.getStatus() + ")");
        }
        Tenant tenant = tenants.findById(tenantId)
                .orElseThrow(() -> new NotFoundException("Tenant " + tenantId + " not found"));

        Duration length = Duration.between(a.getStartsAt(), a.getEndsAt());   // keep what was booked
        Instant newEnd = newStart.plus(length);
        requireBookable(tenant, tenantId, a.getStaff().getId(), newStart, newEnd);

        a.setStartsAt(newStart);
        a.setEndsAt(newEnd);
        return toResponse(saveOrConflict(a));
    }


    private void requireBookable(Tenant tenant, Long tenantId, Long staffId,
                                 Instant start, Instant end) {
        if (!start.isAfter(Instant.now())) {
            throw new BadRequestException("The appointment must start in the future");
        }
        ZoneId zone = ZoneId.of(tenant.getTimezone());
        LocalDate date = start.atZone(zone).toLocalDate();
        var windows = workingHours.windowsFor(tenantId, staffId, date);
        if (!AvailabilityCalculator.fitsWithinWindows(date, zone, windows,
                new AvailabilityCalculator.Range(start, end))) {
            throw new ConflictException("The staff member does not work at that time");
        }
    }

    /**
     * saveAndFlush sends the SQL NOW, inside this try block, so a clash with another
     * booking is thrown here and not later at commit, where we could not catch it.
     */
    private Appointment saveOrConflict(Appointment a) {
        try {
            return appointments.saveAndFlush(a);
        } catch (RuntimeException e) {
            if (DatabaseErrors.isExclusionViolation(e)) {
                throw new ConflictException(DatabaseErrors.SLOT_TAKEN_MESSAGE);
            }
            throw e;
        }
    }

    private Appointment find(Long tenantId, Long staffScope, Long id) {
        Appointment a = appointments.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new NotFoundException("Appointment " + id + " not found"));
        if (staffScope != null && !a.getStaff().getId().equals(staffScope)) {
            throw new NotFoundException("Appointment " + id + " not found");
        }
        return a;
    }

    private AppointmentResponse toResponse(Appointment a) {
        return new AppointmentResponse(
                a.getId(),
                a.getStaff().getId(), a.getStaff().getUser().getFullName(),
                a.getCustomer().getId(), a.getCustomer().getFullName(),
                a.getService().getId(), a.getService().getName(),
                a.getStartsAt(), a.getEndsAt(),
                a.getStatus(), a.getNotes(), a.getCreatedAt());
    }
}