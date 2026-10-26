-- Day 17: the DATABASE refuses two overlapping appointments for the same staff member.
--
-- Why in the database: "is the slot free?" checked in Java and then INSERT are two separate
-- steps. Two requests can both see "free" and both insert (a race condition). A constraint is
-- checked by PostgreSQL itself, atomically, for every insert and update, from any client.

-- Lets a GiST index compare plain equality (staff_id) together with range overlap (&&).
-- Needs superuser rights; the Docker postgres user has them. Managed hosts: check it is allowed.
CREATE EXTENSION IF NOT EXISTS btree_gist;

ALTER TABLE appointments
    ADD CONSTRAINT appointments_no_overlap
    EXCLUDE USING gist (
        staff_id WITH =,                                  -- same staff member ...
        tstzrange(starts_at, ends_at, '[)') WITH &&       -- ... and overlapping time
    )
    WHERE (status <> 'CANCELLED');                        -- cancelled bookings free their slot
-- '[)' = start inclusive, end exclusive, so 10:00-10:30 and 10:30-11:00 do NOT conflict.
-- staff_id is a membership id, unique across all tenants, so tenant_id is not needed here.
