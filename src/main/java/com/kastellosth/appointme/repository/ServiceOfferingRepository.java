package com.kastellosth.appointme.repository;

import com.kastellosth.appointme.entity.ServiceOffering;
import org.springframework.data.jpa.repository.JpaRepository;
public interface ServiceOfferingRepository extends JpaRepository<ServiceOffering,Long> {
}