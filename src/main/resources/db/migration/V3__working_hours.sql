-- Day 15: weekly working hours per staff member.
-- Times are WALL-CLOCK times in the tenant's time zone (see tenants.timezone),
-- so they are TIME, not TIMESTAMPTZ.
-- day_of_week: 1 = Monday ... 7 = Sunday (same numbers as java.time.DayOfWeek).
-- Stored as INTEGER (not SMALLINT) so Hibernate's schema validation matches a Java int.

CREATE TABLE working_hours (
    id          BIGSERIAL PRIMARY KEY,
    tenant_id   BIGINT  NOT NULL REFERENCES tenants(id),
    staff_id    BIGINT  NOT NULL,
    day_of_week INTEGER NOT NULL CHECK (day_of_week BETWEEN 1 AND 7),
    start_time  TIME    NOT NULL,
    end_time    TIME    NOT NULL,
    CHECK (end_time > start_time),
    -- Day 14 idea again: the staff member must belong to the SAME tenant as this row.
    CONSTRAINT fk_working_hours_staff_same_tenant
        FOREIGN KEY (tenant_id, staff_id) REFERENCES memberships (tenant_id, id)
);

CREATE INDEX idx_working_hours_staff ON working_hours (staff_id, day_of_week);
