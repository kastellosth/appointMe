package com.kastellosth.appointme.entity;

import java.util.EnumSet;
import java.util.Set;

public enum InvoiceStatus {
    DRAFT,
    ISSUED,
    PAID,
    VOID;

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

    public boolean isFinal() {
        return allowedNext().isEmpty();
    }

    public boolean isEditable() {
        return this == DRAFT;
    }
}
