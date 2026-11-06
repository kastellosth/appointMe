package com.kastellosth.appointme.support;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class RequestHashTest {

    @Test
    void sameInputSameHash() {
        assertEquals(RequestHash.of("7", "10", "CASH", null), RequestHash.of("7", "10", "CASH", null));
    }

    @Test
    void anyDifferenceChangesTheHash() {
        String base = RequestHash.of("7", "10", "CASH", null);
        assertNotEquals(base, RequestHash.of("8", "10", "CASH", null));
        assertNotEquals(base, RequestHash.of("7", "11", "CASH", null));
        assertNotEquals(base, RequestHash.of("7", "10", "CARD", null));
        assertNotEquals(base, RequestHash.of("7", "10", "CASH", "POS-1"));
    }

    @Test
    void partsCannotBeShiftedIntoEachOther() {
        assertNotEquals(RequestHash.of("ab", "c"), RequestHash.of("a", "bc"));
    }

    @Test
    void isSha256Hex() {
        String hash = RequestHash.of("x");
        assertEquals(64, hash.length());
        // SHA-256("x"), a known value
        assertEquals("2d711642b726b04401627ca9fbac32f5c8530fb1903cc4db02258717921a4881", hash);
    }
}
