package com.kastellosth.appointme.exception;

import java.sql.SQLException;

/**
 * Helpers for reading the reason behind a database failure.
 * Pure Java (no Spring), so it is easy to unit test.
 */
public final class DatabaseErrors {

    /** Shown to the client when two bookings would overlap. */
    public static final String SLOT_TAKEN_MESSAGE = "That time slot is already booked for this staff member";

    /** PostgreSQL SQLState for "exclusion_violation" (our appointments_no_overlap constraint). */
    private static final String EXCLUSION_VIOLATION = "23P01";

    private DatabaseErrors() {
    }

    /**
     * Walks the cause chain (Spring wraps Hibernate wraps the JDBC driver) and returns true
     * if any link is an SQLException with SQLState 23P01.
     */
    public static boolean isExclusionViolation(Throwable error) {
        Throwable current = error;
        for (int depth = 0; current != null && depth < 20; depth++) {
            if (current instanceof SQLException sql && EXCLUSION_VIOLATION.equals(sql.getSQLState())) {
                return true;
            }
            current = (current.getCause() == current) ? null : current.getCause();
        }
        return false;
    }
}
