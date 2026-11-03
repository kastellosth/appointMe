-- =====================================================================
-- appointMe DEV SEED  (local development only!)
-- Wipes ALL business data, restarts the id counters at 1, and inserts
-- a small known data set so the ids in requests.http always match.
-- flyway_schema_history is NOT touched, so migrations stay applied.
-- =====================================================================

TRUNCATE invoice_lines, invoices, invoice_counters, working_hours, appointments, customers, services, memberships, users, tenants
    RESTART IDENTITY CASCADE;

-- Tenants: id 1 and id 2   (Day 21: tax rate and currency per business)
INSERT INTO tenants (name, slug, timezone, tax_rate, currency) VALUES
  ('Test Barber', 'test-barber', 'Europe/Athens', 0.2400, 'EUR'),   -- tenant 1: 24 %
  ('Second Gym',  'second-gym',  'Europe/London', 0.2000, 'GBP');   -- tenant 2: 20 %

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

-- Users: id 1 = Alice, id 2 = Bob, id 3 = Gus. All log in with the password:  correct-horse-1
-- (dev-only BCrypt hash of that test password; never use real passwords here)
INSERT INTO users (email, password_hash, full_name) VALUES
  ('alice@test.com', '$2a$10$ltP1XKnRglbwTbLn5wTa5eOfLhdQYlDInljmUGRJ3T8MDJU9Lpj6G', 'Alice Owner'),
  ('bob@test.com',   '$2a$10$ltP1XKnRglbwTbLn5wTa5eOfLhdQYlDInljmUGRJ3T8MDJU9Lpj6G', 'Bob Barber'),
  ('gus@test.com',   '$2a$10$ltP1XKnRglbwTbLn5wTa5eOfLhdQYlDInljmUGRJ3T8MDJU9Lpj6G', 'Gus Gym-Owner');

-- Memberships (these ids are the "staffId" used when booking):
--   membership 1 = Alice, OWNER in tenant 1
--   membership 2 = Bob,   STAFF in tenant 1
--   membership 3 = Bob,   STAFF in tenant 2   (for cross-tenant attack tests)
--   membership 4 = Gus,   OWNER in tenant 2   (the victim business in Day 14 attacks)
INSERT INTO memberships (user_id, tenant_id, role) VALUES
  (1, 1, 'OWNER'),
  (2, 1, 'STAFF'),
  (2, 2, 'STAFF'),
  (3, 2, 'OWNER');


-- Working hours (1 = Monday ... 7 = Sunday). Wall-clock times in the tenant's zone.
--   Alice (membership 1): Mon-Fri 09:00-17:00
--   Bob   (membership 2): Mon-Fri 09:00-13:00 and 14:00-18:00 (lunch break)
--   Bob   (membership 3, tenant 2) and Gus (membership 4): Mon-Fri 08:00-16:00 (London time)
INSERT INTO working_hours (tenant_id, staff_id, day_of_week, start_time, end_time)
SELECT 1, 1, d, TIME '09:00', TIME '17:00' FROM generate_series(1, 5) AS d
UNION ALL SELECT 1, 2, d, TIME '09:00', TIME '13:00' FROM generate_series(1, 5) AS d
UNION ALL SELECT 1, 2, d, TIME '14:00', TIME '18:00' FROM generate_series(1, 5) AS d
UNION ALL SELECT 2, 3, d, TIME '08:00', TIME '16:00' FROM generate_series(1, 5) AS d
UNION ALL SELECT 2, 4, d, TIME '08:00', TIME '16:00' FROM generate_series(1, 5) AS d;

-- Day 21: appointments for the invoice tests. Inserted directly (the API refuses past times).
-- Times are always relative to TODAY, so the seed keeps working on any day.
--   appointment 1: tenant 1, Alice, Ann, Haircut    YESTERDAY 10:00 Athens  -> can be completed
--   appointment 2: tenant 1, Bob,   Ben, Beard Trim YESTERDAY 11:00 Athens  -> can be completed
--   appointment 3: tenant 2, Gus,   Zoe, Yoga Class YESTERDAY 10:00 London  -> for cross-tenant tests
--   appointment 4: tenant 1, Alice, Ben, Haircut    IN 2 DAYS 07:00 Athens  -> future: completing it is refused
--                  (07:00 is outside working hours, so it never blocks a slot your other .http files book)
INSERT INTO appointments (tenant_id, staff_id, customer_id, service_id, starts_at, ends_at) VALUES
  (1, 1, 1, 1, ((current_date - 1) + TIME '10:00') AT TIME ZONE 'Europe/Athens',
               ((current_date - 1) + TIME '10:30') AT TIME ZONE 'Europe/Athens'),
  (1, 2, 2, 2, ((current_date - 1) + TIME '11:00') AT TIME ZONE 'Europe/Athens',
               ((current_date - 1) + TIME '11:15') AT TIME ZONE 'Europe/Athens'),
  (2, 4, 3, 3, ((current_date - 1) + TIME '10:00') AT TIME ZONE 'Europe/London',
               ((current_date - 1) + TIME '11:00') AT TIME ZONE 'Europe/London'),
  (1, 1, 2, 1, ((current_date + 2) + TIME '07:00') AT TIME ZONE 'Europe/Athens',
               ((current_date + 2) + TIME '07:30') AT TIME ZONE 'Europe/Athens');

-- Show what you got
SELECT 'tenants' AS tbl, id, name AS label FROM tenants
UNION ALL SELECT 'services',    id, name      FROM services
UNION ALL SELECT 'customers',   id, full_name FROM customers
UNION ALL SELECT 'memberships', id, 'user ' || user_id || ' / tenant ' || tenant_id || ' / ' || role FROM memberships
UNION ALL SELECT 'appointments', id, 'tenant ' || tenant_id || ' / ' || to_char(starts_at AT TIME ZONE 'UTC', 'YYYY-MM-DD HH24:MI') || ' UTC / ' || status FROM appointments
ORDER BY 1, 2;
