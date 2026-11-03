package com.kastellosth.appointme.repository;

import com.kastellosth.appointme.entity.Invoice;
import com.kastellosth.appointme.entity.InvoiceStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;

/**
 * JpaSpecificationExecutor (Day 21) lets GET /invoices combine optional filters
 * without one query method per combination. See InvoiceFilters.
 */
public interface InvoiceRepository extends JpaRepository<Invoice, Long>, JpaSpecificationExecutor<Invoice> {

    /** The ONLY way to load a single invoice: id AND tenant, like every other repository. */
    Optional<Invoice> findByIdAndTenantId(Long id, Long tenantId);

    /** "Does this appointment already have an invoice that is not void?" */
    boolean existsByTenantIdAndAppointmentIdAndStatusNot(Long tenantId, Long appointmentId, InvoiceStatus status);
}
