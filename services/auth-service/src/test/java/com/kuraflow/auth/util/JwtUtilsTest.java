package com.kuraflow.auth.util;

import com.kuraflow.auth.exception.InvalidTokenException;
import com.kuraflow.auth.util.JwtUtils.RefreshTokenClaims;
import com.kuraflow.shared.security.CustomUserDetails;
import com.kuraflow.shared.security.InvalidJwtSecretException;
import com.kuraflow.shared.security.JwtAuthenticationFilter;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class JwtUtilsTest {

    private static final String SECRET = "404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970";

    private final JwtUtils jwtUtils = new JwtUtils(SECRET, 3600, 604800);

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void refusesToStartWithoutASecret() {
        assertThatThrownBy(() -> new JwtUtils("", 3600, 604800))
                .isInstanceOf(InvalidJwtSecretException.class);
    }

    @Test
    void accessTokensAreAcceptedByTheSharedFilter() throws Exception {
        UUID userId = UUID.randomUUID();
        String accessToken = jwtUtils.generateAccessToken(userId, "test@kuraflow.com");

        Authentication authentication = authenticateThroughSharedFilter(accessToken);

        assertThat(authentication).isNotNull();
        assertThat(((CustomUserDetails) authentication.getPrincipal()).getId()).isEqualTo(userId);
    }

    @Test
    void refreshTokensAreRejectedByTheSharedFilter() throws Exception {
        String refreshToken = jwtUtils.generateRefreshToken("test@kuraflow.com", "session-1", "token-1");

        assertThat(authenticateThroughSharedFilter(refreshToken)).isNull();
    }

    @Test
    void parsesRefreshTokens() {
        String refreshToken = jwtUtils.generateRefreshToken("refresh@kuraflow.com", "session-1", "token-1");

        RefreshTokenClaims claims = jwtUtils.parseRefreshToken(refreshToken);

        assertThat(claims).isEqualTo(new RefreshTokenClaims("refresh@kuraflow.com", "session-1", "token-1"));
    }

    @Test
    void anAccessTokenIsNotARefreshToken() {
        String accessToken = jwtUtils.generateAccessToken(UUID.randomUUID(), "test@kuraflow.com");

        assertThatThrownBy(() -> jwtUtils.parseRefreshToken(accessToken))
                .isInstanceOf(InvalidTokenException.class);
    }

    @Test
    void rejectsExpiredForgedAndMalformedRefreshTokens() {
        String expired = new JwtUtils(SECRET, 3600, -60).generateRefreshToken("a@kuraflow.com", "s", "t");
        String forged = new JwtUtils("another-secret-that-is-at-least-32-bytes", 3600, 604800)
                .generateRefreshToken("a@kuraflow.com", "s", "t");

        assertThatThrownBy(() -> jwtUtils.parseRefreshToken(expired)).isInstanceOf(InvalidTokenException.class);
        assertThatThrownBy(() -> jwtUtils.parseRefreshToken(forged)).isInstanceOf(InvalidTokenException.class);
        assertThatThrownBy(() -> jwtUtils.parseRefreshToken("not-a-jwt")).isInstanceOf(InvalidTokenException.class);
    }

    private static Authentication authenticateThroughSharedFilter(String token) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/progress");
        request.addHeader("Authorization", "Bearer " + token);
        new JwtAuthenticationFilter(SECRET).doFilter(request, new MockHttpServletResponse(), mock(FilterChain.class));
        return SecurityContextHolder.getContext().getAuthentication();
    }
}
