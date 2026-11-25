package com.kuraflow.auth.exception;

/**
 * A refresh token that is malformed, expired, forged, of the wrong type, already used, or revoked.
 * Mapped to 401 by {@link GlobalExceptionHandler}.
 */
public class InvalidTokenException extends RuntimeException {

    public InvalidTokenException(String message) {
        super(message);
    }

    public InvalidTokenException(String message, Throwable cause) {
        super(message, cause);
    }
}
