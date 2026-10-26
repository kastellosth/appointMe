package com.kastellosth.appointme.repository;

import com.kastellosth.appointme.entity.WorkingHours;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface WorkingHoursRepository extends JpaRepository<WorkingHours, Long> {

    List<WorkingHours> findByTenantIdAndStaffId(Long tenantId, Long staffId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from WorkingHours w where w.tenantId = :tenantId and w.staffId = :staffId")
    void deleteSchedule(@Param("tenantId") Long tenantId, @Param("staffId") Long staffId);
}