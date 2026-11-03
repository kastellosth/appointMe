-- =====================================================================
-- Day 21: try to break V6 (tenant billing, stored line tax, same-customer rule)
-- and compare the two rounding strategies with real numbers.
-- Run (after reloading the seed):
--   Get-Content .\scripts\day21-billing-tests.sql | docker exec -i appointme-db psql -U appointme -d appointme
-- ERROR lines are the PASSES for the tests marked EXPECT ERROR.
-- Seed: appointment 1 = tenant 1, customer 1 (Ann). Appointment 3 = tenant 2.
-- =====================================================================
\set ON_ERROR_STOP off
\pset pager off

\echo
\echo '--- B1  seed billing settings                                   EXPECT 1 | 0.2400 | EUR  and  2 | 0.2000 | GBP'
SELECT id, tax_rate, currency FROM tenants ORDER BY id;

\echo '--- B2  tax rate above 100 %                                    EXPECT ERROR ck_tenants_tax_rate'
UPDATE tenants SET tax_rate = 1.5 WHERE id = 1;

\echo '--- B3  negative tax rate                                       EXPECT ERROR ck_tenants_tax_rate'
UPDATE tenants SET tax_rate = -0.01 WHERE id = 1;

\echo '--- B4  lowercase currency                                      EXPECT ERROR ck_tenants_currency'
UPDATE tenants SET currency = 'eur' WHERE id = 1;

\echo '--- B5  a tenant created the old way gets the defaults          EXPECT 0.0000 | EUR'
INSERT INTO tenants (name, slug, timezone) VALUES ('Defaults Test', 'day21-defaults', 'UTC')
RETURNING tax_rate, currency;
DELETE FROM tenants WHERE slug = 'day21-defaults';

\echo
\echo '--- B6  invoice for appointment 1 but for customer 2 (Ben)      EXPECT ERROR fk_invoices_appointment_same_customer'
INSERT INTO invoices (tenant_id, customer_id, appointment_id) VALUES (1, 2, 1);

\echo '--- B7  invoice for appointment 1 and its own customer (Ann)    EXPECT INSERT 0 1'
INSERT INTO invoices (tenant_id, customer_id, appointment_id) VALUES (1, 1, 1);

\echo '--- B8  invoice without an appointment, any customer            EXPECT INSERT 0 1 (MATCH SIMPLE skips NULLs)'
INSERT INTO invoices (tenant_id, customer_id) VALUES (1, 2);

\echo '--- B9  tenant 1 invoices tenant 2''s appointment 3              EXPECT ERROR (a same-tenant FK)'
INSERT INTO invoices (tenant_id, customer_id, appointment_id) VALUES (1, 1, 3);

\echo
\echo '--- B10 a line with a negative tax amount                       EXPECT ERROR ck_invoice_lines_tax_amount'
INSERT INTO invoice_lines (tenant_id, invoice_id, line_no, description, quantity, unit_price, tax_rate, line_total, tax_amount)
SELECT 1, id, 1, 'Bad tax', 1, 10.00, 0.24, 10.00, -2.40 FROM invoices WHERE appointment_id = 1;

\echo '--- B11 a correct line: 25.00 at 24 % = 6.00 tax                EXPECT INSERT 0 1'
INSERT INTO invoice_lines (tenant_id, invoice_id, line_no, description, quantity, unit_price, tax_rate, line_total, tax_amount)
SELECT 1, id, 1, 'Haircut', 1, 25.00, 0.24, 25.00, 6.00 FROM invoices WHERE appointment_id = 1;

\echo
\echo '--- B12 ROUNDING: the same 3 lines, two strategies.'
\echo '        Lines: 3 x "Gel" at 0.10, 24 %.  Each line: 0.10 x 0.24 = 0.024'
\echo '        EXPECT per_line_tax 0.06 (0.02 + 0.02 + 0.02)   per_invoice_tax 0.07 (round(0.30 x 0.24 = 0.072))'
WITH lines(net, rate) AS (VALUES (0.10, 0.24), (0.10, 0.24), (0.10, 0.24))
SELECT sum(round(net * rate, 2))  AS per_line_tax,
       round(sum(net) * 0.24, 2)  AS per_invoice_tax
FROM lines;

\echo '--- B13 ROUNDING: the strategies agree most of the time.'
\echo '        Haircut 25.00 + Beard Trim 10.00 at 24 %          EXPECT both 8.40'
WITH lines(net, rate) AS (VALUES (25.00, 0.24), (10.00, 0.24))
SELECT sum(round(net * rate, 2)) AS per_line_tax,
       round(sum(net) * 0.24, 2) AS per_invoice_tax
FROM lines;

\echo '--- B14 ROUNDING: half-up at exactly half a cent.  10.30 x 0.24 = 2.472 -> 2.47;  0.625 x 1 -> 0.63'
\echo '        EXPECT 2.47 | 0.63'
SELECT round(10.30 * 0.24, 2) AS a, round(0.625, 2) AS b;

\echo
\echo '--- B15 every stored line tax follows the rule                  EXPECT 0 rows'
SELECT id, line_total, tax_rate, tax_amount FROM invoice_lines
WHERE tax_amount <> round(line_total * tax_rate, 2);

\echo '--- B16 the new list index exists                               EXPECT idx_invoices_tenant_created'
SELECT indexname FROM pg_indexes WHERE indexname = 'idx_invoices_tenant_created';

\echo
\echo '=== DONE. Reload the seed to clean up. ==='
