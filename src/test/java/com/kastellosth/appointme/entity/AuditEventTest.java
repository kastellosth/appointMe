package com.kastellosth.appointme.entity;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AuditEventTest {

    private static final Instant NOW = Instant.parse("2026-11-05T10:00:00Z");

    @Test
    void entityTypeComesFromTheAction() {
        assertEquals(AuditEntityType.CUSTOMER,
                AuditEvent.of(1L, 1L, AuditAction.CUSTOMER_DELETED, 5L, null, NOW).getEntityType());
        assertEquals(AuditEntityType.INVOICE,
                AuditEvent.of(1L, 1L, AuditAction.PAYMENT_RECORDED, 5L, null, NOW).getEntityType());
    }

    @Test
    void noActorMeansTheSystemDidIt() {
        assertNull(AuditEvent.of(1L, null, AuditAction.INVOICE_PAID, 5L, null, NOW).getActorUserId());
    }

    @Test
    void anEventWithoutTenantActionOrRecordIsImpossible() {
        assertThrows(NullPointerException.class, () -> AuditEvent.of(null, 1L, AuditAction.INVOICE_PAID, 5L, null, NOW));
        assertThrows(NullPointerException.class, () -> AuditEvent.of(1L, 1L, null, 5L, null, NOW));
        assertThrows(NullPointerException.class, () -> AuditEvent.of(1L, 1L, AuditAction.INVOICE_PAID, null, null, NOW));
    }

    @Test
    void namesFitTheColumnsAndTheCheckConstraint() {
        for (AuditAction action : AuditAction.values()) {
            assertEquals(true, action.name().length() <= 50 && action.name().matches("[A-Z][A-Z_]*"), action.name());
        }
        for (AuditEntityType type : AuditEntityType.values()) {
            assertEquals(true, type.name().length() <= 30 && type.name().matches("[A-Z][A-Z_]*"), type.name());
        }
    }
}
