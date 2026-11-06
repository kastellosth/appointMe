package com.kastellosth.appointme.repository;

import com.kastellosth.appointme.entity.AuditAction;
import com.kastellosth.appointme.entity.AuditEntityType;
import com.kastellosth.appointme.entity.AuditEvent;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public final class AuditFilters {

    private AuditFilters() {
    }

    public static Specification<AuditEvent> of(Long tenantId, AuditEntityType entityType, Long entityId,
                                               AuditAction action, Long actorUserId, Instant from, Instant to) {
        return (root, query, cb) -> {
            List<Predicate> where = new ArrayList<>();
            where.add(cb.equal(root.get("tenantId"), tenantId));
            if (entityType != null) {
                where.add(cb.equal(root.get("entityType"), entityType));
            }
            if (entityId != null) {
                where.add(cb.equal(root.get("entityId"), entityId));
            }
            if (action != null) {
                where.add(cb.equal(root.get("action"), action));
            }
            if (actorUserId != null) {
                where.add(cb.equal(root.get("actorUserId"), actorUserId));
            }
            if (from != null) {
                where.add(cb.greaterThanOrEqualTo(root.<Instant>get("occurredAt"), from));
            }
            if (to != null) {
                where.add(cb.lessThan(root.<Instant>get("occurredAt"), to));
            }
            return cb.and(where.toArray(Predicate[]::new));
        };
    }
}
