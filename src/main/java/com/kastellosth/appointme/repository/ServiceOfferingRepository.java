package com.kastellosth.appointme.repository;

import com.kastellosth.appointme.entity.ServiceOffering;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ServiceOfferingRepository extends JpaRepository<ServiceOffering, Long> {

    List<ServiceOffering> findAllByTenantId(Long tenantId);

    Optional<ServiceOffering> findByIdAndTenantId(Long id, Long tenantId);
}