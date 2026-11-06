package com.kastellosth.appointme.repository;

import com.kastellosth.appointme.entity.Invoice;
import com.kastellosth.appointme.entity.InvoiceStatus;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** Not "(:status IS NULL OR ...)" in JPQL: PostgreSQL often cannot determine the type of a NULL parameter. */
public final class InvoiceFilters {

    private InvoiceFilters() {
    }

    public static Specification<Invoice> of(Long tenantId, InvoiceStatus status, Long customerId,
                                            Long appointmentId, Instant createdFrom, Instant createdTo) {
        return (root, query, cb) -> {
            List<Predicate> where = new ArrayList<>();
            where.add(cb.equal(root.get("tenantId"), tenantId));
            if (status != null) {
                where.add(cb.equal(root.get("status"), status));
            }
            if (customerId != null) {
                where.add(cb.equal(root.get("customerId"), customerId));
            }
            if (appointmentId != null) {
                where.add(cb.equal(root.get("appointmentId"), appointmentId));
            }
            if (createdFrom != null) {
                where.add(cb.greaterThanOrEqualTo(root.<Instant>get("createdAt"), createdFrom));
            }
            if (createdTo != null) {
                where.add(cb.lessThan(root.<Instant>get("createdAt"), createdTo));
            }
            return cb.and(where.toArray(Predicate[]::new));
        };
    }
}
