package com.kuraflow.shared.security;

/**
 * Thrown at startup when {@code jwt.secret} is missing or too weak to sign tokens safely.
 */
public class InvalidJwtSecretException extends IllegalStateException {

    public InvalidJwtSecretException(String message) {
        super(message);
    }
}
