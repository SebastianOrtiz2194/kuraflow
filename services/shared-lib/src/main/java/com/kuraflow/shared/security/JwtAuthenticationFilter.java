package com.kuraflow.shared.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.Jwts;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;
import java.util.UUID;

@Slf4j
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtParser jwtParser;

    public JwtAuthenticationFilter(@Value("${jwt.secret:}") String secret) {
        this.jwtParser = Jwts.parser().verifyWith(JwtSecrets.hmacKey(secret)).build();
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith(BEARER_PREFIX)
                && SecurityContextHolder.getContext().getAuthentication() == null) {
            authenticate(authHeader.substring(BEARER_PREFIX.length()), request);
        }
        filterChain.doFilter(request, response);
    }

    private void authenticate(String token, HttpServletRequest request) {
        try {
            Claims claims = jwtParser.parseSignedClaims(token).getPayload();

            // Refresh tokens are signed with the same key; they must never authenticate an API call.
            if (!JwtClaims.ACCESS_TOKEN.equals(claims.get(JwtClaims.TOKEN_TYPE, String.class))) {
                log.debug("Ignoring a JWT that is not an access token");
                return;
            }

            String email = claims.getSubject();
            String userIdStr = claims.get(JwtClaims.USER_ID, String.class);
            if (email == null || userIdStr == null) {
                return;
            }

            UUID userId = UUID.fromString(userIdStr);
            CustomUserDetails userDetails = new CustomUserDetails(userId, email, "");
            UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                    userDetails, null, Collections.emptyList()
            );
            authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            SecurityContextHolder.getContext().setAuthentication(authentication);
            log.debug("Successfully authenticated user {} ({})", userId, email);
        } catch (JwtException | IllegalArgumentException e) {
            // Expired or tampered tokens are client errors: the request continues unauthenticated (401/403).
            log.debug("Ignoring invalid JWT: {}", e.getMessage());
        }
    }
}
