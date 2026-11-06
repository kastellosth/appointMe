# Testing

Each layer proves something the others can't.

| Layer | Examples | Needs | What it proves | What it can't prove |
|---|---|---|---|---|
| Pure unit | `MoneyTest`, `InvoiceTest`, `RequestHashTest` | nothing | calculations and state machines | how parts work together |
| Mockito unit | `service/*Test` | nothing | a service's decisions: branches, order of calls, error mapping | SQL, locks, transactions, triggers |
| Database | `ReminderJobTest`, `AuditTrailTest`, `Concurrent*Test` | Docker database | SQL, locks, constraints, races | the behaviour of a real client |
| SQL scripts | `scripts/day*-tests.sql` | Docker database | the database refuses bad data even without Java | anything in Java |
| HTTP | local `http/*.http` files, git-ignored | running app | status codes, JSON, security | internal details |

## Commands

```bash
./mvnw test -Dtest="com/kastellosth/appointme/service/*Test"   # Mockito tests, no database
./mvnw test                                                    # everything, needs the Docker database
```

`ReminderJobTest` changes dev data, so reload the seed after a full run.

## What gets mocked

- **Mocked:** what a service talks to across a boundary: repositories, `JdbcTemplate`, `ReminderSender`, and other services.
- **Real:** entities, DTOs, `Money`, `RequestHash`, `AvailabilityCalculator`. They are plain Java, and their rules are part of the behaviour under test.

## What a mock can't catch

Both of these were tried on purpose. All Mockito tests still passed:

- An SQL bug in `ReminderService.claim` (`<=` changed to `>=`). `ReminderJobTest` caught it.
- `@Transactional` removed from `PaymentService.record`. In a Mockito test Spring isn't running, so the annotation does nothing. The tests with a real database are the ones where it takes effect.

So a Mockito test never replaces a database test for SQL, locks or transactions.

## Known limit

Mockito prints a warning that it attaches itself as a Java agent. This works on Java 21, but a future JDK will block it, so the build must load Mockito as an agent when the project moves to that JDK.
