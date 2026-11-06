# Payments and idempotency (Day 22)

## Endpoints (owner only)

| Method | Path | What |
|---|---|---|
| POST | `/invoices/{id}/payments` | record a payment. **Header `Idempotency-Key` required.** Body: `amount`, `method` (CASH, CARD, BANK_TRANSFER), optional `reference` |
| GET | `/invoices/{id}/payments` | status, total, paid, balance, and the list of payments |

- Only an **ISSUED** invoice takes payments. Partial payments are fine.
- A payment can never be more than the open balance (409).
- The payment that brings the balance to 0.00 marks the invoice **PAID** automatically, in the
  same transaction. The temporary Day 20 `POST /invoices/{id}/mark-paid` is gone.
- An invoice that has received money cannot be voided (409). That would need a refund first (not built).
- Payments are **append-only**: no update and no delete, in Java (no setters) and in the database (trigger).

## Danger 1: the same request twice → Idempotency-Key

A client sends "pay 10.00". The payment is stored, but the response is lost (timeout, Wi-Fi drop,
or a double click). The client cannot know whether it worked, so it sends the request again.
Without protection, the customer pays twice.

**Rule:** the client creates one unique key per *intended* payment (a UUID) and sends it with
every attempt of that payment. The server does the work at most once per key.

| Situation | Answer |
|---|---|
| New key | do the payment → 201, `Idempotent-Replayed: false` |
| Same key, same request (a retry) | **nothing is done again**: the stored result → 201, the same body, `Idempotent-Replayed: true` |
| Same key, a different request | 422: a client bug. Neither replaying nor paying would be correct |
| Same key while the first attempt is still running | waits for it, then gets its result (a replay) |
| The first attempt failed (for example 409) | nothing was stored, so the key is free again |

"Same request" = the same SHA-256 fingerprint of invoice id, amount (10, 10.0 and 10.00 are the
same), method and reference (`support/RequestHash`).
Keys are **per business**: tenant 2 using the same key string as tenant 1 is unrelated.

### How it works (one transaction)

```
BEGIN
  INSERT INTO idempotency_keys (...) ON CONFLICT (tenant_id, key) DO NOTHING      -- claim
    1 row  -> the key is ours, continue
    0 rows -> SELECT it: same fingerprint -> replay the stored payment; different -> 422
  SELECT ... FROM invoices WHERE id = ? FOR NO KEY UPDATE                       -- lock the invoice
  check status ISSUED, amount <= total - sum(payments)
  INSERT INTO payments (...)                                                    -- trigger re-checks
  UPDATE invoices SET status = 'PAID' ...      (only if balance_after = 0)
  UPDATE idempotency_keys SET payment_id = ?                                    -- remember the result
COMMIT
```

Why the key row is inserted **first**, in the **same** transaction:
- A duplicate request's INSERT hits the primary key and **waits** until the first transaction
  ends. Then it either sees the committed row (replay), or, if the first one rolled back, inserts
  the key itself and does the work. Two attempts can never both pay.
- Failures roll the key back together with everything else: a failed attempt leaves no "poisoned" key.

Why not "SELECT the key; if it doesn't exist, do the work": two requests can both see "no key"
and both pay. That is the same check-then-act race as double booking (see `concurrency.md`).
Only a unique constraint closes it.

**The replay returns the historic answer.** `PaymentResponse` is built only from the stored
payment row, including `balance_after`, the balance right after that payment. A retry the next
day still gets "balance 21.00", not today's balance. That is what the first response said, and
it's what a retry must get.

Known limits (fine for now):
- Only successes are stored. A retried 409 is re-evaluated, which can give a different answer if
  the situation changed in between. Stripe-style APIs also store errors. That would need the
  response status and body in the table.
- Keys are deleted after 7 days by a nightly job (see `reminders.md`). A retry after that counts as a new payment,
  and only the balance check limits it.
- The key table is payment-specific (`payment_id`). For more endpoints, store the response status
  and body instead.

## Danger 2: two different payments at the same moment → row lock

Two cashiers each record the full 100.00 for the same invoice (two different keys). Both read
"balance 100.00", both insert, and the invoice is paid twice. That's the check-then-act race again.

`InvoiceRepository.findForUpdate` (`@Lock(PESSIMISTIC_WRITE)` → `SELECT … FOR NO KEY UPDATE`)
locks the invoice row. The second transaction waits there until the first commits, and then
reads the **new** balance. Pessimistic, not optimistic: a conflict is likely here (same invoice,
same moment), and waiting is cheap.

## Database safety net (V7)

| Rule | Enforced by |
|---|---|
| Payment only for an ISSUED invoice of the same tenant | trigger `trg_payments_check_invoice` (+ composite FK) |
| Never more than the open balance; `balance_after` correct | same trigger (it also locks the invoice row) |
| amount > 0, a known method | CHECK constraints |
| Payments never change or disappear | trigger `trg_payments_append_only` |
| PAID only when payments = total | trigger `trg_invoices_payment_rules` |
| No VOID after money was received | same trigger |
| A key belongs to one business; its payment is of the same business | PK `(tenant_id, key)`, composite FK |

Payment ids have gaps (failed inserts use up sequence values). That's fine: unlike invoice
numbers, ids carry no legal meaning.

## Proof

- `scripts/day22-payment-tests.sql`: the database rules, one by one.
- `ConcurrentPaymentTest`: 20 × the same key at once → 1 payment, 20 answers naming the same id.
  10 × full payment with different keys → 1 wins, 9 × 409, invoice PAID, 1 key stored.
- `http/day22-payments.http`: the whole flow over HTTP, including the 422 and the historic replay.
