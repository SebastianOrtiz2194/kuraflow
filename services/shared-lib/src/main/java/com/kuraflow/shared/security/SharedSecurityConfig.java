package com.kuraflow.shared.security;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Stateless JWT security for the resource services. Everything requires an access token except
 * infrastructure endpoints and the paths a service lists in {@code kuraflow.security.public-paths}.
 */
@Configuration
@EnableWebSecurity
@EnableConfigurationProperties(SharedSecurityProperties.class)
@ComponentScan(basePackages = "com.kuraflow.shared.security")
public class SharedSecurityConfig {

    /** Served without a token by every service. Other actuator endpoints are not public. */
    static final String[] ALWAYS_PUBLIC = {
            "/error",
            "/actuator/health", "/actuator/health/**", "/actuator/info",
            "/v3/api-docs/**", "/api-docs/**", "/swagger-ui/**", "/swagger-ui.html"
    };

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final SharedSecurityProperties properties;

    public SharedSecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter, SharedSecurityProperties properties) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.properties = properties;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http.csrf(csrf -> csrf.disable())
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> {
                auth.requestMatchers(ALWAYS_PUBLIC).permitAll();
                if (!properties.publicPaths().isEmpty()) {
                    auth.requestMatchers(properties.publicPaths().toArray(String[]::new)).permitAll();
                }
                auth.anyRequest().authenticated();
            })
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
