ALTER TABLE customers   ADD CONSTRAINT uq_customers_tenant_id   UNIQUE (tenant_id, id);
ALTER TABLE services    ADD CONSTRAINT uq_services_tenant_id    UNIQUE (tenant_id, id);
ALTER TABLE memberships ADD CONSTRAINT uq_memberships_tenant_id UNIQUE (tenant_id, id);

ALTER TABLE appointments
    ADD CONSTRAINT fk_appointments_customer_same_tenant
        FOREIGN KEY (tenant_id, customer_id) REFERENCES customers (tenant_id, id),
    ADD CONSTRAINT fk_appointments_service_same_tenant
        FOREIGN KEY (tenant_id, service_id)  REFERENCES services (tenant_id, id),
    ADD CONSTRAINT fk_appointments_staff_same_tenant
        FOREIGN KEY (tenant_id, staff_id)    REFERENCES memberships (tenant_id, id);