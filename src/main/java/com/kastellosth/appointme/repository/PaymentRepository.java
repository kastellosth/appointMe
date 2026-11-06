package com.kastellosth.appointme.repository;

import com.kastellosth.appointme.entity.Payment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    Optional<Payment> findByIdAndTenantId(Long id, Long tenantId);

    List<Payment> findByTenantIdAndInvoiceIdOrderByIdAsc(Long tenantId, Long invoiceId);

    /** null when there are no payments yet. */
    @Query("select sum(p.amount) from Payment p where p.tenantId = :tenantId and p.invoiceId = :invoiceId")
    BigDecimal sumForInvoice(@Param("tenantId") Long tenantId, @Param("invoiceId") Long invoiceId);
}
