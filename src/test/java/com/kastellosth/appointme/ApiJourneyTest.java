package com.kastellosth.appointme;

import com.kastellosth.appointme.support.ApiClient;
import com.kastellosth.appointme.support.ApiClient.Business;
import com.kastellosth.appointme.support.DatabaseTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.JsonNode;

import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
class ApiJourneyTest extends DatabaseTestBase {

    @Value("${local.server.port}")
    private int port;
    @Autowired
    private JdbcTemplate jdbc;

    private ApiClient api;
    private Business biz;

    @BeforeEach
    void createBusiness() {
        api = new ApiClient(port);
        biz = api.createBusiness();
    }

    @Test
    void doubleBookingIsAConflictButTheNextSlotIsFine() {
        Instant ten = ApiClient.futureSlot(10);

        api.expect(201, "POST", "/appointments", biz.token(), biz.booking(ten));
        api.expect(409, "POST", "/appointments", biz.token(), biz.booking(ten));
        api.expect(409, "POST", "/appointments", biz.token(), biz.booking(ten.plusSeconds(15 * 60)));
        api.expect(201, "POST", "/appointments", biz.token(), biz.booking(ten.plusSeconds(30 * 60)));
    }

    @Test
    void cancellingFreesTheSlotAndAvailabilityShowsIt() {
        Instant ten = ApiClient.futureSlot(10);
        String date = ten.atZone(ZoneOffset.UTC).toLocalDate().toString();
        String availability = "/availability?staffId=" + biz.staffId() + "&serviceId=" + biz.serviceId()
                + "&date=" + date + "&stepMinutes=30";

        assertTrue(slotOffered(availability, ten), "free before booking");

        long id = api.expect(201, "POST", "/appointments", biz.token(), biz.booking(ten)).id();
        assertFalse(slotOffered(availability, ten), "taken after booking");

        api.expect(200, "PATCH", "/appointments/" + id + "/cancel", biz.token(), null);
        assertTrue(slotOffered(availability, ten), "free again after cancelling");
        api.expect(201, "POST", "/appointments", biz.token(), biz.booking(ten));
    }

    @Test
    void bookingOutsideWorkingHoursIsRefused() {
        Instant night = ApiClient.futureSlot(23);

        api.expect(409, "POST", "/appointments", biz.token(), biz.booking(night));
    }

    @Test
    void fromBookingToPaidInvoice() {
        long appointmentId = api.expect(201, "POST", "/appointments", biz.token(),
                biz.booking(ApiClient.futureSlot(10))).id();

        // The API refuses to complete a future appointment, and refuses to book in the past.
        jdbc.update("UPDATE appointments SET starts_at = starts_at - interval '60 days', "
                + "ends_at = ends_at - interval '60 days' WHERE id = ?", appointmentId);
        api.expect(200, "PATCH", "/appointments/" + appointmentId + "/status", biz.token(), "{\"status\":\"COMPLETED\"}");

        JsonNode invoices = api.expect(200, "GET", "/invoices", biz.token(), null).json().get("content");
        assertEquals(1, invoices.size(), "completing the appointment creates exactly one draft");
        long invoiceId = invoices.get(0).get("id").asLong();
        assertEquals("DRAFT", invoices.get(0).get("status").asString());
        assertEquals(appointmentId, invoices.get(0).get("appointmentId").asLong());

        JsonNode issued = api.expect(200, "POST", "/invoices/" + invoiceId + "/issue", biz.token(), null).json();
        assertEquals("ISSUED", issued.get("status").asString());
        assertTrue(issued.get("number").asString().matches("INV-\\d{4}-0001"), issued.get("number").asString());

        String pay = "{\"amount\":" + issued.get("total").asString() + ",\"method\":\"CASH\"}";
        String paths = "/invoices/" + invoiceId + "/payments";
        ApiClient.Response first = api.expect(201, "POST", paths, biz.token(), pay, "Idempotency-Key", "journey-key-1");
        ApiClient.Response replay = api.expect(201, "POST", paths, biz.token(), pay, "Idempotency-Key", "journey-key-1");

        assertEquals("false", first.raw().headers().firstValue("Idempotent-Replayed").orElseThrow());
        assertEquals("true", replay.raw().headers().firstValue("Idempotent-Replayed").orElseThrow());
        assertEquals(first.id(), replay.id(), "a replay returns the first payment");

        api.expect(422, "POST", paths, biz.token(), "{\"amount\":1.00,\"method\":\"CASH\"}", "Idempotency-Key", "journey-key-1");
        api.expect(409, "POST", paths, biz.token(), pay, "Idempotency-Key", "journey-key-2");

        assertEquals(1, api.expect(200, "GET", paths, biz.token(), null).json().get("payments").size());
        assertEquals("PAID", api.expect(200, "GET", "/invoices/" + invoiceId, biz.token(), null).json().get("status").asString());
        api.expect(409, "POST", "/invoices/" + invoiceId + "/void", biz.token(), null);

        assertTrue(api.expect(200, "GET", "/audit-events", biz.token(), null).json().get("totalElements").asLong() >= 4,
                "the journey leaves an audit trail");
    }

    private boolean slotOffered(String availabilityPath, Instant start) {
        for (JsonNode slot : api.expect(200, "GET", availabilityPath, biz.token(), null).json().get("slots")) {
            if (Instant.from(java.time.format.DateTimeFormatter.ISO_OFFSET_DATE_TIME.parse(slot.get("startsAt").asString())).equals(start)) {
                return true;
            }
        }
        return false;
    }
}
