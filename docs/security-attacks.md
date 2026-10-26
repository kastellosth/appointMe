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

"Result" is filled in with what really happened when I ran it.
Every attack must satisfy three things: expected status, no foreign data in the response,
and no change in the database.

| ID | Attack | Expected | Defended by | Result |
|---|---|---|---|---|
| ATK-01 | Read another tenant's service by id | 404 | tenant-scoped query | |
| ATK-02 | Read another tenant's customer by id | 404 | tenant-scoped query | |
| ATK-03 | Read another tenant's appointment by id | 404 | tenant-scoped query | |
| ATK-04 | Probe a non-existent id (compare with ATK-01) | 404, indistinguishable | uniform error | |
| ATK-05 | Update another tenant's service | 404, unchanged | tenant-scoped query | |
| ATK-06 | Deactivate another tenant's service | 404, unchanged | tenant-scoped query | |
| ATK-07 | Update another tenant's customer | 404, unchanged | tenant-scoped query | |
| ATK-08 | Delete another tenant's customer | 404, unchanged | tenant-scoped query | |
| ATK-09 | Change status of another tenant's appointment | 404, unchanged | tenant-scoped query | |
| ATK-10 | Book using another tenant's customer | 404 | scoped lookup + composite FK | |
| ATK-11 | Book using another tenant's service | 404 | scoped lookup + composite FK | |
| ATK-12 | Book using another tenant's staff member | 404 | scoped lookup + composite FK | |
| ATK-13 | Book using all three foreign ids | 404 | scoped lookup + composite FK | |
| ATK-14 | List services, look for foreign rows | none | tenant-scoped query | |
| ATK-15 | Search customers by a foreign customer's name | 0 results | tenant-scoped query | |
| ATK-16 | List customers at maximum page size | none foreign | tenant-scoped query | |
| ATK-17 | List a month of appointments | none foreign | tenant-scoped query | |
| ATK-18 | List the team | none foreign | tenant-scoped query | |
| ATK-19 | `?tenantId=2` in the query string | ignored | tenant from token only | |
| ATK-20 | `X-Tenant-Id: 2` header | ignored | tenant from token only | |
| ATK-21 | `tenantId` / `tenant` in a JSON body | ignored, row in own tenant | DTOs have no such field | |
| ATK-22 | Select a business I am not a member of | 404 | membership check | |
| ATK-23 | Forge `tenant_id` in a token | 401 | HS256 signature | |
| ATK-24 | Forge `role` in a token | 401 | HS256 signature | |
| ATK-25 | Unsigned `alg: none` token | 401 | decoder pinned to HS256 | |
| ATK-26 | User token (no business) on business endpoint | 403 | `CurrentUser.tenantId()` | |
| ATK-27 | No token | 401 | `anyRequest().authenticated()` | |
| ATK-28 | SQL: insert booking with another tenant's customer | rejected | composite FK | |
| ATK-29 | SQL: insert booking with another tenant's service | rejected | composite FK | |
| ATK-30 | SQL: insert booking with another tenant's staff | rejected | composite FK | |
| ATK-31 | SQL: update a valid booking to a foreign customer | rejected | composite FK | |
| ROLE-01..11 | Staff attempting owner actions, reading colleagues' bookings, booking for others | 403 / 404 | `@PreAuthorize`, service scope | see `day13-roles.http` |

## Known limitations (honest list)

- **Tokens are snapshots.** A role or membership change is not seen until the token expires
  (30 minutes). Mitigation planned: shorter lifetime for owner tokens, or re-check on sensitive actions.
- **Email probing on `POST /staff`.** An owner can learn whether an email has an account. A real
  product would send invitation emails instead.
- **No rate limiting** on login or registration yet (stretch goal).
- **No token revocation or refresh tokens** (stretch goal).
- **Dev default JWT secret** exists for local convenience; deployment requires `JWT_SECRET` (Day 27).
