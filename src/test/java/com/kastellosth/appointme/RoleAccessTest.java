package com.kastellosth.appointme;

import com.kastellosth.appointme.support.ApiClient;
import com.kastellosth.appointme.support.ApiClient.Business;
import com.kastellosth.appointme.support.DatabaseTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import tools.jackson.databind.JsonNode;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
class RoleAccessTest extends DatabaseTestBase {

    @Value("${local.server.port}")
    private int port;

    private ApiClient api;
    private Business biz;
    private String staffToken;
    private long staffId;

    @BeforeEach
    void createBusinessWithOneStaffMember() {
        api = new ApiClient(port);
        biz = api.createBusiness();

        String email = "staff-" + biz.unique() + "@test.local";
        String password = "staff-pass-" + biz.unique();
        String userToken = api.register(email, password);
        staffId = api.expect(201, "POST", "/staff", biz.token(), "{\"email\":\"" + email + "\"}").json().get("membershipId").asLong();
        api.workAllWeek(biz.token(), staffId);
        staffToken = api.expect(200, "POST", "/auth/select-tenant/" + biz.tenantId(), userToken, null).json().get("accessToken").asString();
    }

    @Test
    void staffCannotDoOwnerActions() {
        String service = "{\"name\":\"X\",\"durationMinutes\":5,\"price\":1.00}";

        api.expect(403, "POST", "/services", staffToken, service);
        api.expect(403, "PUT", "/services/" + biz.serviceId(), staffToken, service);
        api.expect(403, "DELETE", "/services/" + biz.serviceId(), staffToken, null);
        api.expect(403, "POST", "/staff", staffToken, "{\"email\":\"someone@test.local\"}");
        api.expect(403, "PUT", "/staff/" + staffId + "/working-hours", staffToken, "{\"intervals\":[]}");
        api.expect(403, "PUT", "/tenants/current/billing", staffToken, "{\"taxRate\":0.10,\"currency\":\"EUR\"}");
        api.expect(403, "GET", "/invoices", staffToken, null);
        api.expect(403, "GET", "/invoices/1", staffToken, null);
        api.expect(403, "POST", "/invoices/1/payments", staffToken, "{\"amount\":1.00,\"method\":\"CASH\"}", "Idempotency-Key", "k");
        api.expect(403, "GET", "/audit-events", staffToken, null);
        api.expect(403, "GET", "/reminders", staffToken, null);
    }

    @Test
    void staffCanReadWhatTheirWorkNeeds() {
        api.expect(200, "GET", "/services", staffToken, null);
        api.expect(200, "GET", "/customers", staffToken, null);
        api.expect(200, "GET", "/staff", staffToken, null);
    }

    @Test
    void staffOnlySeeTheirOwnAppointments() {
        Instant slot = ApiClient.futureSlot(10);
        long owners = api.expect(201, "POST", "/appointments", biz.token(), biz.booking(slot)).id();
        long own = api.expect(201, "POST", "/appointments", staffToken,
                biz.booking(staffId, biz.customerId(), biz.serviceId(), slot)).id();

        api.expect(200, "GET", "/appointments/" + own, staffToken, null);
        api.expect(404, "GET", "/appointments/" + owners, staffToken, null);
        api.expect(404, "PATCH", "/appointments/" + owners + "/cancel", staffToken, null);

        String range = "?from=" + slot.minusSeconds(3600) + "&to=" + slot.plusSeconds(3600);
        JsonNode seenByStaff = api.expect(200, "GET", "/appointments" + range, staffToken, null).json();
        assertEquals(1, seenByStaff.size());
        assertEquals(own, seenByStaff.get(0).get("id").asLong());
        assertEquals(2, api.expect(200, "GET", "/appointments" + range, biz.token(), null).json().size(), "the owner sees both");
    }

    @Test
    void staffCannotBookForAColleague() {
        api.expect(403, "POST", "/appointments", staffToken, biz.booking(ApiClient.futureSlot(11)));
    }
}
