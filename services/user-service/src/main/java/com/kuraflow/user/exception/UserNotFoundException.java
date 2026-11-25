package com.kuraflow.user.exception;

import java.util.UUID;

/** Mapped to 404 by {@link GlobalExceptionHandler}. */
public class UserNotFoundException extends RuntimeException {

    public UserNotFoundException(UUID userId) {
        super("User not found: " + userId);
    }
}
