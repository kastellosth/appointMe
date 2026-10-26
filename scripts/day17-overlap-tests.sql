-- =====================================================================
-- Day 17: try to break the exclusion constraint.
-- Run (after reloading the seed):
--   Get-Content .\scripts\day17-overlap-tests.sql | docker exec -i appointme-db psql -U appointme -d appointme
-- Bob = membership 2 (tenant 1), Alice = membership 1, Bob@tenant2 = membership 3.
-- Every test row has notes = 'DAY17-TEST' and is deleted at the end.
-- ERROR lines are the PASSES for the tests marked EXPECT ERROR.
-- =====================================================================
\set ON_ERROR_STOP off
\pset pager off

\echo
\echo '--- T1  first booking, Bob 10:00-10:30 (UTC)                  EXPECT INSERT 0 1'
INSERT INTO appointments (tenant_id, staff_id, customer_id, service_id, starts_at, ends_at, notes)
VALUES (1, 2, 1, 1, '2027-02-01 10:00+00', '2027-02-01 10:30+00', 'DAY17-TEST');

\echo '--- T2  partial overlap 10:15-10:45                          EXPECT ERROR exclusion violation'
INSERT INTO appointments (tenant_id, staff_id, customer_id, service_id, starts_at, ends_at, notes)
VALUES (1, 2, 1, 1, '2027-02-01 10:15+00', '2027-02-01 10:45+00', 'DAY17-TEST');

\echo '--- T3  exactly the same time                                EXPECT ERROR'
INSERT INTO appointments (tenant_id, staff_id, customer_id, service_id, starts_at, ends_at, notes)
VALUES (1, 2, 1, 1, '2027-02-01 10:00+00', '2027-02-01 10:30+00', 'DAY17-TEST');

\echo '--- T4  fully inside the first one, 10:10-10:20              EXPECT ERROR'
INSERT INTO appointments (tenant_id, staff_id, customer_id, service_id, starts_at, ends_at, notes)
VALUES (1, 2, 1, 1, '2027-02-01 10:10+00', '2027-02-01 10:20+00', 'DAY17-TEST');

\echo '--- T5  fully around the first one, 09:00-11:00              EXPECT ERROR'
INSERT INTO appointments (tenant_id, staff_id, customer_id, service_id, starts_at, ends_at, notes)
VALUES (1, 2, 1, 1, '2027-02-01 09:00+00', '2027-02-01 11:00+00', 'DAY17-TEST');

\echo '--- T6  back-to-back AFTER, 10:30-11:00                      EXPECT INSERT 0 1'
INSERT INTO appointments (tenant_id, staff_id, customer_id, service_id, starts_at, ends_at, notes)
VALUES (1, 2, 1, 1, '2027-02-01 10:30+00', '2027-02-01 11:00+00', 'DAY17-TEST');

\echo '--- T7  back-to-back BEFORE, 09:30-10:00                     EXPECT INSERT 0 1'
INSERT INTO appointments (tenant_id, staff_id, customer_id, service_id, starts_at, ends_at, notes)
VALUES (1, 2, 1, 1, '2027-02-01 09:30+00', '2027-02-01 10:00+00', 'DAY17-TEST');

\echo '--- T8  DIFFERENT staff (Alice), same time as T1             EXPECT INSERT 0 1'
INSERT INTO appointments (tenant_id, staff_id, customer_id, service_id, starts_at, ends_at, notes)
VALUES (1, 1, 1, 1, '2027-02-01 10:00+00', '2027-02-01 10:30+00', 'DAY17-TEST');

\echo '--- T9  same instant written in another zone (12:00+02)      EXPECT ERROR (it is 10:00 UTC)'
INSERT INTO appointments (tenant_id, staff_id, customer_id, service_id, starts_at, ends_at, notes)
VALUES (1, 2, 1, 1, '2027-02-01 12:00+02', '2027-02-01 12:30+02', 'DAY17-TEST');

\echo '--- T10 a CANCELLED booking at 12:00-12:30                   EXPECT INSERT 0 1'
INSERT INTO appointments (tenant_id, staff_id, customer_id, service_id, starts_at, ends_at, status, notes)
VALUES (1, 2, 1, 1, '2027-02-01 12:00+00', '2027-02-01 12:30+00', 'CANCELLED', 'DAY17-TEST');

\echo '--- T11 a SCHEDULED booking in the cancelled slot            EXPECT INSERT 0 1 (slot reused)'
INSERT INTO appointments (tenant_id, staff_id, customer_id, service_id, starts_at, ends_at, notes)
VALUES (1, 2, 1, 1, '2027-02-01 12:00+00', '2027-02-01 12:30+00', 'DAY17-TEST');

\echo '--- T12 a second CANCELLED booking in the same slot          EXPECT INSERT 0 1 (cancelled rows ignore each other)'
INSERT INTO appointments (tenant_id, staff_id, customer_id, service_id, starts_at, ends_at, status, notes)
VALUES (1, 2, 1, 1, '2027-02-01 12:00+00', '2027-02-01 12:30+00', 'CANCELLED', 'DAY17-TEST');

\echo '--- T13 UPDATE: move the 10:30-11:00 row onto 10:15-10:45    EXPECT ERROR (updates are checked too)'
UPDATE appointments SET starts_at = '2027-02-01 10:15+00', ends_at = '2027-02-01 10:45+00'
WHERE notes = 'DAY17-TEST' AND staff_id = 2 AND starts_at = '2027-02-01 10:30+00';

\echo '--- T14 UPDATE: "un-cancel" a cancelled row into a taken slot EXPECT ERROR'
UPDATE appointments SET status = 'SCHEDULED'
WHERE notes = 'DAY17-TEST' AND staff_id = 2 AND status = 'CANCELLED' AND starts_at = '2027-02-01 12:00+00';

\echo '--- T15 Bob in TENANT 2 (membership 3), same time as T1      EXPECT INSERT 0 1 (a different staff_id)'
INSERT INTO appointments (tenant_id, staff_id, customer_id, service_id, starts_at, ends_at, notes)
VALUES (2, 3, 3, 3, '2027-02-01 10:00+00', '2027-02-01 10:30+00', 'DAY17-TEST');

\echo
\echo '--- Result: Bob (staff 2) must have exactly 6 rows: SCHEDULED at 09:30, 10:00, 10:30 and 12:00, plus CANCELLED at 12:00 twice'
SELECT id, staff_id, to_char(starts_at AT TIME ZONE 'UTC','HH24:MI') AS start_utc,
       to_char(ends_at AT TIME ZONE 'UTC','HH24:MI') AS end_utc, status
FROM appointments WHERE notes = 'DAY17-TEST' AND staff_id = 2 ORDER BY starts_at, status;

\echo '--- Cleanup'
DELETE FROM appointments WHERE notes = 'DAY17-TEST';
SELECT count(*) AS leftover_test_rows FROM appointments WHERE notes = 'DAY17-TEST';
