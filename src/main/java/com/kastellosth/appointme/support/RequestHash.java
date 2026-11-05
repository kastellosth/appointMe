package com.kastellosth.appointme.support;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * A fingerprint of a request: SHA-256 of its parts, as 64 hex characters.
 * Used to detect "same Idempotency-Key, DIFFERENT request", which must be refused, not replayed.
 *
 * The parts are joined with the ASCII "unit separator" (0x1F), a character that never appears in
 * normal input, so ("ab", "c") and ("a", "bc") give different hashes.
 */
public final class RequestHash {

    private static final char SEPARATOR = '\u001F';

    private RequestHash() {
    }

    public static String of(String... parts) {
        StringBuilder canonical = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            if (i > 0) {
                canonical.append(SEPARATOR);
            }
            canonical.append(parts[i] == null ? "" : parts[i]);
        }
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(canonical.toString().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is always available in Java", e);   // cannot happen
        }
    }
}
