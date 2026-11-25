package com.kuraflow.shared.security;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Boots the auto-configured security chain the way a resource service gets it. Only the paths a
 * service declares are public: the old global list made /api/system/** and /api/content/** public
 * in every service.
 */
@SpringBootTest(
        classes = SharedSecurityConfigTest.TestApp.class,
        properties = {
                "jwt.secret=" + SharedSecurityConfigTest.SECRET,
                "kuraflow.security.public-paths[0]=/api/content/**"
        })
@AutoConfigureMockMvc
class SharedSecurityConfigTest {

    static final String SECRET = "test-secret-that-is-at-least-32-bytes-long";

    @Autowired
    private MockMvc mockMvc;

    @Test
    void declaredPublicPathsNeedNoToken() throws Exception {
        mockMvc.perform(get("/api/content/lessons")).andExpect(status().isOk());
    }

    @Test
    void everythingElseNeedsAnAccessToken() throws Exception {
        mockMvc.perform(get("/api/system/users")).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/progress")).andExpect(status().isForbidden());

        mockMvc.perform(get("/api/progress").header("Authorization", "Bearer " + accessToken()))
                .andExpect(status().isOk());
    }

    private static String accessToken() {
        return Jwts.builder()
                .subject("learner@kuraflow.dev")
                .claim(JwtClaims.TOKEN_TYPE, JwtClaims.ACCESS_TOKEN)
                .claim(JwtClaims.USER_ID, UUID.randomUUID().toString())
                .expiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)))
                .compact();
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @Import(Endpoints.class)
    static class TestApp {
    }

    @RestController
    static class Endpoints {

        @GetMapping({"/api/content/lessons", "/api/system/users", "/api/progress"})
        String ok() {
            return "ok";
        }
    }
}
