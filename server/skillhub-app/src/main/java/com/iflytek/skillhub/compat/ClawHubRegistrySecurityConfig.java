package com.iflytek.skillhub.compat;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Declares a dedicated stateless security chain for public compatibility endpoints used by
 * registry-style clients.
 */
@Configuration
public class ClawHubRegistrySecurityConfig {

    @Bean
    @Order(0)
    public SecurityFilterChain clawHubRegistryFilterChain(HttpSecurity http) throws Exception {
        http
                .securityMatcher("/api/v1/search", "/api/v1/download", "/api/v1/skills/*")
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                .requestCache(cache -> cache.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS));

        return http.build();
    }
}
