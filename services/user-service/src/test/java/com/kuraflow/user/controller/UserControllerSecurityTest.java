package com.kuraflow.user.controller;

import com.kuraflow.shared.security.JwtClaims;
import com.kuraflow.shared.security.SharedSecurityConfig;
import com.kuraflow.user.dto.PublicUserProfileResponse;
import com.kuraflow.user.dto.UserProfileResponse;
import com.kuraflow.user.exception.UserNotFoundException;
import com.kuraflow.user.service.UserService;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * HTTP-level checks through the real shared JWT filter chain: a user can only see or change their
 * own private profile.
 */
@WebMvcTest(controllers = UserController.class, properties = "jwt.secret=" + UserControllerSecurityTest.SECRET)
@Import(SharedSecurityConfig.class)
class UserControllerSecurityTest {

    static final String SECRET = "test-secret-that-is-at-least-32-bytes-long";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private UserService userService;

    private final UUID alice = UUID.randomUUID();
    private final UUID bob = UUID.randomUUID();

    @Test
    void aUserCannotPatchSomeoneElsesProfile() throws Exception {
        mockMvc.perform(patch("/api/users/{id}", bob)
                        .header("Authorization", bearer(alice, JwtClaims.ACCESS_TOKEN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"displayName\":\"Hacked\"}"))
                .andExpect(status().isMethodNotAllowed());
        verifyNoInteractions(userService);
    }

    @Test
    void publicProfilesDoNotExposeTheEmail() throws Exception {
        when(userService.getPublicProfile(bob))
                .thenReturn(new PublicUserProfileResponse(bob, "Bob", null, null, null, null, 3, 4));

        mockMvc.perform(get("/api/users/{id}", bob).header("Authorization", bearer(alice, JwtClaims.ACCESS_TOKEN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value("Bob"))
                .andExpect(jsonPath("$.email").doesNotExist());
    }

    @Test
    void unknownUsersAre404() throws Exception {
        when(userService.getPublicProfile(bob)).thenThrow(new UserNotFoundException(bob));

        mockMvc.perform(get("/api/users/{id}", bob).header("Authorization", bearer(alice, JwtClaims.ACCESS_TOKEN)))
                .andExpect(status().isNotFound());
    }

    @Test
    void theEmailLookupEndpointIsGone() throws Exception {
        mockMvc.perform(get("/api/users/email/{email}", "bob@kuraflow.com")
                        .header("Authorization", bearer(alice, JwtClaims.ACCESS_TOKEN)))
                .andExpect(status().isNotFound());
        verifyNoInteractions(userService);
    }

    @Test
    void ownProfileComesFromTheTokenNotFromTheUrl() throws Exception {
        when(userService.getOwnProfile(alice, "alice@kuraflow.dev"))
                .thenReturn(UserProfileResponse.builder().id(alice).email("alice@kuraflow.dev").build());

        mockMvc.perform(get("/api/users/me").header("Authorization", bearer(alice, JwtClaims.ACCESS_TOKEN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("alice@kuraflow.dev"));
    }

    @Test
    void requestsWithoutAnAccessTokenAreRejected() throws Exception {
        mockMvc.perform(get("/api/users/me")).andExpect(status().is4xxClientError());
        mockMvc.perform(get("/api/users/me").header("Authorization", bearer(alice, JwtClaims.REFRESH_TOKEN)))
                .andExpect(status().is4xxClientError());
        verifyNoInteractions(userService);
    }

    @Test
    void invalidProfileUpdatesAreRejected() throws Exception {
        mockMvc.perform(patch("/api/users/me")
                        .header("Authorization", bearer(alice, JwtClaims.ACCESS_TOKEN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"avatarUrl\":\"javascript:alert(1)\",\"displayName\":\"   \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.validationErrors.avatarUrl").exists())
                .andExpect(jsonPath("$.validationErrors.displayName").exists());
        verifyNoInteractions(userService);
    }

    @Test
    void malformedIdsAre400NotServerErrors() throws Exception {
        mockMvc.perform(get("/api/users/{id}", "not-a-uuid").header("Authorization", bearer(alice, JwtClaims.ACCESS_TOKEN)))
                .andExpect(status().isBadRequest());
    }

    /** Every request in this class is made by Alice. */
    private static String bearer(UUID aliceId, String tokenType) {
        return "Bearer " + Jwts.builder()
                .subject("alice@kuraflow.dev")
                .claim(JwtClaims.TOKEN_TYPE, tokenType)
                .claim(JwtClaims.USER_ID, aliceId.toString())
                .expiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)))
                .compact();
    }
}
