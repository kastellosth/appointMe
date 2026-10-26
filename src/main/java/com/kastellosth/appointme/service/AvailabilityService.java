package com.kastellosth.appointme.service;

import com.kastellosth.appointme.dto.AvailabilityResponse;
import com.kastellosth.appointme.dto.SlotResponse;
import com.kastellosth.appointme.entity.AppointmentStatus;
import com.kastellosth.appointme.entity.ServiceOffering;
import com.kastellosth.appointme.entity.Tenant;
import com.kastellosth.appointme.exception.BadRequestException;
import com.kastellosth.appointme.exception.ForbiddenException;
import com.kastellosth.appointme.exception.NotFoundException;
import com.kastellosth.appointme.repository.AppointmentRepository;
import com.kastellosth.appointme.repository.MembershipRepository;
import com.kastellosth.appointme.repository.ServiceOfferingRepository;
import com.kastellosth.appointme.repository.TenantRepository;
import com.kastellosth.appointme.repository.WorkingHoursRepository;
import com.kastellosth.appointme.service.AvailabilityCalculator.LocalWindow;
import com.kastellosth.appointme.service.AvailabilityCalculator.Range;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
public class AvailabilityService {

    private static final int MIN_STEP_MINUTES = 5;
    private static final int MAX_STEP_MINUTES = 240;

    private final MembershipRepository memberships;
    private final ServiceOfferingRepository services;
    private final TenantRepository tenants;
    private final WorkingHoursRepository workingHours;
    private final AppointmentRepository appointments;

    public AvailabilityService(MembershipRepository memberships,
                               ServiceOfferingRepository services,
                               TenantRepository tenants,
                               WorkingHoursRepository workingHours,
                               AppointmentRepository appointments) {
        this.memberships = memberships;
        this.services = services;
        this.tenants = tenants;
        this.workingHours = workingHours;
        this.appointments = appointments;
    }

    /** staffScope is null for owners, or the staff member's own membership id. */
    @Transactional(readOnly = true)
    public AvailabilityResponse find(Long tenantId, Long staffScope, Long staffId,
                                     Long serviceId, LocalDate date, int stepMinutes) {
        if (stepMinutes < MIN_STEP_MINUTES || stepMinutes > MAX_STEP_MINUTES) {
            throw new BadRequestException("stepMinutes must be between "
                    + MIN_STEP_MINUTES + " and " + MAX_STEP_MINUTES);
        }
        if (staffScope != null && !staffScope.equals(staffId)) {
            throw new ForbiddenException("Staff members can only view their own availability");
        }

        // Everything is loaded WITH the caller's tenant: a foreign id behaves like a missing one.
        memberships.findByIdAndTenantId(staffId, tenantId)
                .orElseThrow(() -> new NotFoundException("Staff member " + staffId + " not found"));
        ServiceOffering service = services.findByIdAndTenantId(serviceId, tenantId)
                .orElseThrow(() -> new NotFoundException("Service " + serviceId + " not found"));
        if (!service.isActive()) {
            throw new BadRequestException("Service '" + service.getName() + "' is not active");
        }
        Tenant tenant = tenants.findById(tenantId)
                .orElseThrow(() -> new NotFoundException("Tenant " + tenantId + " not found"));
        ZoneId zone = ZoneId.of(tenant.getTimezone());

        // The staff member's wall-clock intervals for that weekday (1 = Monday ... 7 = Sunday)
        int weekday = date.getDayOfWeek().getValue();
        List<LocalWindow> windows = workingHours.findByTenantIdAndStaffId(tenantId, staffId).stream()
                .filter(w -> w.getDayOfWeek() == weekday)
                .map(w -> new LocalWindow(w.getStartTime(), w.getEndTime()))
                .toList();

        // Busy periods only matter on a day the person works at all
        List<Range> busy = List.of();
        if (!windows.isEmpty()) {
            Instant dayStart = date.atStartOfDay(zone).toInstant();
            Instant dayEnd = date.plusDays(1).atStartOfDay(zone).toInstant();
            busy = appointments
                    .findBusyForStaff(tenantId, staffId, dayStart, dayEnd, AppointmentStatus.CANCELLED)
                    .stream()
                    .map(a -> new Range(a.getStartsAt(), a.getEndsAt()))
                    .toList();
        }

        List<Range> free = AvailabilityCalculator.freeSlots(
                date, zone, windows, busy,
                Duration.ofMinutes(service.getDurationMinutes()),
                Duration.ofMinutes(stepMinutes),
                Instant.now());

        List<SlotResponse> slots = free.stream()
                .map(r -> new SlotResponse(format(r.start(), zone), format(r.end(), zone)))
                .toList();

        return new AvailabilityResponse(staffId, serviceId, date, tenant.getTimezone(),
                service.getDurationMinutes(), stepMinutes, slots);
    }

    private static String format(Instant instant, ZoneId zone) {
        return DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(instant.atZone(zone).toOffsetDateTime());
    }
}