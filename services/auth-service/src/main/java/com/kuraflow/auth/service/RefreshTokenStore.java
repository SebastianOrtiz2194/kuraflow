package com.kuraflow.auth.service;

import java.time.Duration;

/**
 * Server-side state for refresh-token rotation. Each login creates a session whose only valid
 * refresh token is the most recently issued one.
 */
public interface RefreshTokenStore {

    /** Starts a session whose current refresh token is {@code tokenId}. */
    void create(String sessionId, String tokenId, Duration ttl);

    /**
     * Atomically replaces the session's current token with {@code nextTokenId}, but only if
     * {@code currentTokenId} is still the current one. Otherwise the token was already used or the
     * session was revoked: the whole session is revoked and {@code false} is returned.
     */
    boolean rotate(String sessionId, String currentTokenId, String nextTokenId, Duration ttl);

    /** Ends the session; its refresh tokens stop working. */
    void revoke(String sessionId);
}
