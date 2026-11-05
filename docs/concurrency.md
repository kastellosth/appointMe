# Concurrency: double booking

## The problem

"Is the slot free?" (a SELECT) and "book it" (an INSERT) are two separate steps. Two requests can
both see "free", and both insert. Java alone cannot stop this: every request runs in its own
thread and transaction, and neither can see the other's uncommitted row.

## The fix: the database decides (V4)

```sql
ALTER TABLE appointments
    ADD CONSTRAINT appointments_no_overlap
    EXCLUDE USING gist (
        staff_id WITH =,
        tstzrange(starts_at, ends_at, '[)') WITH &&
    )
    WHERE (status <> 'CANCELLED');
```

- Two rows with the same `staff_id` whose time ranges overlap (`&&`) cannot both exist.
  PostgreSQL checks this atomically on every INSERT and UPDATE, from any client.
- `'[)'`: start inclusive, end exclusive. 10:00–10:30 and 10:30–11:00 do **not** conflict.
- Cancelled appointments are ignored, so cancelling frees the slot.
- `btree_gist` lets one GiST index combine `=` (staff) with `&&` (ranges).
- `staff_id` is a membership id, which is unique across tenants, so `tenant_id` is not needed here.

The Java checks in `AppointmentService` (in the future, inside working hours) stay. They give
clear error messages for the normal case. The constraint is what makes it **correct** under load.

## How a clash reaches the client

`AppointmentService.saveOrConflict` uses `saveAndFlush`, so the INSERT runs **inside** the
service's try block, not later at commit where it could not be caught:

| SQLState | Meaning | Answer |
|---|---|---|
| `23P01` | exclusion violation: the slot is taken | 409 "That time slot is already booked" |
| `40P01` | deadlock detected: PostgreSQL aborted this transaction | 409 "being booked by someone else, try again" |

`DatabaseErrors` walks the exception's cause chain to find the `SQLException` and its SQLState.
`GlobalExceptionHandler` repeats the mapping as a safety net, for errors that surface outside the
service. It also maps `PessimisticLockingFailureException` to 409.

### Why deadlocks happen at all

With many parallel inserts for the same staff member, each transaction may have to **wait** for
another's uncommitted row before it can decide whether the constraint is violated. With three or
more transactions, these waits can form a cycle: A waits for B, and B waits for A. PostgreSQL
detects the cycle, aborts one transaction with `40P01`, and the others continue. Nothing was
stored by the aborted transaction, so for the client it means the same thing as a conflict: the
slot is not available.

This showed up in the Day 19 tests as "UNEXPECTED" outcomes (a 500 over HTTP), until `40P01` was mapped.

## Proof: the tests

Both tests create their own throw-away business (`RaceTestData`), so they don't touch the dev
seed, and they delete it afterwards. Both need the Docker database running.

- **`ConcurrentBookingTest`**: 30 threads call `AppointmentService.create` for the same slot, all
  released at the same instant (`ParallelRunner`: one thread per task, a `CountDownLatch` "ready"
  and a "go" latch). Expected: exactly 1 `CREATED` and 29 `ConflictException`, **and exactly 1 row
  in the database**. The database count is the decisive check.
- **`ConcurrentBookingHttpTest`**: the same over real HTTP. Expected: exactly one 201, 29 × 409,
  and 1 row.

The Hikari pool is raised to 40 connections in these tests. With the default of 10, most
threads would just queue for a connection and never really collide.

## Related

- Payments (Day 22): the same request twice is stopped by an idempotency key (a unique constraint
  plus a replay). Two different payments at once are stopped by a row lock on the invoice
  (`SELECT … FOR NO KEY UPDATE`). See `payments.md`.

- Invoices (Day 20) use the same idea for numbering: the counter row is updated inside the
  issuing transaction, so the row lock serializes issuers (see `invoices.md`).
- Two "complete" requests for one appointment: see `invoices.md`, Day 21.
