package com.kastellosth.appointme.exception;

/**
 * 422: the request is well-formed, but cannot be processed as sent.
 * Used when an Idempotency-Key is reused with a DIFFERENT request: that is a client bug,
 * and neither "replay the old result" nor "do the new request" would be correct.
 */
public class UnprocessableException extends RuntimeException {
    public UnprocessableException(String message) {
        super(message);
    }
}
