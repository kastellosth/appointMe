package com.kastellosth.appointme.repository ;

import com.kastellosth.appointme.entity.Appointment;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface AppointmentRepository extends JpaRepository<Appointment, Long> {

    @EntityGraph(attributePaths = {"staff.user", "customer", "service"})
    Optional<Appointment> findByIdAndTenantId(Long id, Long tenantId);

    @EntityGraph(attributePaths = {"staff.user", "customer", "service"})
    @Query("""
            select a from Appointment a
            where a.tenant.id = :tenantId
              and a.startsAt >= :from
              and a.startsAt < :to
            order by a.startsAt
            """)
    List<Appointment> findInRange(@Param("tenantId") Long tenantId,
                                  @Param("from") Instant from,
                                  @Param("to") Instant to);

    @EntityGraph(attributePaths = {"staff.user", "customer", "service"})
    @Query("""
        select a from Appointment a
        where a.tenant.id = :tenantId
          and a.staff.id = :staffId
          and a.startsAt >= :from
          and a.startsAt < :to
        order by a.startsAt
        """)
    List<Appointment> findInRangeForStaff(@Param("tenantId") Long tenantId,
                                          @Param("staffId") Long staffId,
                                          @Param("from") Instant from,
                                          @Param("to") Instant to);

}

