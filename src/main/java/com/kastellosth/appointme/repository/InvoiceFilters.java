package com.kastellosth.appointme.repository;

import com.kastellosth.appointme.entity.Invoice;
import com.kastellosth.appointme.entity.InvoiceStatus;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Builds the WHERE clause of GET /invoices. Only the filters that were given are added.
 * The tenant filter is NOT optional: it is the first argument and always applied.
 *
 * Why not one JPQL query with "(:status IS NULL OR i.status = :status)"? PostgreSQL often
 * cannot tell the type of a NULL parameter ("could not determine data type of parameter $1"),
 * and the query planner cannot use indexes well for that pattern.
 */
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
            // half-open [from, to), like every other time range in this project
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
