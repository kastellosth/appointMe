package com.kastellosth.appointme.repository;

import com.kastellosth.appointme.entity.Membership;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MembershipRepository extends JpaRepository<Membership, Long> {

    Optional<Membership> findByIdAndTenantId(Long id, Long tenantId);

    Optional<Membership> findByUserIdAndTenantId(Long userId, Long tenantId);

    @EntityGraph(attributePaths = "tenant")
    List<Membership> findByUserId(Long userId);
}