-- Day 20: invoices, invoice lines and the per-tenant number counter.

-- 1) Composite-FK targets (same trick as V2): lets other tables prove "same tenant".
ALTER TABLE appointments ADD CONSTRAINT uq_appointments_tenant_id UNIQUE (tenant_id, id);

-- 2) Counter rows: one row per (tenant, year). The number is handed out with a single
--    atomic upsert, which locks the row until the transaction ends, so two invoices
--    issued at the same moment can never get the same number.
CREATE TABLE invoice_counters (
    tenant_id   BIGINT NOT NULL REFERENCES tenants(id),
    year        INT    NOT NULL,
    last_number INT    NOT NULL CHECK (last_number >= 0),
    PRIMARY KEY (tenant_id, year)
);

-- 3) Invoices. The number is NULL while the invoice is a DRAFT and is assigned when it is ISSUED
--    (so abandoned drafts never leave holes in the legal sequence).
CREATE TABLE invoices (
    id             BIGSERIAL PRIMARY KEY,
    tenant_id      BIGINT        NOT NULL REFERENCES tenants(id),
    customer_id    BIGINT        NOT NULL,
    appointment_id BIGINT,
    number         VARCHAR(30),
    status         VARCHAR(20)   NOT NULL DEFAULT 'DRAFT',
    currency       VARCHAR(3)    NOT NULL DEFAULT 'EUR',
    subtotal       NUMERIC(12,2) NOT NULL DEFAULT 0,
    tax_total      NUMERIC(12,2) NOT NULL DEFAULT 0,
    total          NUMERIC(12,2) NOT NULL DEFAULT 0,
    issued_at      TIMESTAMPTZ,
    due_at         TIMESTAMPTZ,
    paid_at        TIMESTAMPTZ,
    created_at     TIMESTAMPTZ   NOT NULL DEFAULT now(),
    version        BIGINT        NOT NULL DEFAULT 0,

    CONSTRAINT uq_invoices_tenant_id     UNIQUE (tenant_id, id),
    CONSTRAINT uq_invoices_tenant_number UNIQUE (tenant_id, number),

    CONSTRAINT fk_invoices_customer_same_tenant
        FOREIGN KEY (tenant_id, customer_id)    REFERENCES customers (tenant_id, id),
    CONSTRAINT fk_invoices_appointment_same_tenant
        FOREIGN KEY (tenant_id, appointment_id) REFERENCES appointments (tenant_id, id),

    CONSTRAINT ck_invoices_status   CHECK (status IN ('DRAFT', 'ISSUED', 'PAID', 'VOID')),
    CONSTRAINT ck_invoices_currency CHECK (currency ~ '^[A-Z]{3}$'),
    CONSTRAINT ck_invoices_amounts  CHECK (subtotal >= 0 AND tax_total >= 0 AND total = subtotal + tax_total),
    -- an issued or paid invoice has a number and an issue date; a paid one has a payment date
    CONSTRAINT ck_invoices_issued   CHECK (status NOT IN ('ISSUED', 'PAID') OR (number IS NOT NULL AND issued_at IS NOT NULL)),
    CONSTRAINT ck_invoices_paid     CHECK ((status = 'PAID') = (paid_at IS NOT NULL))
);

CREATE INDEX idx_invoices_tenant_status ON invoices (tenant_id, status);
CREATE INDEX idx_invoices_customer      ON invoices (tenant_id, customer_id);

-- At most one live (not VOID) invoice per appointment: generating it twice is impossible.
CREATE UNIQUE INDEX uq_invoices_one_live_per_appointment
    ON invoices (tenant_id, appointment_id)
    WHERE appointment_id IS NOT NULL AND status <> 'VOID';

-- 4) Invoice lines. line_total is exact: whole quantity x a 2-decimal price needs no rounding.
--    The (invoice_id, line_no) uniqueness is DEFERRED to commit: when the whole line list is replaced,
--    Hibernate inserts the new rows BEFORE it deletes the old ones, and an immediate check would fail.
CREATE TABLE invoice_lines (
    id          BIGSERIAL PRIMARY KEY,
    tenant_id   BIGINT        NOT NULL,
    invoice_id  BIGINT        NOT NULL,
    line_no     INT           NOT NULL CHECK (line_no >= 1),
    description VARCHAR(255)  NOT NULL,
    quantity    INT           NOT NULL CHECK (quantity > 0),
    unit_price  NUMERIC(10,2) NOT NULL CHECK (unit_price >= 0),
    tax_rate    NUMERIC(5,4)  NOT NULL DEFAULT 0 CHECK (tax_rate >= 0 AND tax_rate <= 1),
    line_total  NUMERIC(12,2) NOT NULL,

    CONSTRAINT fk_invoice_lines_invoice_same_tenant
        FOREIGN KEY (tenant_id, invoice_id) REFERENCES invoices (tenant_id, id),
    CONSTRAINT uq_invoice_lines_line_no UNIQUE (invoice_id, line_no) DEFERRABLE INITIALLY DEFERRED,
    CONSTRAINT ck_invoice_lines_total    CHECK (line_total = quantity * unit_price)
);

-- 5) Last line of defence: financial records are frozen once issued, even if the Java code has a bug.

-- 5a) Lines can only be added, changed or removed while the invoice is a DRAFT.
CREATE FUNCTION invoice_lines_only_while_draft() RETURNS trigger AS $$
DECLARE
    inv_status TEXT;
    inv_id     BIGINT;
BEGIN
    inv_id := COALESCE(NEW.invoice_id, OLD.invoice_id);
    -- FOR SHARE: a concurrent ISSUE (which updates the invoice row) must wait for this transaction.
    SELECT status INTO inv_status FROM invoices WHERE id = inv_id FOR SHARE;
    IF inv_status IS DISTINCT FROM 'DRAFT' THEN
        RAISE EXCEPTION 'invoice % is % - its lines can no longer change', inv_id, inv_status
            USING ERRCODE = '23514';
    END IF;
    RETURN COALESCE(NEW, OLD);
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_invoice_lines_only_while_draft
    BEFORE INSERT OR UPDATE OR DELETE ON invoice_lines
    FOR EACH ROW EXECUTE FUNCTION invoice_lines_only_while_draft();

-- 5b) Legal status moves, and frozen amounts after DRAFT.
CREATE FUNCTION invoices_enforce_rules() RETURNS trigger AS $$
BEGIN
    IF TG_OP = 'DELETE' THEN
        IF OLD.status <> 'DRAFT' THEN
            RAISE EXCEPTION 'invoice % is % and cannot be deleted', OLD.id, OLD.status USING ERRCODE = '23514';
        END IF;
        RETURN OLD;
    END IF;

    IF NEW.status <> OLD.status THEN
        IF NOT ((OLD.status = 'DRAFT'  AND NEW.status IN ('ISSUED', 'VOID')) OR
                (OLD.status = 'ISSUED' AND NEW.status IN ('PAID', 'VOID'))) THEN
            RAISE EXCEPTION 'illegal invoice status change % -> %', OLD.status, NEW.status USING ERRCODE = '23514';
        END IF;
    END IF;

    IF OLD.status <> 'DRAFT' AND (
           NEW.tenant_id   IS DISTINCT FROM OLD.tenant_id
        OR NEW.customer_id IS DISTINCT FROM OLD.customer_id
        OR NEW.appointment_id IS DISTINCT FROM OLD.appointment_id
        OR NEW.number      IS DISTINCT FROM OLD.number
        OR NEW.currency    IS DISTINCT FROM OLD.currency
        OR NEW.subtotal    IS DISTINCT FROM OLD.subtotal
        OR NEW.tax_total   IS DISTINCT FROM OLD.tax_total
        OR NEW.total       IS DISTINCT FROM OLD.total
        OR NEW.issued_at   IS DISTINCT FROM OLD.issued_at
        OR NEW.due_at      IS DISTINCT FROM OLD.due_at) THEN
        RAISE EXCEPTION 'invoice % is % - its amounts and identity are frozen', OLD.id, OLD.status
            USING ERRCODE = '23514';
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_invoices_enforce_rules
    BEFORE UPDATE OR DELETE ON invoices
    FOR EACH ROW EXECUTE FUNCTION invoices_enforce_rules();
