package com.kastellosth.appointme.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;

import java.time.Instant;
import java.util.Objects;

/**
 * One line of history. Append-only: no setters, @Immutable for Hibernate, and the database refuses
 * UPDATE and DELETE (trg_audit_events_append_only).
 *
 * entityId is a plain number, not a relation: the event must survive when the record it talks
 * about is deleted, for example a customer.
 */
@Entity
@Immutable
@Table(name = "audit_events")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AuditEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private Long tenantId;

    /** null = the system did it, not a logged-in user. */
    @Column(name = "actor_user_id", updatable = false)
    private Long actorUserId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 50)
    private AuditAction action;

    @Enumerated(EnumType.STRING)
    @Column(name = "entity_type", nullable = false, updatable = false, length = 30)
    private AuditEntityType entityType;

    @Column(name = "entity_id", nullable = false, updatable = false)
    private Long entityId;

    @Column(length = 500, updatable = false)
    private String details;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    public static AuditEvent of(Long tenantId, Long actorUserId, AuditAction action, Long entityId,
                                String details, Instant occurredAt) {
        AuditEvent e = new AuditEvent();
        e.tenantId = Objects.requireNonNull(tenantId, "tenantId");
        e.actorUserId = actorUserId;
        e.action = Objects.requireNonNull(action, "action");
        e.entityType = action.entityType();   // comes from the action, so the two cannot disagree
        e.entityId = Objects.requireNonNull(entityId, "entityId");
        e.details = details;
        e.occurredAt = Objects.requireNonNull(occurredAt, "occurredAt");
        return e;
    }
}
