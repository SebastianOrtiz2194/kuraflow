package com.kuraflow.shared.security;

import io.jsonwebtoken.security.Keys;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;

/**
 * Builds the HMAC key every service uses to sign or verify JWTs from {@code jwt.secret}
 * (environment variable {@code JWT_SECRET}).
 *
 * <p>A missing or short secret is rejected so a misconfigured service fails at startup instead
 * of signing tokens with a guessable key. Error messages never include the secret itself.
 */
public final class JwtSecrets {

    /** HS256 requires a key of at least 256 bits. */
    public static final int MIN_SECRET_BYTES = 32;

    private JwtSecrets() {
    }

    public static SecretKey hmacKey(String secret) {
        if (secret == null || secret.isBlank()) {
            throw new InvalidJwtSecretException("The JWT secret (jwt.secret / JWT_SECRET) is not set.");
        }
        byte[] keyBytes = secret.getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length < MIN_SECRET_BYTES) {
            throw new InvalidJwtSecretException("The JWT secret (jwt.secret / JWT_SECRET) is " + keyBytes.length
                    + " bytes long; HS256 needs at least " + MIN_SECRET_BYTES + ".");
        }
        return Keys.hmacShaKeyFor(keyBytes);
    }
}
