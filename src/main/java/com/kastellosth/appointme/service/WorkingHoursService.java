package com.kastellosth.appointme.service;

import com.kastellosth.appointme.dto.ScheduleInterval;
import com.kastellosth.appointme.dto.WorkingHoursRequest;
import com.kastellosth.appointme.dto.WorkingHoursResponse;
import com.kastellosth.appointme.entity.WorkingHours;
import com.kastellosth.appointme.exception.NotFoundException;
import com.kastellosth.appointme.repository.MembershipRepository;
import com.kastellosth.appointme.repository.WorkingHoursRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;

@Service
public class WorkingHoursService {

    private final WorkingHoursRepository workingHours;
    private final MembershipRepository memberships;

    public WorkingHoursService(WorkingHoursRepository workingHours, MembershipRepository memberships) {
        this.workingHours = workingHours;
        this.memberships = memberships;
    }

    @Transactional(readOnly = true)
    public WorkingHoursResponse get(Long tenantId, Long staffId) {
        requireStaffInTenant(tenantId, staffId);
        return toResponse(staffId, workingHours.findByTenantIdAndStaffId(tenantId, staffId));
    }

    /** Replaces the WHOLE week. All or nothing: if validation fails, nothing is deleted. */
    @Transactional
    public WorkingHoursResponse replace(Long tenantId, Long staffId, WorkingHoursRequest request) {
        requireStaffInTenant(tenantId, staffId);
        WorkingHoursValidator.validate(request.intervals());   // before touching the data

        workingHours.deleteSchedule(tenantId, staffId);

        List<WorkingHours> rows = request.intervals().stream().map(i -> {
            WorkingHours row = new WorkingHours();
            row.setTenantId(tenantId);
            row.setStaffId(staffId);
            row.setDayOfWeek(i.dayOfWeek().getValue());   // MONDAY -> 1 ... SUNDAY -> 7
            row.setStartTime(i.start());
            row.setEndTime(i.end());
            return row;
        }).toList();

        return toResponse(staffId, workingHours.saveAll(rows));
    }

    // A membership of another tenant looks exactly like one that does not exist (404),
    // the same rule as every other tenant-owned record.
    private void requireStaffInTenant(Long tenantId, Long staffId) {
        if (!memberships.existsByIdAndTenantId(staffId, tenantId)) {
            throw new NotFoundException("Staff member " + staffId + " not found");
        }
    }

    private WorkingHoursResponse toResponse(Long staffId, List<WorkingHours> rows) {
        List<ScheduleInterval> intervals = rows.stream()
                .sorted(Comparator.comparingInt(WorkingHours::getDayOfWeek)
                        .thenComparing(WorkingHours::getStartTime))
                .map(r -> new ScheduleInterval(DayOfWeek.of(r.getDayOfWeek()), r.getStartTime(), r.getEndTime()))
                .toList();
        return new WorkingHoursResponse(staffId, intervals);
    }
    @Transactional(readOnly = true)
    public List<AvailabilityCalculator.LocalWindow> windowsFor(Long tenantId, Long staffId, LocalDate date) {
        int weekday = date.getDayOfWeek().getValue();
        return workingHours.findByTenantIdAndStaffId(tenantId, staffId).stream()
                .filter(w -> w.getDayOfWeek() == weekday)
                .map(w -> new AvailabilityCalculator.LocalWindow(w.getStartTime(), w.getEndTime()))
                .toList();
    }
}