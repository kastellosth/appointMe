package com.kastellosth.appointme.entity;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

class InvoiceStatusTest {

    @ParameterizedTest(name = "{0} -> {1}: {2}")
    @CsvSource({
            "DRAFT,  DRAFT,  false",
            "DRAFT,  ISSUED, true",
            "DRAFT,  PAID,   false",
            "DRAFT,  VOID,   true",
            "ISSUED, DRAFT,  false",
            "ISSUED, ISSUED, false",
            "ISSUED, PAID,   true",
            "ISSUED, VOID,   true",
            "PAID,   DRAFT,  false",
            "PAID,   ISSUED, false",
            "PAID,   PAID,   false",
            "PAID,   VOID,   false",
            "VOID,   DRAFT,  false",
            "VOID,   ISSUED, false",
            "VOID,   PAID,   false",
            "VOID,   VOID,   false"
    })
    void everyTransitionIsEitherAllowedOrRefused(InvoiceStatus from, InvoiceStatus to, boolean allowed) {
        assertEquals(allowed, from.canTransitionTo(to));
    }
}
