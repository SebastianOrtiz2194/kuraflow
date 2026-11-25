package com.kuraflow.gateway.util;

import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtUtilTest {

    private static final String SECRET = "test-secret-that-is-at-least-32-bytes-long";

    private final JwtUtil jwtUtil = new JwtUtil(SECRET);

    @Test
    void refusesToStartWithoutAStrongSecret() {
        assertThatThrownBy(() -> new JwtUtil(""))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not set");
        assertThatThrownBy(() -> new JwtUtil("your_jwt_secret"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("15 bytes")
                .hasMessageNotContaining("your_jwt_secret");
    }

    @Test
    void acceptsAccessTokens() {
        assertThat(jwtUtil.validateAccessToken(token("access", 60_000)).getSubject())
                .isEqualTo("learner@kuraflow.dev");
    }

    @Test
    void rejectsRefreshTokensAndTokensWithoutAType() {
        assertThatThrownBy(() -> jwtUtil.validateAccessToken(token("refresh", 60_000)))
                .isInstanceOf(JwtException.class);
        assertThatThrownBy(() -> jwtUtil.validateAccessToken(token(null, 60_000)))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void rejectsExpiredTokens() {
        assertThatThrownBy(() -> jwtUtil.validateAccessToken(token("access", -60_000)))
                .isInstanceOf(JwtException.class);
    }

    private static String token(String type, long ttlMillis) {
        var builder = Jwts.builder()
                .subject("learner@kuraflow.dev")
                .expiration(new Date(System.currentTimeMillis() + ttlMillis))
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)));
        if (type != null) {
            builder.claim(JwtUtil.TOKEN_TYPE_CLAIM, type);
        }
        return builder.compact();
    }
}
