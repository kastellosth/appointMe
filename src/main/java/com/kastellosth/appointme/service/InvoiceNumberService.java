package com.kastellosth.appointme.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Not a SEQUENCE: a sequence never gives a number back, so a rollback would leave a hole in the legal series. */
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

    @Transactional(propagation = Propagation.MANDATORY)
    public String next(long tenantId, int year) {
        Integer n = jdbc.queryForObject(NEXT_NUMBER, Integer.class, tenantId, year);
        return format(year, n);
    }

    static String format(int year, int n) {
        return String.format("INV-%d-%04d", year, n);
    }
}
