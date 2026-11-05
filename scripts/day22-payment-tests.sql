-- =====================================================================
-- Try to break the payment tables (V7).
-- Run (after reloading the seed):
--   Get-Content .\scripts\day22-payment-tests.sql | docker exec -i appointme-db psql -U appointme -d appointme
-- ERROR lines are the PASSES for the tests marked EXPECT ERROR.
-- Payments and issued invoices cannot be deleted (on purpose): reload the seed afterwards
-- (TRUNCATE does not fire row triggers).
-- =====================================================================
\set ON_ERROR_STOP off
\pset pager off

\echo
\echo '--- setup: invoice A (tenant 1, ISSUED, total 31.00), invoice B (DRAFT), invoice C (tenant 2, ISSUED, 15.00)'
INSERT INTO invoices (tenant_id, customer_id, number, status, subtotal, tax_total, total, issued_at)
VALUES (1, 1, 'TEST-A', 'ISSUED', 25.00, 6.00, 31.00, now()),
       (2, 3, 'TEST-C', 'ISSUED', 15.00, 0.00, 15.00, now());
INSERT INTO invoices (tenant_id, customer_id) VALUES (1, 2);
SELECT id, tenant_id, number, status, total FROM invoices ORDER BY id;
-- ids: 1 = A, 2 = C, 3 = B

\echo
\echo '--- P1  payment for a DRAFT invoice                            EXPECT ERROR only an ISSUED invoice'
INSERT INTO payments (tenant_id, invoice_id, amount, method, balance_after) VALUES (1, 3, 10.00, 'CASH', 0.00);

\echo '--- P2  amount 0                                               EXPECT ERROR ck_payments_amount'
INSERT INTO payments (tenant_id, invoice_id, amount, method, balance_after) VALUES (1, 1, 0.00, 'CASH', 31.00);

\echo '--- P3  unknown method                                         EXPECT ERROR ck_payments_method'
INSERT INTO payments (tenant_id, invoice_id, amount, method, balance_after) VALUES (1, 1, 10.00, 'BITCOIN', 21.00);

\echo '--- P4  tenant 1 pays tenant 2''s invoice C                     EXPECT ERROR invoice 2 does not exist in tenant 1'
INSERT INTO payments (tenant_id, invoice_id, amount, method, balance_after) VALUES (1, 2, 5.00, 'CASH', 10.00);

\echo '--- P5  more than the total (40 > 31)                          EXPECT ERROR more than the open balance'
INSERT INTO payments (tenant_id, invoice_id, amount, method, balance_after) VALUES (1, 1, 40.00, 'CASH', 0.00);

\echo '--- P6  wrong balance_after                                    EXPECT ERROR balance_after ... is wrong'
INSERT INTO payments (tenant_id, invoice_id, amount, method, balance_after) VALUES (1, 1, 10.00, 'CASH', 99.00);

\echo '--- P7  partial payment 10.00 -> balance 21.00                 EXPECT INSERT 0 1'
INSERT INTO payments (tenant_id, invoice_id, amount, method, balance_after) VALUES (1, 1, 10.00, 'CASH', 21.00);

\echo '--- P8  PAID now, with only 10 of 31 paid                      EXPECT ERROR cannot be PAID'
UPDATE invoices SET status = 'PAID', paid_at = now() WHERE id = 1;

\echo '--- P9  VOID an invoice that has received money                EXPECT ERROR has payments ... cannot be voided'
UPDATE invoices SET status = 'VOID' WHERE id = 1;

\echo '--- P10 21.01 is one cent too much                             EXPECT ERROR more than the open balance'
INSERT INTO payments (tenant_id, invoice_id, amount, method, balance_after) VALUES (1, 1, 21.01, 'CARD', 0.00);

\echo '--- P11 the rest, 21.00 -> balance 0.00                        EXPECT INSERT 0 1'
INSERT INTO payments (tenant_id, invoice_id, amount, method, reference, balance_after)
VALUES (1, 1, 21.00, 'CARD', 'POS-7781', 0.00);

\echo '--- P12 now PAID is allowed                                    EXPECT UPDATE 1'
UPDATE invoices SET status = 'PAID', paid_at = now() WHERE id = 1;

\echo '--- P13 another payment for the PAID invoice                   EXPECT ERROR only an ISSUED invoice'
INSERT INTO payments (tenant_id, invoice_id, amount, method, balance_after) VALUES (1, 1, 1.00, 'CASH', 0.00);

\echo '--- P14 change a payment                                       EXPECT ERROR payments cannot be changed'
UPDATE payments SET amount = 5.00 WHERE invoice_id = 1;

\echo '--- P15 delete a payment                                       EXPECT ERROR payments cannot be changed or deleted'
DELETE FROM payments WHERE invoice_id = 1;

\echo
\echo '--- K1  an idempotency key for payment 1                       EXPECT INSERT 0 1'
INSERT INTO idempotency_keys (tenant_id, key, operation, request_hash, payment_id)
SELECT 1, 'key-0001-abcd', 'invoice-payment', repeat('a', 64), min(id) FROM payments;

\echo '--- K2  the same key again in the same tenant: the claim used by the app'
\echo '        EXPECT INSERT 0 0 (DO NOTHING: the app then reads the existing row and replays it)'
INSERT INTO idempotency_keys (tenant_id, key, operation, request_hash)
VALUES (1, 'key-0001-abcd', 'invoice-payment', repeat('b', 64))
ON CONFLICT (tenant_id, key) DO NOTHING;

\echo '--- K3  the same key string in ANOTHER tenant                  EXPECT INSERT 0 1 (keys are per business)'
INSERT INTO idempotency_keys (tenant_id, key, operation, request_hash)
VALUES (2, 'key-0001-abcd', 'invoice-payment', repeat('c', 64));

\echo '--- K4  tenant 2''s key pointing at tenant 1''s payment          EXPECT ERROR fk_idempotency_payment_same_tenant'
UPDATE idempotency_keys SET payment_id = (SELECT min(id) FROM payments) WHERE tenant_id = 2;

\echo
\echo '--- final state: two payments 10.00 + 21.00, balance 21.00 then 0.00, invoice 1 PAID'
SELECT id, invoice_id, amount, method, reference, balance_after FROM payments ORDER BY id;
SELECT id, number, status, total FROM invoices WHERE id = 1;
\echo '=== DONE. Reload the seed to clean up. ==='
