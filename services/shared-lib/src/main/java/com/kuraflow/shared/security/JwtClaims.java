package com.kuraflow.shared.security;

/**
 * Claim names and values shared by the token issuer (auth-service) and every verifier.
 */
public final class JwtClaims {

    /** Kind of token: {@link #ACCESS_TOKEN} or {@link #REFRESH_TOKEN}. */
    public static final String TOKEN_TYPE = "typ";
    public static final String ACCESS_TOKEN = "access";
    public static final String REFRESH_TOKEN = "refresh";

    /** The user's UUID; present on access tokens only. */
    public static final String USER_ID = "userId";

    /** Refresh tokens: the login session ("token family") the token belongs to. */
    public static final String SESSION_ID = "sid";

    private JwtClaims() {
    }
}
