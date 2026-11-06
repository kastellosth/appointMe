-- For a hosted database, where deploy/init-roles.sh does not run.
-- Run once, as the database owner, BEFORE the application first starts.
-- Replace the password. The role must exist because every migration ends by granting rights to it.
CREATE ROLE appointme_app LOGIN PASSWORD 'REPLACE_WITH_APP_PASSWORD';
GRANT USAGE ON SCHEMA public TO appointme_app;
