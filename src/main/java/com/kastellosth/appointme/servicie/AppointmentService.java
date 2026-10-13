package com.kastellosth.appointme.servicie;

import com.kastellosth.appointme.dto.AppointmentRequest;
import com.kastellosth.appointme.dto.AppointmentResponse;
import com.kastellosth.appointme.entity.*;
import com.kastellosth.appointme.exception.BadRequestException;
import com.kastellosth.appointme.exception.ConflictException;
import com.kastellosth.appointme.exception.NotFoundException;
import com.kastellosth.appointme.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
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

    @Transactional
    public AppointmentResponse create(Long tenantId, AppointmentRequest req) {
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

        Appointment a = new Appointment();
        a.setTenant(tenant);
        a.setService(service);
        a.setCustomer(customer);
        a.setStaff(staff);
        a.setStartsAt(req.startsAt());
        a.setEndsAt(req.startsAt().plus(Duration.ofMinutes(service.getDurationMinutes())));
        a.setNotes(req.notes());

        return toResponse(appointments.save(a));
    }

    @Transactional(readOnly = true)
    public List<AppointmentResponse> list(Long tenantId, Instant from, Instant to) {
        if (!from.isBefore(to)) {
            throw new BadRequestException("'from' must be before 'to'");
        }
        if (Duration.between(from, to).compareTo(MAX_RANGE) > 0) {
            throw new BadRequestException("Date range cannot exceed 62 days");
        }
        return appointments.findInRange(tenantId, from, to).stream()
                .map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public AppointmentResponse get(Long tenantId, Long id) {
        return toResponse(find(tenantId, id));
    }

    @Transactional
    public AppointmentResponse updateStatus(Long tenantId, Long id, AppointmentStatus target) {
        Appointment a = find(tenantId, id);
        if (!a.getStatus().canTransitionTo(target)) {
            throw new ConflictException(
                    "Cannot change status from " + a.getStatus() + " to " + target);
        }
        a.setStatus(target);
        return toResponse(a);
    }

    private Appointment find(Long tenantId, Long id) {
        return appointments.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new NotFoundException("Appointment " + id + " not found"));
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
