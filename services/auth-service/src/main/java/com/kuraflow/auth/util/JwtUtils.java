package com.kuraflow.auth.util;

import com.kuraflow.auth.exception.InvalidTokenException;
import com.kuraflow.shared.security.JwtClaims;
import com.kuraflow.shared.security.JwtSecrets;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.Jwts;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

/**
 * Issues and parses KuraFlow JWTs.
 * <ul>
 *   <li>Access tokens ({@code typ=access}) carry the user id and authorize API calls.</li>
 *   <li>Refresh tokens ({@code typ=refresh}) carry a session id ({@code sid}) and a token id
 *       ({@code jti}); only the session's current token id is accepted (see {@code RefreshTokenStore}).</li>
 * </ul>
 */
@Component
public class JwtUtils {

    private final SecretKey signingKey;
    private final JwtParser parser;
    private final Duration accessTokenTtl;
    private final Duration refreshTokenTtl;

    public JwtUtils(@Value("${jwt.secret:}") String secret,
                    @Value("${jwt.expiration:3600}") long accessTokenTtlSeconds,
                    @Value("${jwt.refreshExpiration:604800}") long refreshTokenTtlSeconds) {
        this.signingKey = JwtSecrets.hmacKey(secret);
        this.parser = Jwts.parser().verifyWith(signingKey).build();
        this.accessTokenTtl = Duration.ofSeconds(accessTokenTtlSeconds);
        this.refreshTokenTtl = Duration.ofSeconds(refreshTokenTtlSeconds);
    }

    public String generateAccessToken(UUID userId, String email) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(email)
                .claim(JwtClaims.TOKEN_TYPE, JwtClaims.ACCESS_TOKEN)
                .claim(JwtClaims.USER_ID, userId.toString())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(accessTokenTtl)))
                .signWith(signingKey)
                .compact();
    }

    public String generateRefreshToken(String email, String sessionId, String tokenId) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(email)
                .id(tokenId)
                .claim(JwtClaims.TOKEN_TYPE, JwtClaims.REFRESH_TOKEN)
                .claim(JwtClaims.SESSION_ID, sessionId)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(refreshTokenTtl)))
                .signWith(signingKey)
                .compact();
    }

    /**
     * Checks a refresh token's signature, expiry and type. Whether it is still the session's
     * current token is decided by the {@code RefreshTokenStore}.
     *
     * @throws InvalidTokenException if the token is malformed, expired, forged, or not a refresh token
     */
    public RefreshTokenClaims parseRefreshToken(String token) {
        try {
            Claims claims = parser.parseSignedClaims(token).getPayload();
            String type = claims.get(JwtClaims.TOKEN_TYPE, String.class);
            String sessionId = claims.get(JwtClaims.SESSION_ID, String.class);
            if (!JwtClaims.REFRESH_TOKEN.equals(type) || claims.getSubject() == null
                    || sessionId == null || claims.getId() == null) {
                throw new InvalidTokenException("Not a refresh token");
            }
            return new RefreshTokenClaims(claims.getSubject(), sessionId, claims.getId());
        } catch (JwtException | IllegalArgumentException e) {
            throw new InvalidTokenException("Refresh token is invalid or expired", e);
        }
    }

    public Duration refreshTokenTtl() {
        return refreshTokenTtl;
    }

    public record RefreshTokenClaims(String email, String sessionId, String tokenId) {
    }
}
