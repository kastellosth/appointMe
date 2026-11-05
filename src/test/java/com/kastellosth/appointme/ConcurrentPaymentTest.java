package com.kastellosth.appointme;

import com.kastellosth.appointme.dto.PaymentRequest;
import com.kastellosth.appointme.entity.PaymentMethod;
import com.kastellosth.appointme.exception.ConflictException;
import com.kastellosth.appointme.service.PaymentService;
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

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Day 22. Two races, two defences:
 *   A) the SAME payment sent 20 times at once (same Idempotency-Key)  -> exactly 1 payment, 20 identical answers
 *   B) 10 DIFFERENT full payments for one invoice at once              -> exactly 1 wins, the rest 409, never overpaid
 * Runs against the DEV database (Docker must be up), in its own throw-away business.
 */
@SpringBootTest(properties = {
        "spring.datasource.hikari.maximum-pool-size=30",
        "spring.datasource.hikari.minimum-idle=2"
})
class ConcurrentPaymentTest {

    private static final BigDecimal TOTAL = new BigDecimal("100.00");

    @Autowired
    private PaymentService paymentService;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private PasswordEncoder passwordEncoder;

    private World world;
    private Long invoiceId;

    @BeforeEach
    void createIssuedInvoice() {
        world = RaceTestData.create(jdbc, passwordEncoder);
        // An ISSUED invoice of 100.00, inserted directly (the invoice triggers only guard UPDATE and DELETE)
        invoiceId = jdbc.queryForObject("""
                INSERT INTO invoices (tenant_id, customer_id, number, status, subtotal, tax_total, total, issued_at)
                VALUES (?, ?, 'RACE-1', 'ISSUED', 100.00, 0.00, 100.00, now()) RETURNING id
                """, Long.class, world.tenantId(), world.customerId());
    }

    @AfterEach
    void cleanUp() {
        RaceTestData.delete(jdbc, world);
    }

    @Test
    void sameKeySentManyTimesPaysOnce() throws Exception {
        String key = "race-" + UUID.randomUUID();
        PaymentRequest request = new PaymentRequest(new BigDecimal("30.00"), PaymentMethod.CASH, null);

        List<String> outcomes = ParallelRunner.runAtOnce(20, () -> {
            try {
                PaymentService.Result r = paymentService.record(world.tenantId(), invoiceId, key, request);
                return (r.replayed() ? "REPLAYED " : "NEW ") + r.payment().id();
            } catch (Exception e) {
                return "UNEXPECTED " + e.getClass().getSimpleName() + ": " + e.getMessage();
            }
        });
        Map<String, Long> summary = summarize(outcomes);
        System.out.println("Same-key outcomes: " + summary);

        assertEquals(1L, outcomes.stream().filter(o -> o.startsWith("NEW ")).count(), "one NEW. " + summary);
        assertEquals(19L, outcomes.stream().filter(o -> o.startsWith("REPLAYED ")).count(), "19 REPLAYED. " + summary);
        assertEquals(1L, outcomes.stream().map(o -> o.substring(o.indexOf(' ') + 1)).distinct().count(),
                "all 20 answers must name the same payment id. " + summary);
        // the decisive check: the database
        assertEquals(1, count("SELECT count(*) FROM payments WHERE invoice_id = ?"));
    }

    @Test
    void differentPaymentsCannotOverpay() throws Exception {
        // each request pays the FULL total, with its own key: only one may succeed
        List<String> outcomes = ParallelRunner.runAtOnce(10, () -> {
            try {
                paymentService.record(world.tenantId(), invoiceId, "race-" + UUID.randomUUID(),
                        new PaymentRequest(TOTAL, PaymentMethod.CARD, null));
                return "PAID";
            } catch (ConflictException e) {
                return "CONFLICT";
            } catch (Exception e) {
                return "UNEXPECTED " + e.getClass().getSimpleName() + ": " + e.getMessage();
            }
        });
        Map<String, Long> summary = summarize(outcomes);
        System.out.println("Overpay outcomes: " + summary);

        assertEquals(1L, outcomes.stream().filter("PAID"::equals).count(), "exactly one PAID. " + summary);
        assertEquals(9L, outcomes.stream().filter("CONFLICT"::equals).count(), "nine CONFLICT. " + summary);
        assertEquals(1, count("SELECT count(*) FROM payments WHERE invoice_id = ?"));
        assertEquals("PAID", jdbc.queryForObject("SELECT status FROM invoices WHERE id = ?", String.class, invoiceId));
        // failed requests rolled back their keys too: only the winner's key is stored
        assertEquals(1, count("SELECT count(*) FROM idempotency_keys k JOIN payments p ON p.id = k.payment_id "
                + "WHERE p.invoice_id = ?"));
    }

    private int count(String sql) {
        return jdbc.queryForObject(sql, Integer.class, invoiceId);
    }

    private static Map<String, Long> summarize(List<String> outcomes) {
        Map<String, Long> summary = new TreeMap<>();
        outcomes.forEach(o -> summary.merge(o, 1L, Long::sum));
        return summary;
    }
}
