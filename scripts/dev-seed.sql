-- =====================================================================
-- appointMe DEV SEED  (local development only!)
-- Wipes ALL business data, restarts the id counters at 1, and inserts
-- a small known data set so the ids in requests.http always match.
-- flyway_schema_history is NOT touched, so migrations stay applied.
-- =====================================================================

TRUNCATE working_hours, appointments, customers, services, memberships, users, tenants
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
--   Gus   (membership 4): Mon-Fri 08:00-16:00
INSERT INTO working_hours (tenant_id, staff_id, day_of_week, start_time, end_time)
SELECT 1, 1, d, TIME '09:00', TIME '17:00' FROM generate_series(1, 5) AS d
UNION ALL SELECT 1, 2, d, TIME '09:00', TIME '13:00' FROM generate_series(1, 5) AS d
UNION ALL SELECT 1, 2, d, TIME '14:00', TIME '18:00' FROM generate_series(1, 5) AS d
UNION ALL SELECT 2, 4, d, TIME '08:00', TIME '16:00' FROM generate_series(1, 5) AS d;

-- Show what you got
SELECT 'tenants' AS tbl, id, name AS label FROM tenants
UNION ALL SELECT 'services',    id, name      FROM services
UNION ALL SELECT 'customers',   id, full_name FROM customers
UNION ALL SELECT 'memberships', id, 'user ' || user_id || ' / tenant ' || tenant_id || ' / ' || role FROM memberships
ORDER BY 1, 2;