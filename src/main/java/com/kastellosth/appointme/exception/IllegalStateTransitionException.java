package com.kastellosth.appointme.exception;

/**
 * "That status change is not allowed from the current status."
 * It extends ConflictException, so the handler you already have answers it with 409.
 */
public class IllegalStateTransitionException extends ConflictException {

    public IllegalStateTransitionException(String message) {
        super(message);
    }
}
