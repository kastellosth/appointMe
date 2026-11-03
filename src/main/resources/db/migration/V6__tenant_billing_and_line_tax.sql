-- Day 21: billing settings per business, stored tax per invoice line, and two safety nets.

-- 1) Each business has ONE tax rate and ONE currency. New invoices copy them.
--    A fraction, like invoice_lines.tax_rate: 0.2400 means 24 %.
ALTER TABLE tenants
    ADD COLUMN tax_rate NUMERIC(5,4) NOT NULL DEFAULT 0
        CONSTRAINT ck_tenants_tax_rate CHECK (tax_rate >= 0 AND tax_rate <= 1),
    ADD COLUMN currency VARCHAR(3)   NOT NULL DEFAULT 'EUR'
        CONSTRAINT ck_tenants_currency CHECK (currency ~ '^[A-Z]{3}$');

-- 2) Store the tax of each line instead of recalculating it every time it is read.
--    Rule: what was printed on an issued invoice must never change, even if the rounding code changes.
ALTER TABLE invoice_lines
    ADD COLUMN tax_amount NUMERIC(12,2) NOT NULL DEFAULT 0
        CONSTRAINT ck_invoice_lines_tax_amount CHECK (tax_amount >= 0);

--    Fill it for lines that already exist, with the same rule the Java code used on Day 20
--    (per line, half-up to cents; PostgreSQL round() on NUMERIC rounds half away from zero,
--    which is the same as HALF_UP for amounts >= 0).
--    The Day 20 trigger blocks ANY change to lines of issued invoices, so it is switched off
--    for this one statement only. Flyway runs the migration in a transaction: if anything fails,
--    the trigger is back on after the rollback.
ALTER TABLE invoice_lines DISABLE TRIGGER trg_invoice_lines_only_while_draft;
UPDATE invoice_lines SET tax_amount = round(line_total * tax_rate, 2);
ALTER TABLE invoice_lines ENABLE TRIGGER trg_invoice_lines_only_while_draft;

-- 3) An invoice for an appointment must be for THAT appointment's customer.
--    (Java checks it too. MATCH SIMPLE: invoices without an appointment are not checked.)
ALTER TABLE appointments
    ADD CONSTRAINT uq_appointments_tenant_id_customer UNIQUE (tenant_id, id, customer_id);
ALTER TABLE invoices
    ADD CONSTRAINT fk_invoices_appointment_same_customer
        FOREIGN KEY (tenant_id, appointment_id, customer_id)
        REFERENCES appointments (tenant_id, id, customer_id);

-- 4) GET /invoices lists newest first per tenant.
CREATE INDEX idx_invoices_tenant_created ON invoices (tenant_id, created_at DESC, id DESC);
