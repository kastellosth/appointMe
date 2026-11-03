package com.kastellosth.appointme.exception;

import java.sql.SQLException;


public final class DatabaseErrors {

    public static final String SLOT_TAKEN_MESSAGE = "That time slot is already booked for this staff member";

    public static final String SLOT_CONTENDED_MESSAGE =
            "That time slot is being booked by someone else. Please try again.";

    private static final String EXCLUSION_VIOLATION = "23P01";

    private static final String DEADLOCK_DETECTED = "40P01";

    private DatabaseErrors() {
    }

    public static boolean isExclusionViolation(Throwable error) {
        return hasSqlState(error, EXCLUSION_VIOLATION);
    }

    public static boolean isDeadlock(Throwable error) {
        return hasSqlState(error, DEADLOCK_DETECTED);
    }


    private static boolean hasSqlState(Throwable error, String sqlState) {
        Throwable current = error;
        for (int depth = 0; current != null && depth < 20; depth++) {
            if (current instanceof SQLException sql && sqlState.equals(sql.getSQLState())) {
                return true;
            }
            current = (current.getCause() == current) ? null : current.getCause();
        }
        return false;
    }
}