package com.kastellosth.appointme;

import com.kastellosth.appointme.support.DatabaseTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
class HealthEndpointTest extends DatabaseTestBase {

    @LocalServerPort
    private int port;

    private HttpResponse<String> get(String path) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).GET().build();
        try (HttpClient client = HttpClient.newHttpClient()) {
            return client.send(request, HttpResponse.BodyHandlers.ofString());
        }
    }

    @Test
    void healthIsPublicAndShowsNoDetails() throws Exception {
        HttpResponse<String> response = get("/actuator/health");

        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("\"status\":\"UP\""), response.body());
        assertFalse(response.body().contains("postgres"), response.body());
    }

    @Test
    void readinessIsPublicBecauseTheContainerHealthCheckHasNoToken() throws Exception {
        assertEquals(200, get("/actuator/health/readiness").statusCode());
    }

    @Test
    void otherActuatorEndpointsNeedALogin() throws Exception {
        assertEquals(401, get("/actuator/env").statusCode());
        assertEquals(401, get("/actuator/beans").statusCode());
    }
}
