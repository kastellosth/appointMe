# Scheduled jobs

Two jobs run inside the app, with no request and no logged-in user. In the audit log, `actor_user_id` is `null`.

| Job | When | What |
|---|---|---|
| `sendReminders` | 1 minute after the previous run ended (`app.reminders.delay`) | reminds customers of appointments in the next 24 hours |
| `deleteOldIdempotencyKeys` | 03:15 UTC (`app.idempotency.cleanup-cron`) | deletes keys older than 7 days |

`app.scheduling.enabled=false` switches both off. The tests do this, so that a job can't change their data.

## Reminders

`GET /reminders?status=&appointmentId=&page=0&size=20` (owner only, read-only, newest first).

```
PENDING --claim--> SENDING --> SENT
   ^                  |
   |                  +--> PENDING  (send failed: retry later)
   |                  +--> FAILED   (5 attempts used)
   |                  +--> SKIPPED  (nothing to send)
   +---- claim expired after 10 minutes (the app crashed)
```

One run has three steps:

1. **Create.** `INSERT … SELECT … ON CONFLICT DO NOTHING` adds a row for every SCHEDULED appointment in the next 24 hours.
2. **Claim.** One short transaction moves up to 50 rows to SENDING with `FOR UPDATE SKIP LOCKED`, then commits.
   The rows are picked in a `MATERIALIZED` CTE. Inside `WHERE id IN (SELECT … LIMIT n)`, PostgreSQL may run the
   subquery more than once and go past the limit. `ReminderJobTest` caught exactly this.
3. **Send.** For each row: check it is still valid, send with no transaction open, then record the result in a new transaction.

### Two copies of the app

Several instances can run at once, and each reminder still goes to only one of them:

- `FOR UPDATE` locks the rows being claimed. `SKIP LOCKED` makes another instance pass over them and take the next ones.
  Without `SKIP LOCKED` it would wait, then find they are taken, so the instances would work one after the other.
- Create needs no lock: the unique key makes a double insert harmless.
- Each status change is `UPDATE … WHERE id = ? AND status = 'SENDING'`. If the row has moved on, 0 rows change and no second audit event is written.

### Why nothing is held open during the send

An email call can take seconds. An open transaction during that call would hold a pooled connection and row locks the whole time, so a slow provider could exhaust the connection pool and stop bookings too.

### At-least-once

The send and the database update can't be one atomic step, so a crash between them forces a choice:

| Order | Crash in between | Result |
|---|---|---|
| Mark SENT, then send | the email never goes out | customer may miss the appointment |
| **Send, then mark SENT (ours)** | the row expires and is sent again | customer may get two reminders |

A duplicate reminder is the cheaper mistake. A real provider should receive `reminderId` as its idempotency key, which removes most duplicates.

### Failures

- A failed send is retried after 5, 10, 20 and 40 minutes. After 5 attempts it becomes FAILED, with a `REMINDER_FAILED` audit event.
- A crash counts as an attempt, because `attempts` goes up at claim time. A reminder that crashes the app can't loop forever.
- An error outside the send, such as a database failure in `prepare` or `markSent`, is logged and the batch continues. That row stays SENDING and is picked up again when its claim expires.
- `reason` stores the exception's class name, never its message: messages can contain addresses. Logs hold only the reminder id.

### When a reminder is skipped

Checked just before sending, so the data is fresh:

| Situation | Reason |
|---|---|
| Cancelled, completed, no-show | `appointment is CANCELLED` (and so on) |
| Moved to another time | `appointment was rescheduled` |
| Already started | `appointment has already started` |
| Customer has no email | `customer has no email` |

The unique key is `(appointment_id, appointment_starts_at)`, so a rescheduled appointment gets a new reminder, and a retried run doesn't.

## Idempotency key cleanup

- Deleted in batches of 1000, each in its own short transaction, so a big delete never holds many locks. `SKIP LOCKED` keeps two instances out of each other's way.
- **7 days, not 24 hours.** A key row costs almost nothing, and a late retry after the key is gone counts as a new payment. A retry the next day should still be a replay.
- A key still in use can't be deleted: it is committed together with its payment, so until then no other transaction can see it.

## Known limits

- **`ReminderJobTest` shares the dev database.** Its fake sender also handles real dev appointments that are due soon
  (within about 29 hours), marks them SENT and writes audit events. Reload the seed afterwards. Testcontainers
  will remove this.
- **There is no real email.** `LoggingReminderSender` only writes a log line. A real sender implements `ReminderSender`.
- **One lead time for everyone.** Per-business settings and SMS would be later additions.
- **The reminder row itself is never deleted.** A long-lived system would archive old rows.
- **Up to 50 reminders per run.** That is about 3000 per hour at the default delay. A slow provider must finish 50 sends within the 10-minute claim, or some are sent twice.
- **Time zones in the job:** the 24-hour window is absolute time, so it's right everywhere, and the message shows the business's local time.

## Proof

- `scripts/day24-reminder-tests.sql`: the database rules. Rolled back at the end.
- `ReminderJobTest`: exactly-once under 5 parallel runners, retries, crash recovery, skips, cleanup.
- `http/day24-reminders.http`: the read API.
