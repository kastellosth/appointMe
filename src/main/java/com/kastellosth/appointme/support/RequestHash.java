package com.kastellosth.appointme.support;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** Parts are joined with 0x1F, which never appears in normal input, so ("ab", "c") and ("a", "bc") differ. */
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
            throw new IllegalStateException("SHA-256 is always available in Java", e);
        }
    }
}
