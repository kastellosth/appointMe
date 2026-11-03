package com.kastellosth.appointme.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Hands out invoice numbers: INV-2026-0001, INV-2026-0002, ... one sequence per tenant and year.
 *
 * Why not a normal database SEQUENCE? A sequence never gives a number back. If a transaction
 * takes number 7 and then fails, 7 is lost forever and the legal series has a hole.
 * Here the counter is an ordinary row updated INSIDE the caller's transaction, so:
 *   - the update locks the row until commit, so two transactions can never get the same number;
 *   - if the transaction rolls back, the counter rolls back with it, so no number is lost.
 * The price: issuing invoices for one tenant is serialized (only one at a time) - fine for invoicing.
 */
@Service
public class InvoiceNumberService {

    private static final String NEXT_NUMBER = """
            INSERT INTO invoice_counters (tenant_id, year, last_number)
            VALUES (?, ?, 1)
            ON CONFLICT (tenant_id, year)
            DO UPDATE SET last_number = invoice_counters.last_number + 1
            RETURNING last_number
            """;

    private final JdbcTemplate jdbc;

    public InvoiceNumberService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** MANDATORY: it must run inside the transaction that saves the invoice, never on its own. */
    @Transactional(propagation = Propagation.MANDATORY)
    public String next(long tenantId, int year) {
        Integer n = jdbc.queryForObject(NEXT_NUMBER, Integer.class, tenantId, year);
        return format(year, n);
    }

    static String format(int year, int n) {
        return String.format("INV-%d-%04d", year, n);
    }
}
