-- =====================================================================
-- Day 20: try to break the invoice tables (V5).
-- Run (after reloading the seed):
--   Get-Content .\scripts\day20-invoice-tests.sql | docker exec -i appointme-db psql -U appointme -d appointme
-- Tenant 1: customer 1, staff membership 2.  Tenant 2: customer 3.
-- ERROR lines are the PASSES for the tests marked EXPECT ERROR.
-- Issued invoices can NOT be deleted (that is the point), so clean up afterwards by
-- reloading the seed (TRUNCATE does not fire row triggers).
-- =====================================================================
\set ON_ERROR_STOP off
\pset pager off

\echo
\echo '--- I1  a DRAFT invoice for customer 1 (no number yet)          EXPECT INSERT 0 1'
INSERT INTO invoices (tenant_id, customer_id) VALUES (1, 1);

\echo '--- I2  a line: 2 x 25.00 = 50.00                               EXPECT INSERT 0 1'
INSERT INTO invoice_lines (tenant_id, invoice_id, line_no, description, quantity, unit_price, line_total)
VALUES (1, 1, 1, 'Haircut', 2, 25.00, 50.00);

\echo '--- I3  line_total that is not quantity x price                 EXPECT ERROR ck_invoice_lines_total'
INSERT INTO invoice_lines (tenant_id, invoice_id, line_no, description, quantity, unit_price, line_total)
VALUES (1, 1, 2, 'Wrong maths', 2, 25.00, 49.00);

\echo '--- I4  quantity 0                                              EXPECT ERROR (quantity check)'
INSERT INTO invoice_lines (tenant_id, invoice_id, line_no, description, quantity, unit_price, line_total)
VALUES (1, 1, 2, 'Nothing', 0, 25.00, 0.00);

\echo '--- I5  customer of ANOTHER tenant (customer 3)                 EXPECT ERROR fk_invoices_customer_same_tenant'
INSERT INTO invoices (tenant_id, customer_id) VALUES (1, 3);

\echo '--- I6  total is not subtotal + tax                             EXPECT ERROR ck_invoices_amounts'
INSERT INTO invoices (tenant_id, customer_id, subtotal, tax_total, total) VALUES (1, 1, 10.00, 2.40, 99.00);

\echo '--- I7  lowercase currency                                      EXPECT ERROR ck_invoices_currency'
INSERT INTO invoices (tenant_id, customer_id, currency) VALUES (1, 1, 'eur');

\echo '--- I8  made-up status                                          EXPECT ERROR ck_invoices_status'
INSERT INTO invoices (tenant_id, customer_id, status) VALUES (1, 1, 'WHATEVER');

\echo '--- I9  a line of tenant 2 pointing at the invoice of tenant 1  EXPECT ERROR fk_invoice_lines_invoice_same_tenant'
INSERT INTO invoice_lines (tenant_id, invoice_id, line_no, description, quantity, unit_price, line_total)
VALUES (2, 1, 9, 'Sneaky', 1, 1.00, 1.00);

\echo '--- I10 while DRAFT the totals may change (50.00 + 12.00 tax)   EXPECT UPDATE 1'
UPDATE invoices SET subtotal = 50.00, tax_total = 12.00, total = 62.00 WHERE id = 1;

\echo '--- I11 DRAFT -> PAID directly                                  EXPECT ERROR illegal invoice status change DRAFT -> PAID'
UPDATE invoices SET status = 'PAID', paid_at = now() WHERE id = 1;

\echo '--- I12 DRAFT -> ISSUED without a number                        EXPECT ERROR ck_invoices_issued'
UPDATE invoices SET status = 'ISSUED', issued_at = now() WHERE id = 1;

\echo '--- I13 DRAFT -> ISSUED the proper way                          EXPECT UPDATE 1'
UPDATE invoices SET status = 'ISSUED', number = 'INV-2027-0001', issued_at = now(), due_at = now() + interval '14 days' WHERE id = 1;

\echo '--- I14 change a line of the ISSUED invoice                     EXPECT ERROR ... its lines can no longer change'
UPDATE invoice_lines SET quantity = 3, line_total = 75.00 WHERE invoice_id = 1;

\echo '--- I15 delete a line of the ISSUED invoice                     EXPECT ERROR'
DELETE FROM invoice_lines WHERE invoice_id = 1;

\echo '--- I16 add a line to the ISSUED invoice                        EXPECT ERROR'
INSERT INTO invoice_lines (tenant_id, invoice_id, line_no, description, quantity, unit_price, line_total)
VALUES (1, 1, 2, 'Late extra', 1, 5.00, 5.00);

\echo '--- I17 change the total of the ISSUED invoice                  EXPECT ERROR ... amounts and identity are frozen'
UPDATE invoices SET subtotal = 1.00, tax_total = 0.00, total = 1.00 WHERE id = 1;

\echo '--- I18 delete the ISSUED invoice                               EXPECT ERROR ... cannot be deleted'
DELETE FROM invoices WHERE id = 1;

\echo '--- I19 ISSUED -> DRAFT                                         EXPECT ERROR illegal invoice status change'
UPDATE invoices SET status = 'DRAFT' WHERE id = 1;

\echo '--- I20 ISSUED -> PAID (Day 22: needs payments = total first)  EXPECT INSERT 0 1 then UPDATE 1'
INSERT INTO payments (tenant_id, invoice_id, amount, method, balance_after) VALUES (1, 1, 62.00, 'CASH', 0.00);
UPDATE invoices SET status = 'PAID', paid_at = now() WHERE id = 1;

\echo '--- I21 PAID -> VOID                                            EXPECT ERROR (PAID is final)'
UPDATE invoices SET status = 'VOID' WHERE id = 1;

\echo '--- I22 PAID -> ISSUED                                          EXPECT ERROR'
UPDATE invoices SET status = 'ISSUED', paid_at = NULL WHERE id = 1;

\echo '--- I23 same number again in the SAME tenant                    EXPECT ERROR uq_invoices_tenant_number'
INSERT INTO invoices (tenant_id, customer_id, number, status, issued_at)
VALUES (1, 1, 'INV-2027-0001', 'ISSUED', now());

\echo '--- I24 the same number in ANOTHER tenant                       EXPECT INSERT 0 1'
INSERT INTO invoices (tenant_id, customer_id, number, status, issued_at)
VALUES (2, 3, 'INV-2027-0001', 'ISSUED', now());

\echo '--- I25 two DRAFTs, both without a number                       EXPECT INSERT 0 2 (NULLs never clash)'
INSERT INTO invoices (tenant_id, customer_id) VALUES (1, 1), (1, 1);

\echo '--- I26 DRAFT -> VOID (no number needed)                        EXPECT UPDATE 1'
UPDATE invoices SET status = 'VOID'
 WHERE id = (SELECT max(id) FROM invoices WHERE status = 'DRAFT' AND number IS NULL);

\echo '--- I27 an appointment to invoice                               EXPECT INSERT 0 1'
INSERT INTO appointments (tenant_id, staff_id, customer_id, service_id, starts_at, ends_at, notes)
VALUES (1, 2, 1, 1, '2027-03-01 10:00+00', '2027-03-01 10:30+00', 'DAY20-TEST');

\echo '--- I28 invoice for that appointment                            EXPECT INSERT 0 1'
INSERT INTO invoices (tenant_id, customer_id, appointment_id)
SELECT 1, 1, id FROM appointments WHERE notes = 'DAY20-TEST';

\echo '--- I29 a SECOND live invoice for the same appointment          EXPECT ERROR uq_invoices_one_live_per_appointment'
INSERT INTO invoices (tenant_id, customer_id, appointment_id)
SELECT 1, 1, id FROM appointments WHERE notes = 'DAY20-TEST';

\echo '--- I30 void the first, then a new one is allowed               EXPECT UPDATE 1 then INSERT 0 1'
UPDATE invoices SET status = 'VOID' WHERE appointment_id IS NOT NULL;
INSERT INTO invoices (tenant_id, customer_id, appointment_id)
SELECT 1, 1, id FROM appointments WHERE notes = 'DAY20-TEST';

\echo '--- I31 tenant 2 invoices tenant 1''s appointment               EXPECT ERROR fk_invoices_appointment_same_tenant'
INSERT INTO invoices (tenant_id, customer_id, appointment_id)
SELECT 2, 3, id FROM appointments WHERE notes = 'DAY20-TEST';

\echo
\echo '--- I32 the number counter: three numbers for tenant 1, year 2027   EXPECT 1, 2, 3'
INSERT INTO invoice_counters (tenant_id, year, last_number) VALUES (1, 2027, 1)
    ON CONFLICT (tenant_id, year) DO UPDATE SET last_number = invoice_counters.last_number + 1 RETURNING last_number;
INSERT INTO invoice_counters (tenant_id, year, last_number) VALUES (1, 2027, 1)
    ON CONFLICT (tenant_id, year) DO UPDATE SET last_number = invoice_counters.last_number + 1 RETURNING last_number;
INSERT INTO invoice_counters (tenant_id, year, last_number) VALUES (1, 2027, 1)
    ON CONFLICT (tenant_id, year) DO UPDATE SET last_number = invoice_counters.last_number + 1 RETURNING last_number;

\echo '--- I33 a new year starts again at 1                            EXPECT 1'
INSERT INTO invoice_counters (tenant_id, year, last_number) VALUES (1, 2028, 1)
    ON CONFLICT (tenant_id, year) DO UPDATE SET last_number = invoice_counters.last_number + 1 RETURNING last_number;

\echo '--- I34 another tenant has its own counter                      EXPECT 1'
INSERT INTO invoice_counters (tenant_id, year, last_number) VALUES (2, 2027, 1)
    ON CONFLICT (tenant_id, year) DO UPDATE SET last_number = invoice_counters.last_number + 1 RETURNING last_number;

\echo
\echo '--- final state'
SELECT id, tenant_id, number, status, subtotal, tax_total, total FROM invoices ORDER BY id;
\echo '=== DONE. Reload the seed to clean up. ==='
