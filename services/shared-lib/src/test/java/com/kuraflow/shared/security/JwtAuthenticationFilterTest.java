package com.kuraflow.shared.security;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class JwtAuthenticationFilterTest {

    private static final String SECRET = "test-secret-that-is-at-least-32-bytes-long";

    private final JwtAuthenticationFilter filter = new JwtAuthenticationFilter(SECRET);
    private final FilterChain chain = mock(FilterChain.class);

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void refusesToStartWithoutASecret() {
        assertThatThrownBy(() -> new JwtAuthenticationFilter(""))
                .isInstanceOf(InvalidJwtSecretException.class);
    }

    @Test
    void authenticatesAValidAccessToken() throws Exception {
        UUID userId = UUID.randomUUID();
        String token = token(SECRET, JwtClaims.ACCESS_TOKEN, userId, 60_000);

        filter.doFilter(requestWith(token), new MockHttpServletResponse(), chain);

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        assertThat(authentication).isNotNull();
        assertThat(((CustomUserDetails) authentication.getPrincipal()).getId()).isEqualTo(userId);
        verify(chain, times(1)).doFilter(any(), any());
    }

    @Test
    void ignoresRefreshTokens() throws Exception {
        String token = token(SECRET, JwtClaims.REFRESH_TOKEN, UUID.randomUUID(), 60_000);

        filter.doFilter(requestWith(token), new MockHttpServletResponse(), chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(chain, times(1)).doFilter(any(), any());
    }

    @Test
    void ignoresTokensWithoutATypeClaim() throws Exception {
        String token = token(SECRET, null, UUID.randomUUID(), 60_000);

        filter.doFilter(requestWith(token), new MockHttpServletResponse(), chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void ignoresExpiredAndForeignTokens() throws Exception {
        String expired = token(SECRET, JwtClaims.ACCESS_TOKEN, UUID.randomUUID(), -60_000);
        String foreign = token("another-secret-that-is-at-least-32-bytes", JwtClaims.ACCESS_TOKEN, UUID.randomUUID(), 60_000);

        filter.doFilter(requestWith(expired), new MockHttpServletResponse(), chain);
        filter.doFilter(requestWith(foreign), new MockHttpServletResponse(), chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(chain, times(2)).doFilter(any(), any());
    }

    @Test
    void doesNotSwallowDownstreamFailuresOrRunTheChainTwice() throws Exception {
        String token = token(SECRET, JwtClaims.ACCESS_TOKEN, UUID.randomUUID(), 60_000);
        doThrow(new ServletException("downstream failure")).when(chain).doFilter(any(), any());

        assertThatThrownBy(() -> filter.doFilter(requestWith(token), new MockHttpServletResponse(), chain))
                .isInstanceOf(ServletException.class)
                .hasMessage("downstream failure");
        verify(chain, times(1)).doFilter(any(), any());
    }

    private static MockHttpServletRequest requestWith(String token) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/progress");
        request.addHeader("Authorization", "Bearer " + token);
        return request;
    }

    private static String token(String secret, String type, UUID userId, long ttlMillis) {
        var builder = Jwts.builder()
                .subject("learner@kuraflow.dev")
                .claim(JwtClaims.USER_ID, userId.toString())
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + ttlMillis))
                .signWith(Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8)));
        if (type != null) {
            builder.claim(JwtClaims.TOKEN_TYPE, type);
        }
        return builder.compact();
    }
}
