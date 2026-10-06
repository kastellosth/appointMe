package com.kastellosth.appointme.repository;

import com.kastellosth.appointme.entity.Membership;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MembershipRepository extends JpaRepository<Membership, Long> {
}