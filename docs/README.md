# appointMe

A multi-tenant appointment scheduling and invoicing backend. Many small
businesses (barbers, clinics, tutors) share one system, each with fully
isolated data.

## Status

Work in progress. Currently implemented: database schema and domain model.

## Tech stack

- Java 21, Spring Boot 4
- PostgreSQL 16 (Docker), Flyway migrations
- Spring Data JPA / Hibernate

## Data model

![ER diagram](docs/er-diagram.png)

## Run locally

1. Start the database: `docker compose up -d`
2. Start the app: `./mvnw spring-boot:run` (Windows: `mvnw.cmd spring-boot:run`)

## Design notes

- **Multi-tenancy:** shared schema, every tenant-owned table has `tenant_id`.
- **Schema ownership:** Flyway owns the schema; Hibernate only validates it.