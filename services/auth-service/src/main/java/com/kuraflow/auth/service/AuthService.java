package com.kuraflow.auth.service;

import com.kuraflow.auth.dto.AuthResponse;
import com.kuraflow.auth.dto.LoginRequest;
import com.kuraflow.auth.dto.RegisterRequest;
import com.kuraflow.auth.entity.UserCredential;
import com.kuraflow.auth.exception.InvalidTokenException;
import com.kuraflow.auth.repository.UserCredentialRepository;
import com.kuraflow.auth.util.JwtUtils;
import com.kuraflow.auth.util.JwtUtils.RefreshTokenClaims;
import com.kuraflow.shared.event.UserRegisteredEvent;
import com.kuraflow.shared.security.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserCredentialRepository userCredentialRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtils jwtUtils;
    private final AuthenticationManager authenticationManager;
    private final UserDetailsService userDetailsService;
    private final RefreshTokenStore refreshTokenStore;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Value("${app.kafka.topics.user-registered:user.registered}")
    private String userRegisteredTopic;

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (userCredentialRepository.existsByEmail(request.getEmail())) {
            throw new IllegalArgumentException("Email already in use: " + request.getEmail());
        }

        UUID userId = UUID.randomUUID();
        UserCredential credential = UserCredential.builder()
                .id(userId)
                .email(request.getEmail())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .displayName(request.getDisplayName())
                .authProvider("local")
                .build();

        userCredentialRepository.save(credential);

        // Publish event for user-service and downstream consumers
        try {
            UserRegisteredEvent event = UserRegisteredEvent.builder()
                    .userId(userId)
                    .email(credential.getEmail())
                    .displayName(credential.getDisplayName())
                    .authProvider(credential.getAuthProvider())
                    .timestamp(Instant.now())
                    .build();
            kafkaTemplate.send(userRegisteredTopic, userId.toString(), event);
            log.info("Published UserRegisteredEvent for user: {}", userId);
        } catch (Exception e) {
            log.error("Failed to publish UserRegisteredEvent for user: {}", userId, e);
        }

        return startSession(new CustomUserDetails(userId, credential.getEmail(), credential.getPasswordHash()));
    }

    public AuthResponse login(LoginRequest request) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getEmail(), request.getPassword())
        );
        return startSession(asCustomUser(authentication.getPrincipal()));
    }

    /**
     * Exchanges a refresh token for a new access token and a new refresh token (rotation).
     * Presenting a refresh token that was already exchanged revokes the whole session.
     */
    public AuthResponse refresh(String refreshToken) {
        RefreshTokenClaims claims = jwtUtils.parseRefreshToken(refreshToken);

        CustomUserDetails user;
        try {
            user = asCustomUser(userDetailsService.loadUserByUsername(claims.email()));
        } catch (UsernameNotFoundException e) {
            refreshTokenStore.revoke(claims.sessionId());
            throw new InvalidTokenException("Refresh token is invalid or has been revoked");
        }

        String nextTokenId = UUID.randomUUID().toString();
        if (!refreshTokenStore.rotate(claims.sessionId(), claims.tokenId(), nextTokenId, jwtUtils.refreshTokenTtl())) {
            log.warn("Rejected a reused or revoked refresh token; session {} is revoked", claims.sessionId());
            throw new InvalidTokenException("Refresh token is invalid or has been revoked");
        }

        return AuthResponse.builder()
                .accessToken(jwtUtils.generateAccessToken(user.getId(), user.getEmail()))
                .refreshToken(jwtUtils.generateRefreshToken(user.getEmail(), claims.sessionId(), nextTokenId))
                .email(user.getEmail())
                .build();
    }

    /** Revokes the session behind a refresh token. Invalid or expired tokens are ignored. */
    public void logout(String refreshToken) {
        try {
            refreshTokenStore.revoke(jwtUtils.parseRefreshToken(refreshToken).sessionId());
        } catch (InvalidTokenException e) {
            log.debug("Logout with an invalid or expired refresh token: nothing to revoke");
        }
    }

    private AuthResponse startSession(CustomUserDetails user) {
        String sessionId = UUID.randomUUID().toString();
        String tokenId = UUID.randomUUID().toString();
        refreshTokenStore.create(sessionId, tokenId, jwtUtils.refreshTokenTtl());

        return AuthResponse.builder()
                .accessToken(jwtUtils.generateAccessToken(user.getId(), user.getEmail()))
                .refreshToken(jwtUtils.generateRefreshToken(user.getEmail(), sessionId, tokenId))
                .email(user.getEmail())
                .build();
    }

    private static CustomUserDetails asCustomUser(Object principal) {
        if (principal instanceof CustomUserDetails user) {
            return user;
        }
        throw new IllegalStateException("Unexpected principal type: "
                + (principal == null ? "null" : principal.getClass().getName()));
    }
}
