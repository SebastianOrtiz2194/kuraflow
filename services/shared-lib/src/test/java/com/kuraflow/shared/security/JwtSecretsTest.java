package com.kuraflow.shared.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.BeanCreationException;
import org.springframework.boot.diagnostics.FailureAnalysis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtSecretsTest {

    @Test
    void rejectsMissingSecret() {
        assertThatThrownBy(() -> JwtSecrets.hmacKey(null))
                .isInstanceOf(InvalidJwtSecretException.class)
                .hasMessageContaining("not set");
        assertThatThrownBy(() -> JwtSecrets.hmacKey("   "))
                .isInstanceOf(InvalidJwtSecretException.class)
                .hasMessageContaining("not set");
    }

    @Test
    void rejectsSecretShorterThan32BytesWithoutEchoingIt() {
        // The placeholder that .env.example used to ship with.
        assertThatThrownBy(() -> JwtSecrets.hmacKey("your_jwt_secret"))
                .isInstanceOf(InvalidJwtSecretException.class)
                .hasMessageContaining("15 bytes")
                .hasMessageNotContaining("your_jwt_secret");
    }

    @Test
    void acceptsSecretOf32Bytes() {
        assertThat(JwtSecrets.hmacKey("0123456789abcdef0123456789abcdef")).isNotNull();
    }

    @Test
    void failureAnalyzerExplainsHowToFixIt() {
        BeanCreationException startupFailure = new BeanCreationException("jwtAuthenticationFilter",
                "Failed to instantiate", new InvalidJwtSecretException("The JWT secret (jwt.secret / JWT_SECRET) is not set."));

        FailureAnalysis analysis = new InvalidJwtSecretFailureAnalyzer().analyze(startupFailure);

        assertThat(analysis.getDescription()).contains("is not set");
        assertThat(analysis.getAction()).contains("JWT_SECRET").contains("openssl rand -base64 48");
    }
}
