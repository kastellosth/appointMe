-- =====================================================================
-- Try to break the reminders table (V9). Needs the seed's appointments 1 and 3.
--   cat scripts/day24-reminder-tests.sql | docker exec -i appointme-db psql -U appointme -d appointme
-- ERROR lines are the PASSES for the tests marked EXPECT ERROR.
-- Rolled back at the end, so no seed reload is needed.
-- =====================================================================
\set ON_ERROR_STOP off
\set ON_ERROR_ROLLBACK on
\pset pager off

BEGIN;

\echo
\echo '--- R1  a normal reminder                                       EXPECT INSERT 0 1'
INSERT INTO reminders (tenant_id, appointment_id, appointment_starts_at) VALUES (1, 1, '2030-01-01 10:00+00');

\echo '--- R2  the same appointment and time again                    EXPECT ERROR uq_reminders_appointment_start'
INSERT INTO reminders (tenant_id, appointment_id, appointment_starts_at) VALUES (1, 1, '2030-01-01 10:00+00');

\echo '--- R3  the same appointment at a new time (rescheduled)       EXPECT INSERT 0 1'
INSERT INTO reminders (tenant_id, appointment_id, appointment_starts_at) VALUES (1, 1, '2030-01-02 10:00+00');

\echo '--- R4  tenant 1 points at tenant 2''s appointment 3            EXPECT ERROR fk_reminders_appointment_same_tenant'
INSERT INTO reminders (tenant_id, appointment_id, appointment_starts_at) VALUES (1, 3, '2030-01-01 10:00+00');

\echo '--- R5  an appointment that does not exist                     EXPECT ERROR fk_reminders_appointment_same_tenant'
INSERT INTO reminders (tenant_id, appointment_id, appointment_starts_at) VALUES (1, 999999, '2030-01-01 10:00+00');

\echo '--- R6  an unknown status                                      EXPECT ERROR ck_reminders_status'
INSERT INTO reminders (tenant_id, appointment_id, appointment_starts_at, status) VALUES (1, 1, '2030-01-03 10:00+00', 'MAYBE');

\echo '--- R7  negative attempts                                      EXPECT ERROR ck_reminders_attempts'
INSERT INTO reminders (tenant_id, appointment_id, appointment_starts_at, attempts) VALUES (1, 1, '2030-01-03 10:00+00', -1);

\echo '--- R8  SENT without sent_at                                   EXPECT ERROR ck_reminders_sent_at'
INSERT INTO reminders (tenant_id, appointment_id, appointment_starts_at, status) VALUES (1, 1, '2030-01-03 10:00+00', 'SENT');

\echo '--- R9  sent_at on a PENDING reminder                          EXPECT ERROR ck_reminders_sent_at'
INSERT INTO reminders (tenant_id, appointment_id, appointment_starts_at, sent_at) VALUES (1, 1, '2030-01-03 10:00+00', now());

\echo '--- R10 SENDING without claimed_at                             EXPECT ERROR ck_reminders_claimed_at'
INSERT INTO reminders (tenant_id, appointment_id, appointment_starts_at, status) VALUES (1, 1, '2030-01-03 10:00+00', 'SENDING');

\echo '--- R11 only the two good rows exist                           EXPECT new_rows = 2'
SELECT count(*) AS new_rows FROM reminders WHERE appointment_id = 1 AND created_at = now();

\echo '--- R12 the claim query can use the partial index              EXPECT idx_reminders_pending in the plan'
SET LOCAL enable_seqscan = off;
EXPLAIN (COSTS OFF) SELECT id FROM reminders WHERE status = 'PENDING' AND next_attempt_at <= now()
 ORDER BY next_attempt_at LIMIT 50 FOR UPDATE SKIP LOCKED;

ROLLBACK;
\echo
\echo 'Done. Everything above was rolled back.'
