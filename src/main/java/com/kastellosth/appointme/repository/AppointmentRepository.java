package com.kastellosth.appointme.repository ;

import com.kastellosth.appointme.entity.Appointment;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AppointmentRepository extends JpaRepository<Appointment,Long> {
}