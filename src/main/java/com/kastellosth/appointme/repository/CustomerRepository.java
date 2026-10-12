package com.kastellosth.appointme.repository;

import com.kastellosth.appointme.entity.Customer;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface CustomerRepository extends JpaRepository<Customer, Long> {

    Page<Customer> findByTenantId(Long tenantId, Pageable pageable);

    Page<Customer> findByTenantIdAndFullNameContainingIgnoreCase(
            Long tenantId, String name, Pageable pageable);

    Optional<Customer> findByIdAndTenantId(Long id, Long tenantId);

    boolean existsByTenantIdAndEmail(Long tenantId, String email);


}