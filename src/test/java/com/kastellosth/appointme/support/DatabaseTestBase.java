package com.kastellosth.appointme.support;

import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * One PostgreSQL for the whole test run: starting one per test class would add seconds each.
 * Testcontainers removes it when the JVM exits.
 */
public abstract class DatabaseTestBase {

    public static final String DATABASE_NAME = "appointme_test";

    // A superuser, like the dev database: RaceTestData needs one to delete frozen financial rows.
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16")
            .withDatabaseName(DATABASE_NAME)
            .withUsername("appointme")
            .withPassword("appointme");

    static {
        POSTGRES.start();
    }
}
