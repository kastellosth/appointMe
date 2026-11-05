package com.kastellosth.appointme;

import com.kastellosth.appointme.dto.AuditEventResponse;
import com.kastellosth.appointme.dto.InvoiceLineRequest;
import com.kastellosth.appointme.dto.InvoiceRequest;
import com.kastellosth.appointme.dto.PaymentRequest;
import com.kastellosth.appointme.entity.AppointmentStatus;
import com.kastellosth.appointme.entity.AuditAction;
import com.kastellosth.appointme.entity.AuditEntityType;
import com.kastellosth.appointme.entity.PaymentMethod;
import com.kastellosth.appointme.exception.BadRequestException;
import com.kastellosth.appointme.exception.ConflictException;
import com.kastellosth.appointme.service.AppointmentService;
import com.kastellosth.appointme.service.AuditService;
import com.kastellosth.appointme.service.CustomerService;
import com.kastellosth.appointme.service.InvoiceService;
import com.kastellosth.appointme.service.PaymentService;
import com.kastellosth.appointme.support.ParallelRunner;
import com.kastellosth.appointme.support.RaceTestData;
import com.kastellosth.appointme.support.RaceTestData.World;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The promise of the audit log: a change and its history are one transaction.
 * Runs against the DEV database (Docker must be up), in its own throw-away businesses.
 */
@SpringBootTest(properties = {
        "spring.datasource.hikari.maximum-pool-size=30",
        "spring.datasource.hikari.minimum-idle=2"
})
class AuditTrailTest {

    @Autowired
    private AuditService audit;
    @Autowired
    private InvoiceService invoices;
    @Autowired
    private PaymentService payments;
    @Autowired
    private AppointmentService appointments;
    @Autowired
    private CustomerService customers;
    @Autowired
    private TransactionTemplate tx;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private PasswordEncoder passwordEncoder;

    private World world;
    private World otherWorld;

    @BeforeEach
    void setUp() {
        world = RaceTestData.create(jdbc, passwordEncoder);
        otherWorld = RaceTestData.create(jdbc, passwordEncoder);
        logInAs(world.userId());
    }

    @AfterEach
    void cleanUp() {
        SecurityContextHolder.clearContext();
        RaceTestData.delete(jdbc, world);
        RaceTestData.delete(jdbc, otherWorld);
    }

    @Test
    void theStoryOfAnInvoiceIsComplete() {
        Long id = issuedInvoice("100.00");
        pay(id, "race-" + UUID.randomUUID(), "30.00");
        pay(id, "race-" + UUID.randomUUID(), "70.00");

        // oldest first, to read it like a story
        assertEquals(List.of(AuditAction.INVOICE_CREATED, AuditAction.INVOICE_ISSUED,
                        AuditAction.PAYMENT_RECORDED, AuditAction.PAYMENT_RECORDED, AuditAction.INVOICE_PAID),
                historyOf(world, AuditEntityType.INVOICE, id).stream().map(AuditEventResponse::action).toList().reversed());

        AuditEventResponse newest = historyOf(world, AuditEntityType.INVOICE, id).getFirst();
        assertEquals(world.userId(), newest.actorUserId());
        assertEquals("ISSUED -> PAID", newest.details());
    }

    @Test
    void noLoggedInUserMeansTheSystemDidIt() {
        SecurityContextHolder.clearContext();
        Long id = draftInvoice("10.00");
        assertNull(historyOf(world, AuditEntityType.INVOICE, id).getFirst().actorUserId());
    }

    @Test
    void aRefusedRequestLeavesNoEvent() {
        Long id = issuedInvoice("100.00");
        int before = countEvents(world);

        assertThrows(ConflictException.class, () -> pay(id, "race-" + UUID.randomUUID(), "100.01"));

        assertEquals(before, countEvents(world));
    }

    @Test
    void rollingBackTheChangeRemovesItsEvent() {
        Long id = issuedInvoice("100.00");
        int before = countEvents(world);

        tx.executeWithoutResult(status -> {
            invoices.voidInvoice(world.tenantId(), id);
            status.setRollbackOnly();   // something later in this transaction went wrong
        });

        assertEquals("ISSUED", statusOf(id));
        assertEquals(before, countEvents(world));
    }

    @Test
    void aFailedAuditInsertRollsBackTheChange() {
        Long id = issuedInvoice("100.00");
        int before = countEvents(world);

        // 501 characters do not fit into details: the event cannot be written
        assertThrows(DataIntegrityViolationException.class, () -> tx.executeWithoutResult(status -> {
            invoices.voidInvoice(world.tenantId(), id);
            audit.record(world.tenantId(), AuditAction.INVOICE_VOIDED, id, "x".repeat(501));
        }));

        assertEquals("ISSUED", statusOf(id));
        assertEquals(before, countEvents(world));
    }

    @Test
    void recordingOutsideATransactionIsRefused() {
        assertThrows(IllegalTransactionStateException.class,
                () -> audit.record(world.tenantId(), AuditAction.INVOICE_PAID, 1L, null));
    }

    @Test
    void aCustomerDeleteThatFailsAtCommitLeavesNoEvent() {
        draftInvoice("10.00");   // the customer now has an invoice, so the foreign key refuses the delete
        int before = countEvents(world);

        assertThrows(DataIntegrityViolationException.class, () -> customers.delete(world.tenantId(), world.customerId()));

        assertEquals(before, countEvents(world));
    }

    @Test
    void aDeletedCustomerKeepsItsHistory() {
        customers.delete(world.tenantId(), world.customerId());

        List<AuditEventResponse> history = historyOf(world, AuditEntityType.CUSTOMER, world.customerId());
        assertEquals(List.of(AuditAction.CUSTOMER_DELETED), history.stream().map(AuditEventResponse::action).toList());
    }

    @Test
    void completingAnAppointmentRecordsBothChanges() {
        Long appointmentId = jdbc.queryForObject("""
                INSERT INTO appointments (tenant_id, staff_id, customer_id, service_id, starts_at, ends_at)
                VALUES (?, ?, ?, ?, now() - interval '2 hours', now() - interval '90 minutes') RETURNING id
                """, Long.class, world.tenantId(), world.staffId(), world.customerId(), world.serviceId());

        appointments.updateStatus(world.tenantId(), null, appointmentId, AppointmentStatus.COMPLETED);

        assertEquals("SCHEDULED -> COMPLETED",
                historyOf(world, AuditEntityType.APPOINTMENT, appointmentId).getFirst().details());
        Long invoiceId = jdbc.queryForObject("SELECT id FROM invoices WHERE appointment_id = ?", Long.class, appointmentId);
        assertEquals(List.of(AuditAction.INVOICE_CREATED),
                historyOf(world, AuditEntityType.INVOICE, invoiceId).stream().map(AuditEventResponse::action).toList());
    }

    @Test
    void aRetriedPaymentAddsNoHistoryEvenUnderARace() throws Exception {
        Long id = issuedInvoice("100.00");
        String key = "race-" + UUID.randomUUID();

        List<String> outcomes = ParallelRunner.runAtOnce(20, () -> {
            try {
                return pay(id, key, "30.00").replayed() ? "REPLAYED" : "NEW";
            } catch (Exception e) {
                return "UNEXPECTED " + e;
            }
        });

        assertEquals(1L, outcomes.stream().filter("NEW"::equals).count(), outcomes.toString());
        assertEquals(19L, outcomes.stream().filter("REPLAYED"::equals).count(), outcomes.toString());
        assertEquals(1L, historyOf(world, AuditEntityType.INVOICE, id).stream()
                .filter(e -> e.action() == AuditAction.PAYMENT_RECORDED).count());
    }

    @Test
    void anotherBusinessSeesNothing() {
        Long id = issuedInvoice("100.00");
        assertTrue(countEvents(world) > 0);

        assertEquals(0, audit.list(otherWorld.tenantId(), null, null, null, null, null, null, 0, 20).totalElements());
        assertEquals(List.of(), historyOf(otherWorld, AuditEntityType.INVOICE, id));
    }

    @Test
    void entityIdWithoutEntityTypeIsABadRequest() {
        assertThrows(BadRequestException.class,
                () -> audit.list(world.tenantId(), null, 1L, null, null, null, null, 0, 20));
    }

    @Test
    void theDatabaseRefusesToRewriteHistory() {
        issuedInvoice("100.00");
        int before = countEvents(world);

        assertThrows(DataIntegrityViolationException.class,
                () -> jdbc.update("UPDATE audit_events SET details = 'nothing happened' WHERE tenant_id = ?", world.tenantId()));
        assertThrows(DataIntegrityViolationException.class,
                () -> jdbc.update("DELETE FROM audit_events WHERE tenant_id = ?", world.tenantId()));

        assertEquals(before, countEvents(world));
    }

    // ----------------------------------------------------------------------------------- helpers

    private void logInAs(Long userId) {
        Jwt jwt = Jwt.withTokenValue("test").header("alg", "none").subject(String.valueOf(userId)).build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
    }

    private Long draftInvoice(String price) {
        return invoices.create(world.tenantId(), new InvoiceRequest(world.customerId(), null, null,
                List.of(new InvoiceLineRequest("Race line", 1, new BigDecimal(price), BigDecimal.ZERO)))).id();
    }

    private Long issuedInvoice(String price) {
        Long id = draftInvoice(price);
        invoices.issue(world.tenantId(), id);
        return id;
    }

    private PaymentService.Result pay(Long invoiceId, String key, String amount) {
        return payments.record(world.tenantId(), invoiceId, key,
                new PaymentRequest(new BigDecimal(amount), PaymentMethod.CASH, null));
    }

    private List<AuditEventResponse> historyOf(World w, AuditEntityType type, Long id) {
        return audit.list(w.tenantId(), type, id, null, null, null, null, 0, 100).content();
    }

    private int countEvents(World w) {
        return jdbc.queryForObject("SELECT count(*) FROM audit_events WHERE tenant_id = ?", Integer.class, w.tenantId());
    }

    private String statusOf(Long invoiceId) {
        return jdbc.queryForObject("SELECT status FROM invoices WHERE id = ?", String.class, invoiceId);
    }
}
