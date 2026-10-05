CREATE TABLE tenants (
                         id         BIGSERIAL PRIMARY KEY,
                         name       VARCHAR(150) NOT NULL,
                         slug       VARCHAR(80)  NOT NULL UNIQUE,
                         timezone   VARCHAR(50)  NOT NULL DEFAULT 'UTC',
                         created_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE users (
                       id            BIGSERIAL PRIMARY KEY,
                       email         VARCHAR(255) NOT NULL UNIQUE,
                       password_hash VARCHAR(255) NOT NULL,
                       full_name     VARCHAR(100) NOT NULL,
                       created_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE memberships (
                             id         BIGSERIAL PRIMARY KEY,
                             user_id    BIGINT      NOT NULL REFERENCES users(id),
                             tenant_id  BIGINT      NOT NULL REFERENCES tenants(id),
                             role       VARCHAR(20) NOT NULL,
                             created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
                             UNIQUE (user_id, tenant_id)
);

CREATE TABLE services (
                          id               BIGSERIAL PRIMARY KEY,
                          tenant_id        BIGINT        NOT NULL REFERENCES tenants(id),
                          name             VARCHAR(150)  NOT NULL,
                          duration_minutes INT           NOT NULL CHECK (duration_minutes > 0),
                          price            NUMERIC(10,2) NOT NULL CHECK (price >= 0),
                          active           BOOLEAN       NOT NULL DEFAULT TRUE,
                          created_at       TIMESTAMPTZ   NOT NULL DEFAULT now()
);

CREATE TABLE customers (
                           id         BIGSERIAL PRIMARY KEY,
                           tenant_id  BIGINT       NOT NULL REFERENCES tenants(id),
                           full_name  VARCHAR(100) NOT NULL,
                           email      VARCHAR(255),
                           phone      VARCHAR(30),
                           created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
                           UNIQUE (tenant_id, email)
);

CREATE TABLE appointments (
                              id          BIGSERIAL PRIMARY KEY,
                              tenant_id   BIGINT      NOT NULL REFERENCES tenants(id),
                              staff_id    BIGINT      NOT NULL REFERENCES memberships(id),
                              customer_id BIGINT      NOT NULL REFERENCES customers(id),
                              service_id  BIGINT      NOT NULL REFERENCES services(id),
                              starts_at   TIMESTAMPTZ NOT NULL,
                              ends_at     TIMESTAMPTZ NOT NULL,
                              status      VARCHAR(20) NOT NULL DEFAULT 'SCHEDULED',
                              notes       TEXT,
                              created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
                              version     BIGINT      NOT NULL DEFAULT 0,
                              CHECK (ends_at > starts_at)
);

CREATE INDEX idx_memberships_tenant       ON memberships(tenant_id);
CREATE INDEX idx_services_tenant          ON services(tenant_id);
CREATE INDEX idx_customers_tenant         ON customers(tenant_id);
CREATE INDEX idx_appointments_tenant_time ON appointments(tenant_id, starts_at);
CREATE INDEX idx_appointments_staff_time  ON appointments(staff_id, starts_at);