package com.kastellosth.appointme;

import com.kastellosth.appointme.support.DatabaseTestBase;
import com.kastellosth.appointme.support.ParallelRunner;
import com.kastellosth.appointme.support.RaceTestData;
import com.kastellosth.appointme.support.RaceTestData.World;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT, properties = {
        "spring.datasource.hikari.maximum-pool-size=40",
        "spring.datasource.hikari.minimum-idle=2"
})
class ConcurrentBookingHttpTest extends DatabaseTestBase {

    private static final int PARALLEL_REQUESTS = 30;
    private static final Pattern ACCESS_TOKEN = Pattern.compile("\"accessToken\"\\s*:\\s*\"([^\"]+)\"");

    @Value("${local.server.port}")
    private int port;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private PasswordEncoder passwordEncoder;

    private final HttpClient http = HttpClient.newHttpClient();
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
    void exactlyOneHttpBookingGetsCreatedTheRestGetConflict() throws Exception {
        String userToken = tokenFrom(post("/auth/login", null,
                "{\"email\":\"" + world.email() + "\",\"password\":\"" + world.password() + "\"}"));
        String tenantToken = tokenFrom(post("/auth/select-tenant/" + world.tenantId(), userToken, null));

        Instant slot = LocalDate.now(ZoneOffset.UTC).plusDays(30).atTime(10, 0).toInstant(ZoneOffset.UTC);
        String body = "{\"staffId\":" + world.staffId() + ",\"customerId\":" + world.customerId()
                + ",\"serviceId\":" + world.serviceId() + ",\"startsAt\":\"" + slot + "\",\"notes\":\"http race\"}";

        List<Integer> statuses = ParallelRunner.runAtOnce(PARALLEL_REQUESTS, () -> {
            try {
                return post("/appointments", tenantToken, body).statusCode();
            } catch (Exception e) {
                return -1;
            }
        });

        Map<Integer, Long> summary = new TreeMap<>();
        statuses.forEach(s -> summary.merge(s, 1L, Long::sum));
        System.out.println("Concurrent HTTP booking statuses: " + summary);

        assertEquals(1L, statuses.stream().filter(s -> s == 201).count(), "exactly one 201. Statuses: " + summary);
        assertEquals(PARALLEL_REQUESTS - 1L, statuses.stream().filter(s -> s == 409).count(),
                "every other response must be 409. Statuses: " + summary);

        Integer rows = jdbc.queryForObject(
                "SELECT count(*) FROM appointments WHERE staff_id = ? AND status <> 'CANCELLED'",
                Integer.class, world.staffId());
        assertEquals(1, rows.intValue(), "exactly one row may exist for that staff member and slot");
    }

    private HttpResponse<String> post(String path, String bearerToken, String json) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .POST(json == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(json));
        if (json != null) {
            builder.header("Content-Type", "application/json");
        }
        if (bearerToken != null) {
            builder.header("Authorization", "Bearer " + bearerToken);
        }
        return http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private String tokenFrom(HttpResponse<String> response) {
        assertEquals(200, response.statusCode(), "login step failed: " + response.body());
        Matcher m = ACCESS_TOKEN.matcher(response.body());
        assertTrue(m.find(), "no accessToken in: " + response.body());
        return m.group(1);
    }
}
