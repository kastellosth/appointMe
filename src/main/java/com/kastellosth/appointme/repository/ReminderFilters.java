package com.kastellosth.appointme.repository;

import com.kastellosth.appointme.entity.Reminder;
import com.kastellosth.appointme.entity.ReminderStatus;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

public final class ReminderFilters {

    private ReminderFilters() {
    }

    public static Specification<Reminder> of(Long tenantId, ReminderStatus status, Long appointmentId) {
        return (root, query, cb) -> {
            List<Predicate> where = new ArrayList<>();
            where.add(cb.equal(root.get("tenantId"), tenantId));
            if (status != null) {
                where.add(cb.equal(root.get("status"), status));
            }
            if (appointmentId != null) {
                where.add(cb.equal(root.get("appointmentId"), appointmentId));
            }
            return cb.and(where.toArray(Predicate[]::new));
        };
    }
}
