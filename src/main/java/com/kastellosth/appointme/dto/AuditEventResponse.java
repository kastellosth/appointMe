package com.kastellosth.appointme.dto;

import com.kastellosth.appointme.entity.AuditAction;
import com.kastellosth.appointme.entity.AuditEntityType;
import com.kastellosth.appointme.entity.AuditEvent;

import java.time.Instant;

public record AuditEventResponse(
        Long id,
        AuditAction action,
        AuditEntityType entityType,
        Long entityId,
        Long actorUserId,
        String details,
        Instant occurredAt
) {
    public static AuditEventResponse from(AuditEvent e) {
        return new AuditEventResponse(e.getId(), e.getAction(), e.getEntityType(), e.getEntityId(),
                e.getActorUserId(), e.getDetails(), e.getOccurredAt());
    }
}
