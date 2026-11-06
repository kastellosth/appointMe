package com.kastellosth.appointme.service;

import com.kastellosth.appointme.exception.BadRequestException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.regex.Pattern;

@Service
public class IdempotencyService {

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

    @Transactional
    public int deleteOlderThan(Instant cutoff, int limit) {
        return jdbc.update("""
                WITH picked AS MATERIALIZED (
                    SELECT tenant_id, key FROM idempotency_keys
                     WHERE created_at < ?
                     LIMIT ?
                       FOR UPDATE SKIP LOCKED)
                DELETE FROM idempotency_keys k
                 USING picked
                 WHERE k.tenant_id = picked.tenant_id AND k.key = picked.key
                """, Timestamp.from(cutoff), limit);
    }
}
