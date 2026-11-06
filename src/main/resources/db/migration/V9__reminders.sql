CREATE TABLE reminders (
    id                    BIGSERIAL   PRIMARY KEY,
    tenant_id             BIGINT      NOT NULL,
    appointment_id        BIGINT      NOT NULL,
    -- part of the unique key, so a rescheduled appointment gets a new reminder
    appointment_starts_at TIMESTAMPTZ NOT NULL,
    status                VARCHAR(20) NOT NULL DEFAULT 'PENDING'
        CONSTRAINT ck_reminders_status CHECK (status IN ('PENDING', 'SENDING', 'SENT', 'FAILED', 'SKIPPED')),
    attempts              INTEGER     NOT NULL DEFAULT 0 CONSTRAINT ck_reminders_attempts CHECK (attempts >= 0),
    next_attempt_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    claimed_at            TIMESTAMPTZ,
    sent_at               TIMESTAMPTZ,
    reason                VARCHAR(200),
    created_at            TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT uq_reminders_appointment_start UNIQUE (appointment_id, appointment_starts_at),
    CONSTRAINT fk_reminders_appointment_same_tenant
        FOREIGN KEY (tenant_id, appointment_id) REFERENCES appointments (tenant_id, id),
    CONSTRAINT ck_reminders_sent_at CHECK ((status = 'SENT') = (sent_at IS NOT NULL)),
    CONSTRAINT ck_reminders_claimed_at CHECK ((status = 'SENDING') = (claimed_at IS NOT NULL))
);

CREATE INDEX idx_reminders_pending ON reminders (next_attempt_at) WHERE status = 'PENDING';
CREATE INDEX idx_reminders_sending ON reminders (claimed_at) WHERE status = 'SENDING';
CREATE INDEX idx_reminders_tenant_created ON reminders (tenant_id, created_at DESC, id DESC);

CREATE INDEX idx_appointments_scheduled_starts ON appointments (starts_at) WHERE status = 'SCHEDULED';
CREATE INDEX idx_idempotency_keys_created ON idempotency_keys (created_at);
