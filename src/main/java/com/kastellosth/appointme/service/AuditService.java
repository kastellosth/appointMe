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

/**
 * The audit log. record() is called directly by the services that change data.
 *
 * Why not @TransactionalEventListener(AFTER_COMMIT)? It runs after the commit, so a crash at that
 * moment gives a change WITHOUT history, and nothing can roll the change back any more. An audit
 * log with holes cannot be trusted. Here both are one transaction: both or neither.
 */
@Service
public class AuditService {

    /** Newest first; id breaks ties so paging is stable. Matches the indexes in V8. */
    private static final Sort NEWEST_FIRST = Sort.by(Sort.Order.desc("occurredAt"), Sort.Order.desc("id"));

    private final AuditEventRepository events;
    private final CurrentUser currentUser;

    public AuditService(AuditEventRepository events, CurrentUser currentUser) {
        this.events = events;
        this.currentUser = currentUser;
    }

    /**
     * MANDATORY: refuses to run outside the caller's transaction, so an event can never be committed
     * on its own. If this insert fails, the business change is rolled back as well.
     *
     * Details must never hold personal data (names, emails, free text typed by a user):
     * this table cannot be edited or erased, so it could not honour a GDPR erasure request.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void record(Long tenantId, AuditAction action, Long entityId, String details) {
        // plain save, not saveAndFlush: an IDENTITY id makes Hibernate run this INSERT right now,
        // without also flushing the caller's other pending changes earlier than they planned.
        events.save(AuditEvent.of(tenantId, currentUser.userIdOrNull(), action, entityId, details, Instant.now()));
    }

    /** Filters are optional; the tenant is not. Sorting is fixed: newest first. */
    @Transactional(readOnly = true)
    public PageResponse<AuditEventResponse> list(Long tenantId, AuditEntityType entityType, Long entityId,
                                                 AuditAction action, Long actorUserId,
                                                 Instant from, Instant to, int page, int size) {
        if (entityId != null && entityType == null) {
            // ids are only unique per table: "entity 5" alone would mix invoice 5 and customer 5
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
