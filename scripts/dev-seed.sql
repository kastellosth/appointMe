-- =====================================================================
-- appointMe DEV SEED  (local development only!)
-- Wipes ALL business data, restarts the id counters at 1, and inserts
-- a small known data set so the ids in requests.http always match.
-- flyway_schema_history is NOT touched, so migrations stay applied.
-- =====================================================================

TRUNCATE appointments, customers, services, memberships, users, tenants
    RESTART IDENTITY CASCADE;

-- Tenants: id 1 and id 2
INSERT INTO tenants (name, slug, timezone) VALUES
  ('Test Barber', 'test-barber', 'Europe/Athens'),   -- tenant 1
  ('Second Gym',  'second-gym',  'Europe/London');   -- tenant 2

-- Services: ids 1, 2 (tenant 1) and 3 (tenant 2)
INSERT INTO services (tenant_id, name, duration_minutes, price) VALUES
  (1, 'Haircut',    30, 25.00),   -- service 1
  (1, 'Beard Trim', 15, 10.00),   -- service 2
  (2, 'Yoga Class', 60, 15.00);   -- service 3

-- Customers: ids 1, 2 (tenant 1) and 3 (tenant 2)
INSERT INTO customers (tenant_id, full_name, email, phone) VALUES
  (1, 'Ann Smith', 'ann@example.com', '6900000001'),   -- customer 1
  (1, 'Ben Jones', 'ben@example.com', '6900000002'),   -- customer 2
  (2, 'Zoe Gym',   'zoe@example.com', '6900000003');   -- customer 3

-- Users: id 1 = Alice, id 2 = Bob (placeholder hashes, NOT real logins)
INSERT INTO users (email, password_hash, full_name) VALUES
  ('alice@test.com', 'TEMP-NOT-A-REAL-HASH', 'Alice Owner'),
  ('bob@test.com',   'TEMP-NOT-A-REAL-HASH', 'Bob Barber');

-- Memberships (these ids are the "staffId" used when booking):
--   membership 1 = Alice, OWNER in tenant 1
--   membership 2 = Bob,   STAFF in tenant 1
--   membership 3 = Bob,   STAFF in tenant 2   (for cross-tenant attack tests)
INSERT INTO memberships (user_id, tenant_id, role) VALUES
  (1, 1, 'OWNER'),
  (2, 1, 'STAFF'),
  (2, 2, 'STAFF');

-- Show what you got
SELECT 'tenants' AS tbl, id, name AS label FROM tenants
UNION ALL SELECT 'services',    id, name      FROM services
UNION ALL SELECT 'customers',   id, full_name FROM customers
UNION ALL SELECT 'memberships', id, 'user ' || user_id || ' / tenant ' || tenant_id || ' / ' || role FROM memberships
ORDER BY 1, 2;
