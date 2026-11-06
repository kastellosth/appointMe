package com.kastellosth.appointme;

import com.kastellosth.appointme.dto.AppointmentRequest;
import com.kastellosth.appointme.exception.ConflictException;
import com.kastellosth.appointme.service.AppointmentService;
import com.kastellosth.appointme.support.ParallelRunner;
import com.kastellosth.appointme.support.RaceTestData;
import com.kastellosth.appointme.support.RaceTestData.World;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest(properties = {
        "spring.datasource.hikari.maximum-pool-size=40",
        "spring.datasource.hikari.minimum-idle=2"
})
class ConcurrentBookingTest {

    private static final int PARALLEL_REQUESTS = 30;

    @Autowired
    private AppointmentService appointmentService;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private PasswordEncoder passwordEncoder;

    private World world;

    @BeforeEach
    void createIsolatedBusiness() {
        world = RaceTestData.create(jdbc, passwordEncoder);
    }

    @AfterEach
    void cleanUp() {
        RaceTestData.delete(jdbc, world);
    }

    @Test
    void exactlyOneOfManyParallelBookingsWins() throws Exception {
        Instant slot = LocalDate.now(ZoneOffset.UTC).plusDays(30).atTime(10, 0).toInstant(ZoneOffset.UTC);
        AppointmentRequest request = new AppointmentRequest(
                world.staffId(), world.customerId(), world.serviceId(), slot, "race");

        List<String> outcomes = ParallelRunner.runAtOnce(PARALLEL_REQUESTS, () -> {
            try {
                appointmentService.create(world.tenantId(), null, request);
                return "CREATED";
            } catch (ConflictException e) {
                return "CONFLICT";
            } catch (Exception e) {
                return "UNEXPECTED " + e.getClass().getSimpleName() + ": " + e.getMessage();
            }
        });

        Map<String, Long> summary = new TreeMap<>();
        outcomes.forEach(o -> summary.merge(o, 1L, Long::sum));
        System.out.println("Concurrent booking outcomes: " + summary);

        assertEquals(1L, outcomes.stream().filter("CREATED"::equals).count(),
                "exactly one request may succeed. Outcomes: " + summary);
        assertEquals(PARALLEL_REQUESTS - 1L, outcomes.stream().filter("CONFLICT"::equals).count(),
                "every other request must get a clean ConflictException. Outcomes: " + summary);

        Integer rows = jdbc.queryForObject(
                "SELECT count(*) FROM appointments WHERE staff_id = ? AND status <> 'CANCELLED'",
                Integer.class, world.staffId());
        assertEquals(1, rows.intValue(), "exactly one row may exist for that staff member and slot");
    }
}
