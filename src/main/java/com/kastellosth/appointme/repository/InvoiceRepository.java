package com.kastellosth.appointme.repository;

import com.kastellosth.appointme.entity.Invoice;
import com.kastellosth.appointme.entity.InvoiceStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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

    /**
     * Day 22. Loads the invoice AND locks its row until the transaction ends (SELECT ... FOR NO KEY UPDATE
     * on PostgreSQL). A second payment for the same invoice waits here, so "read balance, then insert"
     * cannot race: the second one sees the first one's payment.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from Invoice i where i.id = :id and i.tenantId = :tenantId")
    Optional<Invoice> findForUpdate(@Param("id") Long id, @Param("tenantId") Long tenantId);
}
