package com.kastellosth.appointme.exception;

public class IllegalStateTransitionException extends ConflictException {

    public IllegalStateTransitionException(String message) {
        super(message);
    }
}
