# Audit log

The audit log answers: **who did what, to which record, and when?** An invoice's `status` tells you
that it is PAID. The audit log tells you who issued it, who took each payment, and in what order.

## Endpoint (owner only)

`GET /audit-events?entityType=&entityId=&action=&actorUserId=&from=&to=&page=0&size=20`

- Every filter is optional. The tenant filter is always added (from the token).
- Sort order is fixed: newest first (`occurred_at DESC, id DESC`). The indexes match this order.
- `entityId` needs `entityType` (400): ids are only unique per table, so "entity 5" alone would mix
  invoice 5 and customer 5.
- `from` must be before `to` (half-open range). `size` is 1–100, `page` is 0 or more. Otherwise 400.
- Another business's events are simply not found: an empty page, never 403.
- It is read-only. There is no way to write, change or delete an event over HTTP (POST → 405).

The story of one invoice: `GET /audit-events?entityType=INVOICE&entityId=1`

| action | actor | details |
|---|---|---|
| `INVOICE_PAID` | 1 | `ISSUED -> PAID` |
| `PAYMENT_RECORDED` | 1 | `payment 2: 21.00 EUR CASH, balance 0.00` |
| `PAYMENT_RECORDED` | 1 | `payment 1: 10.00 EUR CARD, balance 21.00` |
| `INVOICE_ISSUED` | 1 | `number INV-2026-0001, total 31.00` |
| `INVOICE_LINES_REPLACED` | 1 | `line count 1, total 31.00` |
| `INVOICE_CREATED` | 1 | `draft by hand, total 31.00` |

## What is recorded

| Record | Actions |
|---|---|
| Appointment | `APPOINTMENT_CREATED`, `APPOINTMENT_STATUS_CHANGED`, `APPOINTMENT_CANCELLED`, `APPOINTMENT_RESCHEDULED` |
| Invoice | `INVOICE_CREATED`, `INVOICE_LINES_REPLACED`, `INVOICE_ISSUED`, `INVOICE_VOIDED`, `INVOICE_PAID`, `PAYMENT_RECORDED` |
| Customer | `CUSTOMER_CREATED`, `CUSTOMER_UPDATED`, `CUSTOMER_DELETED` |
| Staff | `STAFF_ADDED` |
| Tenant | `TENANT_BILLING_CHANGED` |

`PAYMENT_RECORDED` belongs to the invoice, so an invoice's history includes its payments.

## The decision: same transaction, not an event listener

`AuditService.record` runs inside the transaction that makes the change
(`Propagation.MANDATORY`: outside a transaction it throws). There are two other common designs:

| Design | Crash or failure right after the commit | Failure of the audit insert |
|---|---|---|
| **Same transaction, direct call (ours)** | nothing is lost: the change and its event are already committed together | the change is rolled back too |
| `@TransactionalEventListener(AFTER_COMMIT)` | **the event is lost**: the change exists with no history | the change stays, with no history |
| `@TransactionalEventListener(BEFORE_COMMIT)` | nothing is lost (it is still the same transaction) | the change is rolled back too |

`AFTER_COMMIT` keeps the request a little faster and lets a business action succeed even if the log is
broken. For a log that might be used to settle a dispute about money, a hole is the worse failure.

`BEFORE_COMMIT` is as safe as a direct call and separates the two concerns better, but you can no
longer see from the service what happens next. A direct call is easier to read and to debug, so we use
that. If many listeners ever need the same events, `BEFORE_COMMIT` is the natural next step.

**What this gives for free:**
- A refused request (409, 400) throws before or instead of the audit call, and any later failure rolls
  the event back. A failed attempt leaves no event.
- A customer delete that fails at commit (the customer has invoices) takes its event with it.
- A replayed payment returns before the audit call, so a retry adds no history. 20 × the same
  Idempotency-Key gives one `PAYMENT_RECORDED`.

**The price:** one extra INSERT per change, and a broken audit table stops business changes. Both are accepted.

## Who is the actor?

`CurrentUser.userIdOrNull()`: the user id from the JWT, or `null` when nobody is logged in on this
thread. `null` means the system did it, for example a scheduled job.

Staff can act too. If Bob completes an appointment, both the status change and the automatic draft
invoice show Bob as the actor.

## No personal data

`details` holds only ids, statuses, amounts and dates. It **never** holds names, emails, phone numbers,
appointment notes or the payment `reference`, which is free text.

The table is append-only, so nothing in it could be erased after a GDPR erasure request. If the log
holds only ids, deleting the customer row makes those ids anonymous, and the history can stay.

This needs discipline: the database cannot tell a name from any other text, so it depends on code review and tests.

## Why `entity_id` is not a foreign key

The history of a customer must outlive the customer. A foreign key would either block the delete or
remove the history. `tenant_id` and `actor_user_id` are real foreign keys.

## Database safety net (V8)

| Rule | Enforced by |
|---|---|
| Events never change or disappear | trigger `trg_audit_events_append_only` (SQLState 23514) |
| Belongs to a real business; the actor is a real user | foreign keys |
| Tenant, action, entity type and id are present | `NOT NULL` |
| Action and entity type look like constant names | CHECK constraints |
| `details` is short | `VARCHAR(500)` |

In Java: no setters, Hibernate `@Immutable`, and `entityType` comes from the action, so
`(INVOICE_ISSUED, CUSTOMER)` cannot be written.

## Known limits (fine for now)

- **Not tamper-proof against a database superuser.** `TRUNCATE` fires no row triggers (the dev seed
  relies on that), and `session_replication_role = replica` switches triggers off (the tests' cleanup
  uses that). In production the application connects as a role that has only INSERT and SELECT
  on this table (see `deploy.md`). Real tamper-evidence would need hash-chained rows or shipping
  events to another system.
- **Only committed changes are recorded.** Reads, failed logins and refused requests are not. Those
  belong in the application or security logs.
- **`actor_user_id` is not checked against membership.** It comes from a signed token and the service
  already checked the tenant, so nothing new is enforced in the database.
- **Tenant creation is not recorded.** It could be added; it was left out of this first version.
  User registration has no tenant at all, so it does not fit this table.
- **The table grows forever.** Partitioning by month and an archive policy are the usual answers.
  Keeping financial history for the legally required period is a question for an accountant.
- **`occurred_at` is the Java clock,** taken at the moment of the call, so two events in different
  transactions can commit in a different order than their timestamps.

## Proof

- `scripts/day23-audit-tests.sql`: the database rules, one by one. Rolled back at the end, so it leaves nothing behind.
- `AuditTrailTest`: the same-transaction promise (rollback both ways, 20 × the same key, tenant isolation).
- `AuditEventTest`: pure unit test, no database.
