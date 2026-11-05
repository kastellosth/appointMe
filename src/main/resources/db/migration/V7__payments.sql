-- Day 22: payments, and idempotency keys so a retried request never pays twice.

-- 1) Payments. Append-only: a payment is never changed or deleted (a refund would be a new record).
CREATE TABLE payments (
    id            BIGSERIAL PRIMARY KEY,
    tenant_id     BIGINT        NOT NULL,
    invoice_id    BIGINT        NOT NULL,
    amount        NUMERIC(12,2) NOT NULL CONSTRAINT ck_payments_amount CHECK (amount > 0),
    method        VARCHAR(20)   NOT NULL
        CONSTRAINT ck_payments_method CHECK (method IN ('CASH', 'CARD', 'BANK_TRANSFER')),
    reference     VARCHAR(100),
    -- what was still owed AFTER this payment. Stored, like a bank statement's running balance,
    -- so the response of this payment can be rebuilt exactly, any time later.
    balance_after NUMERIC(12,2) NOT NULL CONSTRAINT ck_payments_balance_after CHECK (balance_after >= 0),
    received_at   TIMESTAMPTZ   NOT NULL DEFAULT now(),

    CONSTRAINT uq_payments_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT fk_payments_invoice_same_tenant
        FOREIGN KEY (tenant_id, invoice_id) REFERENCES invoices (tenant_id, id)
);

CREATE INDEX idx_payments_invoice ON payments (tenant_id, invoice_id);

-- 2) Idempotency keys. One row per (business, key). The row is inserted at the START of the
--    request's transaction and committed together with the payment, so:
--      - a second request with the same key WAITS on the primary key until the first finishes,
--        then finds the row and replays the stored result;
--      - if the first request fails, its row is rolled back, and the key can be used again.
CREATE TABLE idempotency_keys (
    tenant_id    BIGINT       NOT NULL REFERENCES tenants (id),
    key          VARCHAR(100) NOT NULL,
    operation    VARCHAR(50)  NOT NULL,          -- what the key was used for, e.g. 'invoice-payment'
    request_hash VARCHAR(64)  NOT NULL,          -- SHA-256 (hex) of the request: same key + other body = error
    payment_id   BIGINT,                         -- the result; NULL only while the first request runs
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),

    PRIMARY KEY (tenant_id, key),
    CONSTRAINT fk_idempotency_payment_same_tenant
        FOREIGN KEY (tenant_id, payment_id) REFERENCES payments (tenant_id, id)
);

-- 3) Last line of defence for payments.

-- 3a) Payments only for ISSUED invoices, never more than the open balance, balance_after must be right.
--     FOR UPDATE: two payments for the same invoice are serialized here, even if the Java lock is ever removed.
CREATE FUNCTION payments_check_invoice() RETURNS trigger AS $$
DECLARE
    inv_status TEXT;
    inv_total  NUMERIC;
    paid       NUMERIC;
BEGIN
    SELECT status, total INTO inv_status, inv_total
      FROM invoices WHERE id = NEW.invoice_id AND tenant_id = NEW.tenant_id
       FOR UPDATE;
    IF NOT FOUND THEN
        -- runs BEFORE the foreign key check, so it gives the "other tenant" message itself
        RAISE EXCEPTION 'invoice % does not exist in tenant %', NEW.invoice_id, NEW.tenant_id
            USING ERRCODE = '23503';
    END IF;
    IF inv_status IS DISTINCT FROM 'ISSUED' THEN
        RAISE EXCEPTION 'invoice % is % - only an ISSUED invoice can receive payments', NEW.invoice_id, inv_status
            USING ERRCODE = '23514';
    END IF;

    SELECT COALESCE(sum(amount), 0) INTO paid FROM payments WHERE invoice_id = NEW.invoice_id;
    IF paid + NEW.amount > inv_total THEN
        RAISE EXCEPTION 'payment of % is more than the open balance % of invoice %',
            NEW.amount, inv_total - paid, NEW.invoice_id USING ERRCODE = '23514';
    END IF;
    IF NEW.balance_after <> inv_total - paid - NEW.amount THEN
        RAISE EXCEPTION 'balance_after % is wrong, expected %', NEW.balance_after, inv_total - paid - NEW.amount
            USING ERRCODE = '23514';
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_payments_check_invoice
    BEFORE INSERT ON payments
    FOR EACH ROW EXECUTE FUNCTION payments_check_invoice();

-- 3b) Append-only.
CREATE FUNCTION payments_append_only() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'payments cannot be changed or deleted (payment %)', OLD.id USING ERRCODE = '23514';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_payments_append_only
    BEFORE UPDATE OR DELETE ON payments
    FOR EACH ROW EXECUTE FUNCTION payments_append_only();

-- 3c) An invoice becomes PAID only when its payments add up to its total,
--     and an invoice that has received money cannot be voided (that needs a refund first).
CREATE FUNCTION invoices_payment_rules() RETURNS trigger AS $$
DECLARE
    paid NUMERIC;
BEGIN
    IF NEW.status = OLD.status OR NEW.status NOT IN ('PAID', 'VOID') THEN
        RETURN NEW;
    END IF;
    SELECT COALESCE(sum(amount), 0) INTO paid FROM payments WHERE invoice_id = NEW.id;
    IF NEW.status = 'PAID' AND paid <> NEW.total THEN
        RAISE EXCEPTION 'invoice % cannot be PAID: payments % <> total %', NEW.id, paid, NEW.total
            USING ERRCODE = '23514';
    END IF;
    IF NEW.status = 'VOID' AND paid > 0 THEN
        RAISE EXCEPTION 'invoice % has payments (%) and cannot be voided', NEW.id, paid
            USING ERRCODE = '23514';
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_invoices_payment_rules
    BEFORE UPDATE ON invoices
    FOR EACH ROW EXECUTE FUNCTION invoices_payment_rules();
