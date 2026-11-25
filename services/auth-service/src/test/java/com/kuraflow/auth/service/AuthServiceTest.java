package com.kuraflow.auth.service;

import com.kuraflow.auth.dto.AuthResponse;
import com.kuraflow.auth.dto.LoginRequest;
import com.kuraflow.auth.dto.RegisterRequest;
import com.kuraflow.auth.entity.UserCredential;
import com.kuraflow.auth.exception.InvalidTokenException;
import com.kuraflow.auth.repository.UserCredentialRepository;
import com.kuraflow.auth.util.JwtUtils;
import com.kuraflow.auth.util.JwtUtils.RefreshTokenClaims;
import com.kuraflow.shared.security.CustomUserDetails;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    private static final String SECRET = "404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970";
    private static final Duration REFRESH_TTL = Duration.ofSeconds(604800);

    @Mock
    private UserCredentialRepository userCredentialRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private AuthenticationManager authenticationManager;
    @Mock
    private UserDetailsService userDetailsService;
    @Mock
    private RefreshTokenStore refreshTokenStore;
    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;

    private final JwtUtils jwtUtils = new JwtUtils(SECRET, 3600, REFRESH_TTL.toSeconds());
    private final CustomUserDetails user = new CustomUserDetails(UUID.randomUUID(), "user@kuraflow.com", "hashed");

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(userCredentialRepository, passwordEncoder, jwtUtils, authenticationManager,
                userDetailsService, refreshTokenStore, kafkaTemplate);
        ReflectionTestUtils.setField(authService, "userRegisteredTopic", "user.registered");
    }

    @Test
    @DisplayName("Register: saves the credential, emits the Kafka event, and starts a refresh session")
    void register_Success() {
        RegisterRequest request = new RegisterRequest("test@kuraflow.com", "Password123!", "Test User");
        when(userCredentialRepository.existsByEmail("test@kuraflow.com")).thenReturn(false);
        when(passwordEncoder.encode("Password123!")).thenReturn("hashedPassword");
        when(userCredentialRepository.save(any(UserCredential.class))).thenAnswer(i -> i.getArgument(0));

        AuthResponse response = authService.register(request);

        assertThat(response.getEmail()).isEqualTo("test@kuraflow.com");
        assertThat(response.getAccessToken()).isNotBlank();
        RefreshTokenClaims refresh = jwtUtils.parseRefreshToken(response.getRefreshToken());
        verify(refreshTokenStore).create(refresh.sessionId(), refresh.tokenId(), REFRESH_TTL);
        verify(userCredentialRepository).save(any(UserCredential.class));
        verify(kafkaTemplate).send(eq("user.registered"), anyString(), any());
    }

    @Test
    @DisplayName("Register: throws IllegalArgumentException if email already registered")
    void register_EmailAlreadyExists() {
        RegisterRequest request = new RegisterRequest("existing@kuraflow.com", "Password123!", "Existing User");
        when(userCredentialRepository.existsByEmail("existing@kuraflow.com")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Email already in use");

        verify(userCredentialRepository, never()).save(any());
        verify(kafkaTemplate, never()).send(anyString(), anyString(), any());
        verifyNoInteractions(refreshTokenStore);
    }

    @Test
    @DisplayName("Login: uses the authenticated principal (no second lookup) and starts a refresh session")
    void login_Success() {
        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenReturn(new UsernamePasswordAuthenticationToken(user, null, List.of()));

        AuthResponse response = authService.login(new LoginRequest("user@kuraflow.com", "Password123!"));

        RefreshTokenClaims refresh = jwtUtils.parseRefreshToken(response.getRefreshToken());
        assertThat(refresh.email()).isEqualTo("user@kuraflow.com");
        verify(refreshTokenStore).create(refresh.sessionId(), refresh.tokenId(), REFRESH_TTL);
        verifyNoInteractions(userDetailsService);
    }

    @Test
    @DisplayName("Login: throws BadCredentialsException when authentication fails")
    void login_InvalidCredentials() {
        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenThrow(new BadCredentialsException("Bad credentials"));

        assertThatThrownBy(() -> authService.login(new LoginRequest("wrong@kuraflow.com", "WrongPassword")))
                .isInstanceOf(BadCredentialsException.class);
        verifyNoInteractions(refreshTokenStore);
    }

    @Test
    @DisplayName("Refresh: rotates the refresh token within the same session")
    void refresh_RotatesTheRefreshToken() {
        String oldToken = jwtUtils.generateRefreshToken("user@kuraflow.com", "session-1", "token-1");
        when(userDetailsService.loadUserByUsername("user@kuraflow.com")).thenReturn(user);
        when(refreshTokenStore.rotate(eq("session-1"), eq("token-1"), anyString(), eq(REFRESH_TTL))).thenReturn(true);

        AuthResponse response = authService.refresh(oldToken);

        ArgumentCaptor<String> nextTokenId = ArgumentCaptor.forClass(String.class);
        verify(refreshTokenStore).rotate(eq("session-1"), eq("token-1"), nextTokenId.capture(), eq(REFRESH_TTL));
        RefreshTokenClaims rotated = jwtUtils.parseRefreshToken(response.getRefreshToken());
        assertThat(rotated.sessionId()).isEqualTo("session-1");
        assertThat(rotated.tokenId()).isEqualTo(nextTokenId.getValue()).isNotEqualTo("token-1");
        assertThat(response.getAccessToken()).isNotBlank();
    }

    @Test
    @DisplayName("Refresh: an access token can't be used as a refresh token")
    void refresh_RejectsAccessTokens() {
        String accessToken = jwtUtils.generateAccessToken(user.getId(), user.getEmail());

        assertThatThrownBy(() -> authService.refresh(accessToken)).isInstanceOf(InvalidTokenException.class);
        verifyNoInteractions(refreshTokenStore, userDetailsService);
    }

    @Test
    @DisplayName("Refresh: reusing an already-rotated refresh token fails (the store revokes the session)")
    void refresh_ReusedTokenIsRejected() {
        String reused = jwtUtils.generateRefreshToken("user@kuraflow.com", "session-1", "token-1");
        when(userDetailsService.loadUserByUsername("user@kuraflow.com")).thenReturn(user);
        when(refreshTokenStore.rotate(eq("session-1"), eq("token-1"), anyString(), eq(REFRESH_TTL))).thenReturn(false);

        assertThatThrownBy(() -> authService.refresh(reused))
                .isInstanceOf(InvalidTokenException.class)
                .hasMessageContaining("revoked");
    }

    @Test
    @DisplayName("Refresh: a deleted user gets 401 and the session is revoked")
    void refresh_DeletedUser() {
        String token = jwtUtils.generateRefreshToken("gone@kuraflow.com", "session-1", "token-1");
        when(userDetailsService.loadUserByUsername("gone@kuraflow.com"))
                .thenThrow(new UsernameNotFoundException("gone"));

        assertThatThrownBy(() -> authService.refresh(token)).isInstanceOf(InvalidTokenException.class);
        verify(refreshTokenStore).revoke("session-1");
        verify(refreshTokenStore, never()).rotate(anyString(), anyString(), anyString(), any());
    }

    @Test
    @DisplayName("Logout: revokes the session behind the refresh token")
    void logout_RevokesSession() {
        authService.logout(jwtUtils.generateRefreshToken("user@kuraflow.com", "session-1", "token-1"));

        verify(refreshTokenStore).revoke("session-1");
    }

    @Test
    @DisplayName("Logout: ignores invalid tokens")
    void logout_IgnoresInvalidTokens() {
        authService.logout("not-a-jwt");

        verifyNoInteractions(refreshTokenStore);
    }
}
