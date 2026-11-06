# Testing

Each layer proves something the others can't.

| Layer | Examples | Needs | What it proves | What it can't prove |
|---|---|---|---|---|
| Pure unit | `MoneyTest`, `InvoiceTest`, `InvoiceStatusTest`, `AvailabilityCalculatorTest`, `RequestHashTest` | nothing | calculations and state machines | how parts work together |
| Mockito unit | `service/*Test` | nothing | a service's decisions: branches, order of calls, error mapping | SQL, locks, transactions, triggers |
| Database | `MigrationsTest`, `ReminderJobTest`, `AuditTrailTest`, `Concurrent*Test` | Docker running (Testcontainers) | SQL, locks, constraints, races | the behaviour of a real client |
| SQL scripts | `scripts/day*-tests.sql` | dev database (`appointme-db`) | the database refuses bad data even without Java | anything in Java |
| API | `ApiJourneyTest`, `TenantIsolationTest`, `RoleAccessTest`, `OpenApiEndpointTest` | Docker running (Testcontainers) | status codes, JSON and security as a real HTTP client sees them | internal details |
| Deployed | `scripts/smoke-test.sh`, `scripts/day27-app-role-tests.sql` | a running production stack | the image, the settings and the database roles | anything it doesn't call |

## Commands

```bash
./mvnw test -Dtest="com/kastellosth/appointme/service/*Test"   # Mockito tests, no database
./mvnw verify                                                  # everything, needs Docker running, not appointme-db
```

## The test database

Database tests extend `DatabaseTestBase`. It starts one empty PostgreSQL 16 for the whole run, using the same image as `docker-compose.yml`. Flyway applies every migration to it, and Testcontainers removes it afterwards.

- **Dev data is safe.** Tests never connect to `appointme-db`.
- **Migrations are tested from zero.** `MigrationsTest` checks that every migration file was applied.
- **A test can't reach the dev database by accident.** `src/test/resources/application.properties` points at a port where nothing listens, so a test that forgets the base class fails with "Connection to localhost:1 refused".
- **Tests share one database.** Each creates its own business, so no test depends on another.

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
