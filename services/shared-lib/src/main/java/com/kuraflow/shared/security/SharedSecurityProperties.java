package com.kuraflow.shared.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * Per-service security settings, e.g. in content-service:
 * <pre>
 * kuraflow:
 *   security:
 *     public-paths:
 *       - /api/content/**
 * </pre>
 *
 * @param publicPaths request patterns this service serves without a token, in addition to
 *                    health checks, API docs and the error page
 */
@ConfigurationProperties(prefix = "kuraflow.security")
public record SharedSecurityProperties(List<String> publicPaths) {

    public SharedSecurityProperties {
        publicPaths = publicPaths == null ? List.of() : List.copyOf(publicPaths);
    }
}
