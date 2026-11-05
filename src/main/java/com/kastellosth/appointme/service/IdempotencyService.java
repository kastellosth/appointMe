package com.kastellosth.appointme.service;

import com.kastellosth.appointme.exception.BadRequestException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Idempotency keys: "do this at most once, however many times the client sends it".
 *
 * How (see docs/payments.md):
 *   claim()    INSERT the key at the START of the caller's transaction, ON CONFLICT DO NOTHING.
 *              - 1 row inserted: the key is ours, do the work.
 *              - 0 rows: the key exists, so return what it holds (the caller replays it).
 *              If another request with the same key is still running, the INSERT WAITS on the
 *              primary key until that transaction ends, so the two can never both do the work.
 *   complete() store the result in the same transaction, just before commit.
 * If the work fails, the transaction rolls back, the key row with it, and the client can retry.
 *
 * Plain SQL (JdbcTemplate) because ON CONFLICT is the whole point, and JPA cannot express it.
 * It shares the JPA transaction and connection, like InvoiceNumberService.
 */
@Service
public class IdempotencyService {

    /** UUIDs and similar: letters, digits, '-' and '_', 8 to 100 characters. */
    private static final Pattern VALID_KEY = Pattern.compile("[A-Za-z0-9_-]{8,100}");

    public record StoredKey(String operation, String requestHash, Long paymentId) {
    }

    private final JdbcTemplate jdbc;

    public IdempotencyService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public static void requireValidKey(String key) {
        if (key == null || !VALID_KEY.matcher(key).matches()) {
            throw new BadRequestException(
                    "Idempotency-Key must be 8-100 characters: letters, digits, '-' or '_' (a UUID is ideal)");
        }
    }

    /** @return empty if the key was free and is now ours; otherwise what the key already holds. */
    @Transactional(propagation = Propagation.MANDATORY)
    public Optional<StoredKey> claim(long tenantId, String key, String operation, String requestHash) {
        int inserted = jdbc.update("""
                INSERT INTO idempotency_keys (tenant_id, key, operation, request_hash)
                VALUES (?, ?, ?, ?)
                ON CONFLICT (tenant_id, key) DO NOTHING
                """, tenantId, key, operation, requestHash);
        if (inserted == 1) {
            return Optional.empty();
        }
        // READ COMMITTED: this new statement sees the row the other transaction committed.
        return Optional.of(jdbc.queryForObject("""
                        SELECT operation, request_hash, payment_id
                          FROM idempotency_keys
                         WHERE tenant_id = ? AND key = ?
                        """,
                (rs, rowNum) -> new StoredKey(rs.getString(1), rs.getString(2), rs.getObject(3, Long.class)),
                tenantId, key));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void complete(long tenantId, String key, long paymentId) {
        jdbc.update("UPDATE idempotency_keys SET payment_id = ? WHERE tenant_id = ? AND key = ?",
                paymentId, tenantId, key);
    }
}
