package com.kastellosth.appointme.service;

import com.kastellosth.appointme.dto.AuditEventResponse;
import com.kastellosth.appointme.dto.PageResponse;
import com.kastellosth.appointme.entity.AuditAction;
import com.kastellosth.appointme.entity.AuditEntityType;
import com.kastellosth.appointme.entity.AuditEvent;
import com.kastellosth.appointme.exception.BadRequestException;
import com.kastellosth.appointme.repository.AuditEventRepository;
import com.kastellosth.appointme.repository.AuditFilters;
import com.kastellosth.appointme.security.CurrentUser;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/** Written in the caller's transaction, not AFTER_COMMIT: a crash there would leave a change with no history. */
@Service
public class AuditService {

    private static final Sort NEWEST_FIRST = Sort.by(Sort.Order.desc("occurredAt"), Sort.Order.desc("id"));

    private final AuditEventRepository events;
    private final CurrentUser currentUser;

    public AuditService(AuditEventRepository events, CurrentUser currentUser) {
        this.events = events;
        this.currentUser = currentUser;
    }

    /** Details must never hold personal data: this table cannot be edited or erased. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void record(Long tenantId, AuditAction action, Long entityId, String details) {
        // save, not saveAndFlush: the IDENTITY id runs this INSERT now, without flushing the caller's changes
        events.save(AuditEvent.of(tenantId, currentUser.userIdOrNull(), action, entityId, details, Instant.now()));
    }

    @Transactional(readOnly = true)
    public PageResponse<AuditEventResponse> list(Long tenantId, AuditEntityType entityType, Long entityId,
                                                 AuditAction action, Long actorUserId,
                                                 Instant from, Instant to, int page, int size) {
        if (entityId != null && entityType == null) {
            throw new BadRequestException("'entityId' needs 'entityType'");
        }
        if (from != null && to != null && !from.isBefore(to)) {
            throw new BadRequestException("'from' must be before 'to'");
        }
        var spec = AuditFilters.of(tenantId, entityType, entityId, action, actorUserId, from, to);
        var result = events.findAll(spec, PageRequest.of(page, size, NEWEST_FIRST));
        return PageResponse.from(result.map(AuditEventResponse::from));
    }
}
