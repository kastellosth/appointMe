-- Runs after every migrate, so new tables are covered without changing any migration.
-- The dev database has no appointme_app role, which is why this is in a production-only location.
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO appointme_app;
GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA public TO appointme_app;

REVOKE ALL ON flyway_schema_history FROM appointme_app;
-- Financial and audit history is append-only: the triggers refuse changes, and this role has no right to try.
REVOKE UPDATE, DELETE ON payments, audit_events FROM appointme_app;
