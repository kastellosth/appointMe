package com.kastellosth.appointme.repository ;

import com.kastellosth.appointme.entity.Customer;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CustomerRepository extends JpaRepository<Customer,Long>{
}