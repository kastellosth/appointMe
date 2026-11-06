package com.kastellosth.appointme.support;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** A real HTTP client, so these tests see what a real caller sees, including the security filters. */
public final class ApiClient {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private final HttpClient http = HttpClient.newHttpClient();
    private final int port;

    public ApiClient(int port) {
        this.port = port;
    }

    public record Response(int status, String body, HttpResponse<String> raw) {
        public JsonNode json() {
            return JSON.readTree(body);
        }

        public long id() {
            return json().get("id").asLong();
        }
    }

    public Response send(String method, String path, String token, String json, String... headers) {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .method(method, json == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(json));
        if (json != null) {
            request.header("Content-Type", "application/json");
        }
        if (token != null) {
            request.header("Authorization", "Bearer " + token);
        }
        if (headers.length > 0) {
            request.headers(headers);
        }
        try {
            HttpResponse<String> response = http.send(request.build(), HttpResponse.BodyHandlers.ofString());
            return new Response(response.statusCode(), response.body(), response);
        } catch (Exception e) {
            throw new IllegalStateException(method + " " + path + " failed", e);
        }
    }

    public Response expect(int status, String method, String path, String token, String json, String... headers) {
        Response response = send(method, path, token, json, headers);
        assertEquals(status, response.status(), method + " " + path + ": " + response.body());
        return response;
    }

    public String login(String email, String password) {
        return expect(200, "POST", "/auth/login", null,
                "{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}").json().get("accessToken").asString();
    }

    public String register(String email, String password) {
        expect(201, "POST", "/auth/register", null,
                "{\"email\":\"" + email + "\",\"password\":\"" + password + "\",\"fullName\":\"Test Person\"}");
        return login(email, password);
    }

    /** One business with an owner, a customer, a service, and working hours all week. Timezone UTC. */
    public Business createBusiness() {
        String unique = UUID.randomUUID().toString().substring(0, 8);
        String email = "owner-" + unique + "@test.local";
        String password = "owner-pass-" + unique;
        String userToken = register(email, password);

        long tenantId = expect(201, "POST", "/tenants", userToken,
                "{\"name\":\"Biz " + unique + "\",\"slug\":\"biz-" + unique + "\",\"timezone\":\"UTC\"}").id();
        String token = expect(200, "POST", "/auth/select-tenant/" + tenantId, userToken, null)
                .json().get("accessToken").asString();
        long staffId = expect(200, "GET", "/staff", token, null).json().get(0).get("membershipId").asLong();
        long customerId = expect(201, "POST", "/customers", token,
                "{\"fullName\":\"Customer " + unique + "\",\"email\":\"c-" + unique + "@test.local\"}").id();
        long serviceId = expect(201, "POST", "/services", token,
                "{\"name\":\"Cut " + unique + "\",\"durationMinutes\":30,\"price\":25.00}").id();
        workAllWeek(token, staffId);
        return new Business(tenantId, token, userToken, staffId, customerId, serviceId, unique);
    }

    public void workAllWeek(String ownerToken, long staffId) {
        StringBuilder intervals = new StringBuilder();
        for (String day : new String[]{"MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY", "SATURDAY", "SUNDAY"}) {
            intervals.append(intervals.isEmpty() ? "" : ",")
                    .append("{\"dayOfWeek\":\"").append(day).append("\",\"start\":\"08:00\",\"end\":\"18:00\"}");
        }
        expect(200, "PUT", "/staff/" + staffId + "/working-hours", ownerToken, "{\"intervals\":[" + intervals + "]}");
    }

    public static Instant futureSlot(int hour) {
        return LocalDate.now(ZoneOffset.UTC).plusDays(30).atTime(hour, 0).toInstant(ZoneOffset.UTC);
    }

    public record Business(long tenantId, String token, String userToken, long staffId, long customerId,
                           long serviceId, String unique) {
        public String booking(long staff, long customer, long service, Instant startsAt) {
            return "{\"staffId\":" + staff + ",\"customerId\":" + customer + ",\"serviceId\":" + service
                    + ",\"startsAt\":\"" + startsAt + "\"}";
        }

        public String booking(Instant startsAt) {
            return booking(staffId, customerId, serviceId, startsAt);
        }
    }
}
