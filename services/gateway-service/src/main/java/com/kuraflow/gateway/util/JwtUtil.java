package com.kuraflow.gateway.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/**
 * Verifies access tokens at the edge. Mirrors {@code JwtSecrets}/{@code JwtClaims} in shared-lib,
 * which this reactive service can't depend on (shared-lib brings in the servlet stack).
 */
@Component
public class JwtUtil {

    /** HS256 requires a key of at least 256 bits. */
    static final int MIN_SECRET_BYTES = 32;
    static final String TOKEN_TYPE_CLAIM = "typ";
    static final String ACCESS_TOKEN = "access";

    private static final String HOW_TO_FIX = "Set JWT_SECRET to the same random value, at least "
            + MIN_SECRET_BYTES + " bytes long, in every service (openssl rand -base64 48).";

    private final JwtParser jwtParser;

    public JwtUtil(@Value("${jwt.secret:}") String secret) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException("The JWT secret (jwt.secret / JWT_SECRET) is not set. " + HOW_TO_FIX);
        }
        byte[] keyBytes = secret.getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length < MIN_SECRET_BYTES) {
            throw new IllegalStateException("The JWT secret (jwt.secret / JWT_SECRET) is " + keyBytes.length
                    + " bytes long; HS256 needs at least " + MIN_SECRET_BYTES + ". " + HOW_TO_FIX);
        }
        this.jwtParser = Jwts.parser().verifyWith(Keys.hmacShaKeyFor(keyBytes)).build();
    }

    /**
     * Checks the signature and expiry, and that the token is an access token: refresh tokens are
     * signed with the same key but must not authorize API calls.
     *
     * @throws JwtException if the token is invalid, expired, or not an access token
     */
    public Claims validateAccessToken(String token) {
        Claims claims = jwtParser.parseSignedClaims(token).getPayload();
        if (!ACCESS_TOKEN.equals(claims.get(TOKEN_TYPE_CLAIM, String.class))) {
            throw new JwtException("Not an access token");
        }
        return claims;
    }
}
