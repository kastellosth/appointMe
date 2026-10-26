# Security: tenant isolation

appointMe is a **multi-tenant** system: many businesses share one database, and every
tenant-owned row carries a `tenant_id`. The main risk of that design is a single query that
forgets its tenant filter. This page explains how isolation is enforced, and lists every
attack I tried against my own API.

## How isolation is enforced (defence in depth)

| Layer | Rule | Where |
|---|---|---|
| Identity | Tenant and role come from a **signed JWT**, never from the URL, query string, header or body | `CurrentUser`, `JwtService` |
| Membership | A tenant token is only issued after the user's membership is verified in the database | `AuthService.selectTenant` |
| Queries | Every lookup on tenant data includes the tenant (`findByIdAndTenantId`) | repositories and services |
| References | Customer, service and staff of a booking are loaded **with the caller's tenant**, so foreign ids behave like missing ones | `AppointmentService.create` |
| Responses | Another tenant's record returns **404**, identical to a record that does not exist, so ids cannot be probed | `GlobalExceptionHandler` |
| Roles | `OWNER` / `STAFF` rules via `@PreAuthorize`; staff see only their own appointments | controllers, `AppointmentService` |
| **Database** | Composite foreign keys `(tenant_id, customer_id)`, `(tenant_id, service_id)`, `(tenant_id, staff_id)` make it **impossible to store** a booking that points at another tenant's data, even if application code has a bug | `V2__tenant_consistency_constraints.sql` |

## Attack log

"Result" is what really happened when I ran it against my local instance
(Spring Boot 4.1.1, PostgreSQL 16, seed data from `scripts/dev-seed.sql`) on 2026-10-26.
Every attack must satisfy three things: expected status, no foreign data in the response,
and no change in the database.

| ID | Attack | Expected | Defended by | Result |
|---|---|---|---|---|
| ATK-01 | Read another tenant's service by id | 404 | tenant-scoped query | PASS: 404 |
| ATK-02 | Read another tenant's customer by id | 404 | tenant-scoped query | PASS: 404 |
| ATK-03 | Read another tenant's appointment by id | 404 | tenant-scoped query | PASS: 404, no foreign note in body |
| ATK-04 | Probe a non-existent id (compare with ATK-01) | 404, indistinguishable | uniform error | PASS: 404, same shape as ATK-01 |
| ATK-05 | Update another tenant's service | 404, unchanged | tenant-scoped query | PASS: 404, service unchanged |
| ATK-06 | Deactivate another tenant's service | 404, unchanged | tenant-scoped query | PASS: 404, service still active |
| ATK-07 | Update another tenant's customer | 404, unchanged | tenant-scoped query | PASS: 404, customer unchanged |
| ATK-08 | Delete another tenant's customer | 404, unchanged | tenant-scoped query | PASS: 404, customer still exists |
| ATK-09 | Change status of another tenant's appointment | 404, unchanged | tenant-scoped query | PASS: 404, status unchanged |
| ATK-10 | Book using another tenant's customer | 404 | scoped lookup + composite FK | PASS: 404 |
| ATK-11 | Book using another tenant's service | 404 | scoped lookup + composite FK | PASS: 404 |
| ATK-12 | Book using another tenant's staff member | 404 | scoped lookup + composite FK | PASS: 404 |
| ATK-13 | Book using all three foreign ids | 404 | scoped lookup + composite FK | PASS: 404 |
| ATK-14 | List services, look for foreign rows | none | tenant-scoped query | PASS: 200, no foreign rows |
| ATK-15 | Search customers by a foreign customer's name | 0 results | tenant-scoped query | PASS: 200, 0 results |
| ATK-16 | List customers at maximum page size | none foreign | tenant-scoped query | PASS: 200, no foreign rows |
| ATK-17 | List a month of appointments | none foreign | tenant-scoped query | PASS: 200, only own bookings |
| ATK-18 | List the team | none foreign | tenant-scoped query | PASS: 200, own team only |
| ATK-19 | `?tenantId=2` in the query string | ignored | tenant from token only | PASS: 200, parameter ignored |
| ATK-20 | `X-Tenant-Id: 2` header | ignored | tenant from token only | PASS: 200, header ignored |
| ATK-21 | `tenantId` / `tenant` in a JSON body | ignored, row in own tenant | DTOs have no such field | PASS: row stored in tenant 1 |
| ATK-22 | Select a business I am not a member of | 404 | membership check | PASS: 404 |
| ATK-23 | Forge `tenant_id` in a token | 401 | HS256 signature | PASS: 401 |
| ATK-24 | Forge `role` in a token | 401 | HS256 signature | PASS: 401 |
| ATK-25 | Unsigned `alg: none` token | 401 | decoder pinned to HS256 | PASS: 401 |
| ATK-26 | User token (no business) on business endpoint | 403 | `CurrentUser.tenantId()` | PASS: 403 |
| ATK-27 | No token | 401 | `anyRequest().authenticated()` | PASS: 401 |
| ATK-28 | SQL: insert booking with another tenant's customer | rejected | composite FK | PASS: rejected by `fk_appointments_customer_same_tenant` |
| ATK-29 | SQL: insert booking with another tenant's service | rejected | composite FK | PASS: rejected by `fk_appointments_service_same_tenant` |
| ATK-30 | SQL: insert booking with another tenant's staff | rejected | composite FK | PASS: rejected by `fk_appointments_staff_same_tenant` |
| ATK-31 | SQL: update a valid booking to a foreign customer | rejected | composite FK | PASS: rejected by composite FK |
| ROLE-01..11 | Staff attempting owner actions, reading colleagues' bookings, booking for others | 403 / 404 | `@PreAuthorize`, service scope | PASS: see `day13-roles.http` |

## What this exercise showed

- **The database layer earned its place.** Before migration V2 was applied, the three SQL attacks
  (ATK-28 to ATK-30) were *accepted*: PostgreSQL stored appointments pointing at another tenant's
  customer, service and staff member. Once V2 was applied, the same statements were rejected. The
  application layer already refused these references, so this layer only matters on the day a
  query forgets its tenant filter, and that is the day it protects the data.
- **A constraint can fail on existing data.** V2 refused to apply while the leftover ATK-28 row was
  in the table (`Key (tenant_id, customer_id)=(1, 3) is not present in table "customers"`). The
  migration runs in a transaction, so it failed cleanly with nothing half-applied. In a real
  system I would first run a query that lists the offending rows, then decide whether to repair or
  remove each one, and only then ship the migration.
- **Uniform 404s.** A foreign record and a non-existent record look the same to the caller
  (ATK-01 against ATK-04), so ids cannot be probed.
- **Every attack was checked three ways:** expected status, no foreign data in the response, and no
  change in the database.

## Known limitations (honest list)

- **Tokens are snapshots.** A role or membership change is not seen until the token expires
  (30 minutes). Mitigation planned: shorter lifetime for owner tokens, or re-check on sensitive actions.
- **Email probing on `POST /staff`.** An owner can learn whether an email has an account. A real
  product would send invitation emails instead.
- **No rate limiting** on login or registration yet (stretch goal).
- **No token revocation or refresh tokens** (stretch goal).
- **Dev default JWT secret** exists for local convenience; deployment requires `JWT_SECRET` (Day 27).