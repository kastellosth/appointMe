-- Day 17 PRE-FLIGHT: list pairs of overlapping, non-cancelled appointments for the same staff member.
-- Must return ZERO rows before V4 can be applied.
SELECT a.id AS appt_a, b.id AS appt_b, a.staff_id,
       a.starts_at AS a_start, a.ends_at AS a_end,
       b.starts_at AS b_start, b.ends_at AS b_end
FROM appointments a
JOIN appointments b
  ON a.staff_id = b.staff_id
 AND a.id < b.id
 AND a.status <> 'CANCELLED' AND b.status <> 'CANCELLED'
 AND a.starts_at < b.ends_at AND b.starts_at < a.ends_at
ORDER BY a.staff_id, a.starts_at;
