-- =====================================================================
-- Try to break the audit log (V8).
-- Run (needs the seed's tenants 1 and users 1):
--   cat scripts/day23-audit-tests.sql | docker exec -i appointme-db psql -U appointme -d appointme
-- ERROR lines are the PASSES for the tests marked EXPECT ERROR.
-- Everything runs in one transaction that is rolled back at the end, so this script leaves
-- nothing behind and no seed reload is needed. ON_ERROR_ROLLBACK keeps the transaction alive
-- after each expected error.
-- =====================================================================
\set ON_ERROR_STOP off
\set ON_ERROR_ROLLBACK on
\pset pager off

BEGIN;

\echo
\echo '--- A1  a normal event, with a user                             EXPECT INSERT 0 1'
INSERT INTO audit_events (tenant_id, actor_user_id, action, entity_type, entity_id, details)
VALUES (1, 1, 'INVOICE_ISSUED', 'INVOICE', 5, 'number INV-2026-0001');

\echo '--- A2  a system event: no user                                EXPECT INSERT 0 1'
INSERT INTO audit_events (tenant_id, action, entity_type, entity_id)
VALUES (1, 'INVOICE_PAID', 'INVOICE', 5);

\echo '--- A3  the record may not exist (any more)                    EXPECT INSERT 0 1'
INSERT INTO audit_events (tenant_id, action, entity_type, entity_id)
VALUES (1, 'CUSTOMER_DELETED', 'CUSTOMER', 999999);

\echo '--- A4  unknown tenant                                         EXPECT ERROR audit_events_tenant_id_fkey'
INSERT INTO audit_events (tenant_id, action, entity_type, entity_id) VALUES (999999, 'INVOICE_PAID', 'INVOICE', 5);

\echo '--- A5  unknown user                                           EXPECT ERROR audit_events_actor_user_id_fkey'
INSERT INTO audit_events (tenant_id, actor_user_id, action, entity_type, entity_id) VALUES (1, 999999, 'INVOICE_PAID', 'INVOICE', 5);

\echo '--- A6  no tenant                                              EXPECT ERROR null value in column "tenant_id"'
INSERT INTO audit_events (action, entity_type, entity_id) VALUES ('INVOICE_PAID', 'INVOICE', 5);

\echo '--- A7  no record                                              EXPECT ERROR null value in column "entity_id"'
INSERT INTO audit_events (tenant_id, action, entity_type) VALUES (1, 'INVOICE_PAID', 'INVOICE');

\echo '--- A8  an action that is not a constant name                  EXPECT ERROR ck_audit_events_action'
INSERT INTO audit_events (tenant_id, action, entity_type, entity_id) VALUES (1, 'oops, lowercase', 'INVOICE', 5);

\echo '--- A9  a bad entity type                                      EXPECT ERROR ck_audit_events_entity_type'
INSERT INTO audit_events (tenant_id, action, entity_type, entity_id) VALUES (1, 'INVOICE_PAID', 'invoice', 5);

\echo '--- A10 details too long (501)                                 EXPECT ERROR value too long'
INSERT INTO audit_events (tenant_id, action, entity_type, entity_id, details) VALUES (1, 'INVOICE_PAID', 'INVOICE', 5, repeat('x', 501));

\echo '--- A11 rewrite history                                        EXPECT ERROR cannot be changed or deleted'
UPDATE audit_events SET details = 'nothing happened' WHERE tenant_id = 1;

\echo '--- A12 erase history                                          EXPECT ERROR cannot be changed or deleted'
DELETE FROM audit_events WHERE tenant_id = 1;

\echo '--- A13 only the three good events are there                   EXPECT new_events = 3'
SELECT count(*) AS new_events FROM audit_events WHERE entity_id IN (5, 999999) AND tenant_id = 1 AND occurred_at = now();

\echo '--- A14 the list query can use the index                       EXPECT idx_audit_events_tenant_time in the plan'
SET LOCAL enable_seqscan = off;
EXPLAIN (COSTS OFF) SELECT * FROM audit_events WHERE tenant_id = 1 ORDER BY occurred_at DESC, id DESC LIMIT 20;

ROLLBACK;
\echo
\echo 'Done. Everything above was rolled back.'
