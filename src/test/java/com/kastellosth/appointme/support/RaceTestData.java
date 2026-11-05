package com.kastellosth.appointme.support;

import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.PreparedStatement;
import java.sql.Statement;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.UUID;

/**
 * Creates a brand-new, isolated business (tenant) for a concurrency test, and removes it afterwards.
 * Plain SQL on purpose: it does not depend on how the entity classes look, and it cannot touch
 * your seed data because everything gets a random name.
 */
public final class RaceTestData {

    private RaceTestData() {
    }

    public record World(Long tenantId, Long userId, Long staffId, Long customerId, Long serviceId,
                        String email, String password) {
    }

    public static World create(JdbcTemplate jdbc, PasswordEncoder encoder) {
        String unique = UUID.randomUUID().toString().substring(0, 8);
        String email = "race-" + unique + "@test.local";
        String password = "race-pass-" + unique;

        Long tenantId = jdbc.queryForObject(
                "INSERT INTO tenants (name, slug, timezone) VALUES (?, ?, 'UTC') RETURNING id",
                Long.class, "Race Test " + unique, "race-test-" + unique);
        Long userId = jdbc.queryForObject(
                "INSERT INTO users (email, password_hash, full_name) VALUES (?, ?, 'Race Owner') RETURNING id",
                Long.class, email, encoder.encode(password));
        Long staffId = jdbc.queryForObject(
                "INSERT INTO memberships (user_id, tenant_id, role) VALUES (?, ?, 'OWNER') RETURNING id",
                Long.class, userId, tenantId);
        Long customerId = jdbc.queryForObject(
                "INSERT INTO customers (tenant_id, full_name, email) VALUES (?, 'Race Customer', ?) RETURNING id",
                Long.class, tenantId, "customer-" + unique + "@test.local");
        Long serviceId = jdbc.queryForObject(
                "INSERT INTO services (tenant_id, name, duration_minutes, price) VALUES (?, 'Race Cut', 30, 10.00) RETURNING id",
                Long.class, tenantId);

        // Open every weekday 08:00-18:00 (the tenant's zone is UTC), so any test date is bookable.
        for (int day = 1; day <= 7; day++) {
            jdbc.update("INSERT INTO working_hours (tenant_id, staff_id, day_of_week, start_time, end_time) "
                    + "VALUES (?, ?, ?, TIME '08:00', TIME '18:00')", tenantId, staffId, day);
        }
        return new World(tenantId, userId, staffId, customerId, serviceId, email, password);
    }

    /** Deletes everything the test created, children first (foreign keys). Safe to call twice. */
    public static void delete(JdbcTemplate jdbc, World w) {
        if (w == null) {
            return;
        }
        deleteMoney(jdbc, w.tenantId());
        jdbc.update("DELETE FROM appointments WHERE tenant_id = ?", w.tenantId());
        jdbc.update("DELETE FROM working_hours WHERE tenant_id = ?", w.tenantId());
        jdbc.update("DELETE FROM customers WHERE tenant_id = ?", w.tenantId());
        jdbc.update("DELETE FROM services WHERE tenant_id = ?", w.tenantId());
        jdbc.update("DELETE FROM memberships WHERE tenant_id = ?", w.tenantId());
        jdbc.update("DELETE FROM users WHERE id = ?", w.userId());
        jdbc.update("DELETE FROM tenants WHERE id = ?", w.tenantId());
    }

    /**
     * Payments and issued invoices are protected by triggers that refuse DELETE (on purpose).
     * A throw-away test business must still be removable, so this switches triggers off for ONE
     * connection only: session_replication_role = replica (needs a superuser; the Docker user is one).
     * Other connections keep every rule. Never do this in application code.
     */
    private static void deleteMoney(JdbcTemplate jdbc, Long tenantId) {
        jdbc.execute((ConnectionCallback<Void>) con -> {
            try (Statement st = con.createStatement()) {
                st.execute("SET session_replication_role = replica");
            }
            try {
                for (String table : new String[]{"audit_events", "idempotency_keys", "payments", "invoice_lines", "invoices",
                        "invoice_counters"}) {
                    try (PreparedStatement ps = con.prepareStatement("DELETE FROM " + table + " WHERE tenant_id = ?")) {
                        ps.setLong(1, tenantId);
                        ps.executeUpdate();
                    }
                }
            } finally {
                // the connection goes back to the pool: always restore the normal mode
                try (Statement st = con.createStatement()) {
                    st.execute("SET session_replication_role = DEFAULT");
                }
            }
            return null;
        });
    }
}
