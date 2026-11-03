# Invoices

## Lifecycle (Day 20)

```
DRAFT  --> ISSUED --> PAID
  |           |
  +--> VOID <-+          (PAID and VOID are final)
```

- The legal moves are defined in ONE place: `InvoiceStatus.allowedNext()`. Everything else asks
  `canTransitionTo`. An illegal move → `IllegalStateTransitionException` (extends `ConflictException`) → 409.
- Only a DRAFT can be edited. `PUT /invoices/{id}/lines` replaces the whole line list; on any
  other status it returns 409.
- `Invoice` and `InvoiceLine` have **no setters**. The only way to change an invoice is through
  its methods (`replaceLines`, `issue`, `markPaid`, `voidInvoice`), and each one checks the status first.
- A mistake on an issued invoice is never edited: you VOID it and make a new one. That is how
  accounting works, and it keeps an audit trail.

## Endpoints (owner only)

| Method | Path | What |
|---|---|---|
| POST | `/invoices` | create a DRAFT (`customerId`, optional `appointmentId`, `currency`, `lines`) → 201 |
| GET | `/invoices/{id}` | one invoice with its lines |
| PUT | `/invoices/{id}/lines` | replace all lines (DRAFT only) |
| POST | `/invoices/{id}/issue` | DRAFT → ISSUED, assigns the number |
| POST | `/invoices/{id}/mark-paid` | ISSUED → PAID (temporary: payments replace it on Day 22) |
| POST | `/invoices/{id}/void` | DRAFT/ISSUED → VOID |

Every lookup is `findByIdAndTenantId` with the tenant from the token, so another business's
invoice is 404, never 403.

## Numbering: gapless, per tenant, per year

`INV-2026-0001`, `INV-2026-0002`, … Each business has its own sequence, and it restarts every year.
The year is the year **at the business** (its time zone), not in UTC: an invoice issued at
00:30 on 1 January in Athens belongs to the new year.

- The number is assigned at **issue**, not at creation. Abandoned drafts never use up a number.
- Why not a database SEQUENCE: a sequence never gives a number back. If a transaction takes
  number 7 and then rolls back, 7 is lost and the legal series has a hole.
- Instead, `invoice_counters (tenant_id, year, last_number)` is updated with one atomic upsert
  (`INSERT … ON CONFLICT DO UPDATE … RETURNING`) **inside the issuing transaction**
  (`InvoiceNumberService.next` is `Propagation.MANDATORY`):
  - the update locks the counter row until commit, so two issuers can never get the same number;
  - a rollback rolls the counter back as well, so no number is lost.
- The price: issuing is serialized per tenant (one at a time). That's fine for invoicing.
- `issue()` first calls `checkIssuable()`, so a refused request (for example no lines) never takes a number.
- Safety net: `UNIQUE (tenant_id, number)`.

## Money

- `BigDecimal` in Java, `NUMERIC` in PostgreSQL, never `double`.
- `line_total = quantity × unit_price`. This is exact (a whole number times a 2-decimal price), and a
  CHECK enforces it.
- Invoice: `total = subtotal + tax_total` (CHECK).
- Inputs with too many decimals are rejected (`@Digits` on the API, `RoundingMode.UNNECESSARY`
  in the entity), not silently rounded.

## Defence in depth: the database repeats the rules (V5)

| Rule | Enforced by |
|---|---|
| Lines change only while DRAFT | trigger `trg_invoice_lines_only_while_draft` (uses `FOR SHARE` on the invoice, so a concurrent issue waits) |
| Only legal status moves | trigger `trg_invoices_enforce_rules` |
| Amounts, number, customer, dates frozen after DRAFT | same trigger |
| Only a DRAFT can be deleted | same trigger |
| ISSUED/PAID has a number and `issued_at`; PAID ⇔ `paid_at` | CHECK constraints |
| Same tenant for customer, appointment, lines | composite FKs `(tenant_id, x_id)` |
| At most one live (not VOID) invoice per appointment | partial unique index `uq_invoices_one_live_per_appointment` |
| Line numbers unique per invoice | `UNIQUE (invoice_id, line_no) DEFERRABLE INITIALLY DEFERRED`, because Hibernate inserts the new lines before it deletes the old ones |

The triggers raise SQLState `23514` (check violation), so Spring reports them as
`DataIntegrityViolationException` → 409. Tests: `scripts/day20-invoice-tests.sql`.

## Day 21: automatic drafts, billing settings, rounding

### When an appointment becomes COMPLETED

`PATCH /appointments/{id}/status {"status":"COMPLETED"}` now also creates a **DRAFT** invoice,
in the **same transaction**:

1. The appointment must have started (`startsAt <= now`), otherwise 409. Same for `NO_SHOW`.
2. The status changes to COMPLETED.
3. `InvoiceService.createDraftForCompletedAppointment` (`Propagation.MANDATORY`: it refuses to run
   outside the caller's transaction) creates one line:
   `"<service name> (<start in the business's time zone>)"`, quantity 1, the service's **current**
   price, the business's **current** tax rate. Currency: the business's currency.
4. If the appointment already has a live (not VOID) invoice, for example one the owner made by hand,
   nothing is created. Completing is not blocked by that.

If step 3 fails, step 2 is rolled back: there is never a COMPLETED appointment that the code
forgot to invoice. Two "complete" requests at the same moment: one wins; the other gets 409
(optimistic lock on the appointment, or `uq_invoices_one_live_per_appointment`).

Staff may complete their own appointments, so they create drafts indirectly, but only owners
can see invoices. The draft is a proposal: the owner reviews it (`PUT /invoices/{id}/lines`) and issues it.

**Known limitation.** The price is copied at completion, not at booking. If the owner changes the
Haircut price between booking and completion, the new price is used. To bill the booked price, the
appointment would have to store the price when it is booked (a later improvement).

### Billing settings per business (V6)

`tenants.tax_rate NUMERIC(5,4)` (0.2400 = 24 %) and `tenants.currency VARCHAR(3)`.
`PUT /tenants/current/billing` (owner only) changes them. They are **copied** into new lines and
invoices, so changing them never changes an existing invoice.
Manual invoices: a line without `taxRate` gets the business's rate; an invoice without `currency`
gets the business's currency.

### Rounding

**Decision: tax is calculated per line and rounded half-up to cents. The invoice tax total is the
sum of the rounded line taxes.** The rule lives in one place, `support/Money.taxOf`.

| 3 lines of 0.10 at 24 % | per line (ours) | per invoice |
|---|---|---|
| tax per line | 0.024 → **0.02** each | (not shown per line) |
| tax total | 0.02 + 0.02 + 0.02 = **0.06** | round(0.30 × 0.24 = 0.072) = **0.07** |

Why per line:
- Every number printed on the invoice adds up. A customer can check each line, and the total
  is the sum of what they see.
- A line's tax never depends on the other lines, so editing one line never changes another
  line's tax.
- It works unchanged if lines ever have different rates (for example a reduced rate for some products).

Cost: the total tax can differ by a few cents from "rate × subtotal". Tax authorities have their
own rules about this. For a real business, check them with an accountant (in Greece, the myDATA
rules). If the rule has to change, only `Money.taxOf` and the totals in `Invoice.recalculate` change.

**Stored, not recalculated.** Each line stores its `tax_amount` (V6). On Day 20 the line tax was
recalculated every time it was read, so a change to the rounding code would have changed what
old, issued invoices showed. Now an issued invoice shows exactly what was stored when it was made.
V6 filled `tax_amount` for existing lines with the Day 20 rule (`round(line_total * tax_rate, 2)`).
For that one UPDATE it switched the "frozen lines" trigger off, inside the migration's transaction.

Half-up: 0.005 → 0.01. Amounts are never negative here, so Java's `HALF_UP` and PostgreSQL's
`round()` (half away from zero) give the same results. Too many decimals in an input is a bug,
not something to round: `Money.exactCents` throws instead.

### GET /invoices

`GET /invoices?status=&customerId=&appointmentId=&createdFrom=&createdTo=&page=0&size=20` (owner only).
- Every filter is optional. The tenant filter is always added (from the token).
- Sort order is fixed: newest first (`created_at DESC, id DESC`). `id` breaks ties, so paging is stable.
  The index `idx_invoices_tenant_created` matches this order.
- `size` must be 1–100 and `page` 0 or more, else 400. `createdFrom` must be before `createdTo` (half-open range).
- Rows are `InvoiceSummaryResponse`, **without lines**. Loading the lines of 20 invoices would be
  one extra query per invoice (N+1). Use `GET /invoices/{id}` for the full invoice.
- Filters are built with a JPA `Specification` (`InvoiceFilters`). This avoids the
  `(:p IS NULL OR col = :p)` JPQL trick, which PostgreSQL often rejects
  ("could not determine data type of parameter").

### Extra database safety net (V6)

`fk_invoices_appointment_same_customer`: an invoice with an appointment must be for that
appointment's customer. Java already checks this; now the database does too.
