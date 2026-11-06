package com.kastellosth.appointme.entity;

/** PAYMENT_RECORDED belongs to the invoice: an invoice's history must include its payments. */
public enum AuditAction {
    APPOINTMENT_CREATED(AuditEntityType.APPOINTMENT),
    APPOINTMENT_STATUS_CHANGED(AuditEntityType.APPOINTMENT),
    APPOINTMENT_CANCELLED(AuditEntityType.APPOINTMENT),
    APPOINTMENT_RESCHEDULED(AuditEntityType.APPOINTMENT),
    REMINDER_SENT(AuditEntityType.APPOINTMENT),
    REMINDER_FAILED(AuditEntityType.APPOINTMENT),

    INVOICE_CREATED(AuditEntityType.INVOICE),
    INVOICE_LINES_REPLACED(AuditEntityType.INVOICE),
    INVOICE_ISSUED(AuditEntityType.INVOICE),
    INVOICE_VOIDED(AuditEntityType.INVOICE),
    INVOICE_PAID(AuditEntityType.INVOICE),
    PAYMENT_RECORDED(AuditEntityType.INVOICE),

    CUSTOMER_CREATED(AuditEntityType.CUSTOMER),
    CUSTOMER_UPDATED(AuditEntityType.CUSTOMER),
    CUSTOMER_DELETED(AuditEntityType.CUSTOMER),

    STAFF_ADDED(AuditEntityType.STAFF),

    TENANT_BILLING_CHANGED(AuditEntityType.TENANT);

    private final AuditEntityType entityType;

    AuditAction(AuditEntityType entityType) {
        this.entityType = entityType;
    }

    public AuditEntityType entityType() {
        return entityType;
    }
}
