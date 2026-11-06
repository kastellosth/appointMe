package com.kastellosth.appointme;

import com.kastellosth.appointme.support.ApiClient;
import com.kastellosth.appointme.support.ApiClient.Business;
import com.kastellosth.appointme.support.DatabaseTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/** The attacks from docs/security-attacks.md, repeated on every build. */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
class TenantIsolationTest extends DatabaseTestBase {

    private static final String MISSING_ID = "999999999";

    @Value("${local.server.port}")
    private int port;

    private ApiClient api;
    private Business alice;
    private Business bob;
    private long bobsAppointment;
    private long bobsInvoice;

    @BeforeEach
    void createTwoBusinesses() {
        api = new ApiClient(port);
        alice = api.createBusiness();
        bob = api.createBusiness();
        bobsAppointment = api.expect(201, "POST", "/appointments", bob.token(), bob.booking(ApiClient.futureSlot(10))).id();
        bobsInvoice = api.expect(201, "POST", "/invoices", bob.token(),
                "{\"customerId\":" + bob.customerId() + ",\"lines\":[{\"description\":\"Secret\",\"quantity\":1,\"unitPrice\":10.00}]}").id();
    }

    @Test
    void readingAnotherBusinessIsNotFound() {
        for (String path : new String[]{
                "/customers/" + bob.customerId(),
                "/services/" + bob.serviceId(),
                "/appointments/" + bobsAppointment,
                "/invoices/" + bobsInvoice,
                "/invoices/" + bobsInvoice + "/payments",
                "/staff/" + bob.staffId() + "/working-hours"}) {
            api.expect(404, "GET", path, alice.token(), null);
        }
    }

    @Test
    void anotherBusinessLooksTheSameAsAnIdThatDoesNotExist() {
        ApiClient.Response foreign = api.expect(404, "GET", "/customers/" + bob.customerId(), alice.token(), null);
        ApiClient.Response missing = api.expect(404, "GET", "/customers/" + MISSING_ID, alice.token(), null);

        assertEquals(missing.json().get("title").asString(), foreign.json().get("title").asString());
        assertEquals(comparable(missing.body(), MISSING_ID), comparable(foreign.body(), String.valueOf(bob.customerId())));
    }

    @Test
    void changingAnotherBusinessIsNotFoundAndChangesNothing() {
        String customerBefore = api.expect(200, "GET", "/customers/" + bob.customerId(), bob.token(), null).body();
        String serviceBefore = api.expect(200, "GET", "/services/" + bob.serviceId(), bob.token(), null).body();
        String appointmentBefore = api.expect(200, "GET", "/appointments/" + bobsAppointment, bob.token(), null).body();
        String invoiceBefore = api.expect(200, "GET", "/invoices/" + bobsInvoice, bob.token(), null).body();

        api.expect(404, "PUT", "/customers/" + bob.customerId(), alice.token(), "{\"fullName\":\"Hacked\"}");
        api.expect(404, "DELETE", "/customers/" + bob.customerId(), alice.token(), null);
        api.expect(404, "PUT", "/services/" + bob.serviceId(), alice.token(),
                "{\"name\":\"Hacked\",\"durationMinutes\":5,\"price\":0.00}");
        api.expect(404, "DELETE", "/services/" + bob.serviceId(), alice.token(), null);
        api.expect(404, "PATCH", "/appointments/" + bobsAppointment + "/status", alice.token(), "{\"status\":\"NO_SHOW\"}");
        api.expect(404, "PATCH", "/appointments/" + bobsAppointment + "/cancel", alice.token(), null);
        api.expect(404, "PATCH", "/appointments/" + bobsAppointment + "/reschedule", alice.token(),
                "{\"startsAt\":\"" + ApiClient.futureSlot(12) + "\"}");
        api.expect(404, "POST", "/invoices/" + bobsInvoice + "/issue", alice.token(), null);
        api.expect(404, "POST", "/invoices/" + bobsInvoice + "/void", alice.token(), null);
        api.expect(404, "POST", "/invoices/" + bobsInvoice + "/payments", alice.token(),
                "{\"amount\":10.00,\"method\":\"CASH\"}", "Idempotency-Key", "attack-" + alice.unique());
        api.expect(404, "PUT", "/staff/" + bob.staffId() + "/working-hours", alice.token(), "{\"intervals\":[]}");

        assertEquals(customerBefore, api.expect(200, "GET", "/customers/" + bob.customerId(), bob.token(), null).body());
        assertEquals(serviceBefore, api.expect(200, "GET", "/services/" + bob.serviceId(), bob.token(), null).body());
        assertEquals(appointmentBefore, api.expect(200, "GET", "/appointments/" + bobsAppointment, bob.token(), null).body());
        assertEquals(invoiceBefore, api.expect(200, "GET", "/invoices/" + bobsInvoice, bob.token(), null).body());
    }

    @Test
    void bookingWithAnotherBusinessesIdsIsNotFound() {
        Instant slot = ApiClient.futureSlot(11);

        api.expect(404, "POST", "/appointments", alice.token(),
                alice.booking(alice.staffId(), bob.customerId(), alice.serviceId(), slot));
        api.expect(404, "POST", "/appointments", alice.token(),
                alice.booking(alice.staffId(), alice.customerId(), bob.serviceId(), slot));
        api.expect(404, "POST", "/appointments", alice.token(),
                alice.booking(bob.staffId(), alice.customerId(), alice.serviceId(), slot));
    }

    @Test
    void listsNeverContainAnotherBusiness() {
        for (String path : new String[]{"/customers?size=100", "/services", "/staff", "/invoices?size=100", "/audit-events?size=100"}) {
            String body = api.expect(200, "GET", path, alice.token(), null).body();
            assertFalse(body.contains(bob.unique()), path + " leaked another business: " + body);
        }
        String from = ApiClient.futureSlot(0).minusSeconds(86400).toString();
        String to = ApiClient.futureSlot(0).plusSeconds(86400 * 2).toString();
        assertEquals(0, api.expect(200, "GET", "/appointments?from=" + from + "&to=" + to, alice.token(), null).json().size());
    }

    @Test
    void theTenantComesOnlyFromTheToken() {
        assertFalse(api.expect(200, "GET", "/customers?tenantId=" + bob.tenantId(), alice.token(), null).body().contains(bob.unique()),
                "query parameter must be ignored");
        assertFalse(api.send("GET", "/customers", alice.token(), null, "X-Tenant-Id", String.valueOf(bob.tenantId())).body().contains(bob.unique()),
                "header must be ignored");

        String name = "Mine " + alice.unique();
        long id = api.expect(201, "POST", "/customers", alice.token(),
                "{\"fullName\":\"" + name + "\",\"tenantId\":" + bob.tenantId() + ",\"tenant\":{\"id\":" + bob.tenantId() + "}}").id();
        api.expect(200, "GET", "/customers/" + id, alice.token(), null);
        api.expect(404, "GET", "/customers/" + id, bob.token(), null);
    }

    @Test
    void cannotSelectABusinessYouDoNotBelongTo() {
        api.expect(404, "POST", "/auth/select-tenant/" + bob.tenantId(), alice.userToken(), null);
    }

    @Test
    void badTokensAreRefused() {
        api.expect(401, "GET", "/customers", null, null);
        api.expect(401, "GET", "/customers", "not-a-token", null);

        String[] parts = alice.token().split("\\.");
        String payload = decode(parts[1]);
        String forgedPayload = payload.replace("\"tenant_id\":" + alice.tenantId(), "\"tenant_id\":" + bob.tenantId());
        assertFalse(payload.equals(forgedPayload), "the forged token must really change the tenant: " + payload);

        api.expect(401, "GET", "/customers", parts[0] + "." + encode(forgedPayload) + "." + parts[2], null);
        api.expect(401, "GET", "/customers", encode("{\"alg\":\"none\"}") + "." + encode(forgedPayload) + ".", null);
    }

    @Test
    void aUserTokenWithoutABusinessCannotUseBusinessEndpoints() {
        api.expect(403, "GET", "/customers", alice.userToken(), null);
    }

    private static String comparable(String body, String id) {
        return body.replace(id, "ID").replaceAll("\"timestamp\":\"[^\"]+\"", "");
    }

    private static String decode(String part) {
        return new String(Base64.getUrlDecoder().decode(part), StandardCharsets.UTF_8);
    }

    private static String encode(String text) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(text.getBytes(StandardCharsets.UTF_8));
    }
}
