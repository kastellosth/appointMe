-- Day 23: the audit log. Who did what, to which record, and when.
-- One row per business change, inserted in the SAME transaction as the change:
-- the change and its audit row commit together, or neither exists.

CREATE TABLE audit_events (
    id            BIGSERIAL    PRIMARY KEY,
    tenant_id     BIGINT       NOT NULL REFERENCES tenants (id),
    -- the user from the token. NULL = the system did it (no logged-in user, e.g. a scheduled job).
    actor_user_id BIGINT       REFERENCES users (id),
    action        VARCHAR(50)  NOT NULL
        CONSTRAINT ck_audit_events_action CHECK (action ~ '^[A-Z][A-Z_]*$'),
    entity_type   VARCHAR(30)  NOT NULL
        CONSTRAINT ck_audit_events_entity_type CHECK (entity_type ~ '^[A-Z][A-Z_]*$'),
    -- deliberately NOT a foreign key: the history of a customer must outlive the customer.
    entity_id     BIGINT       NOT NULL,
    -- a short, factual text. Never personal data: this table cannot be edited or erased.
    details       VARCHAR(500),
    occurred_at   TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- GET /audit-events: the newest events of one business
CREATE INDEX idx_audit_events_tenant_time
    ON audit_events (tenant_id, occurred_at DESC, id DESC);
-- GET /audit-events?entityType=INVOICE&entityId=5: the story of one record
CREATE INDEX idx_audit_events_entity
    ON audit_events (tenant_id, entity_type, entity_id, occurred_at DESC, id DESC);

-- Append-only: history can be added to, never rewritten.
CREATE FUNCTION audit_events_append_only() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'audit events cannot be changed or deleted (event %)', OLD.id USING ERRCODE = '23514';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_audit_events_append_only
    BEFORE UPDATE OR DELETE ON audit_events
    FOR EACH ROW EXECUTE FUNCTION audit_events_append_only();
