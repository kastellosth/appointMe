package com.kastellosth.appointme.repository;

import com.kastellosth.appointme.entity.Tenant;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TenantRepository extends JpaRepository<Tenant, Long> {
}