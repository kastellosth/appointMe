package com.kastellosth.appointme.entity;

import java.util.EnumSet;
import java.util.Set;

/**
 * The life of an invoice. The legal moves are written down in ONE place (this enum),
 * so every other class asks it instead of repeating the rules.
 *
 *   DRAFT  --> ISSUED --> PAID
 *     |           |
 *     +--> VOID <-+          (PAID and VOID are final)
 */
public enum InvoiceStatus {
    DRAFT,
    ISSUED,
    PAID,
    VOID;

    /** The statuses this one may move to. */
    public Set<InvoiceStatus> allowedNext() {
        return switch (this) {
            case DRAFT  -> EnumSet.of(ISSUED, VOID);
            case ISSUED -> EnumSet.of(PAID, VOID);
            case PAID, VOID -> EnumSet.noneOf(InvoiceStatus.class);
        };
    }

    public boolean canTransitionTo(InvoiceStatus target) {
        return allowedNext().contains(target);
    }

    /** No further moves are possible. */
    public boolean isFinal() {
        return allowedNext().isEmpty();
    }

    /** Only a DRAFT may have its lines and amounts changed. */
    public boolean isEditable() {
        return this == DRAFT;
    }
}
