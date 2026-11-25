package com.kuraflow.shared.security;

import org.springframework.boot.diagnostics.AbstractFailureAnalyzer;
import org.springframework.boot.diagnostics.FailureAnalysis;

/**
 * Turns {@link InvalidJwtSecretException} into a short "APPLICATION FAILED TO START" report
 * instead of a long bean-creation stack trace.
 */
class InvalidJwtSecretFailureAnalyzer extends AbstractFailureAnalyzer<InvalidJwtSecretException> {

    @Override
    protected FailureAnalysis analyze(Throwable rootFailure, InvalidJwtSecretException cause) {
        return new FailureAnalysis(
                cause.getMessage(),
                "Set the JWT_SECRET environment variable to the same random value, at least "
                        + JwtSecrets.MIN_SECRET_BYTES + " bytes long, in every service."
                        + " Generate one with: openssl rand -base64 48",
                cause);
    }
}
