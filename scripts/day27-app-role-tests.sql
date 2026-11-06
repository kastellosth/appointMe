-- Run as appointme_app in the production stack. Each EXPECT ERROR line must be refused.
\echo '--- 1 EXPECT ERROR: drop a table'
DROP TABLE audit_events;
\echo '--- 2 EXPECT ERROR: switch off a trigger'
ALTER TABLE payments DISABLE TRIGGER ALL;
\echo '--- 3 EXPECT ERROR: bypass all triggers for this session'
SET session_replication_role = replica;
\echo '--- 4 EXPECT ERROR: empty the audit log'
TRUNCATE audit_events;
\echo '--- 5 EXPECT ERROR: create a table'
CREATE TABLE attacker_table (id INT);
\echo '--- 6 EXPECT ERROR: delete the migration history'
DELETE FROM flyway_schema_history;
\echo '--- 7 EXPECT ERROR: read the migration history'
SELECT count(*) FROM flyway_schema_history;
\echo '--- 8 EXPECT ERROR: change the audit log'
UPDATE audit_events SET details = 'changed';
\echo '--- 9 EXPECT ERROR: delete from the audit log'
DELETE FROM audit_events;
\echo '--- 10 EXPECT ERROR: change a payment'
UPDATE payments SET amount = 1;
\echo '--- 11 EXPECT ERROR: delete a payment'
DELETE FROM payments;
\echo '--- 12 EXPECT OK: normal reading works'
SELECT count(*) AS tenants FROM tenants;
