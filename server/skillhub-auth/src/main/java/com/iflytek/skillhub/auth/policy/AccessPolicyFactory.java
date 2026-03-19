package com.iflytek.skillhub.auth.policy;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import java.util.List;
import java.util.Set;

/**
 * Builds the active external-access policy from configuration properties.
 */
@Configuration
@ConfigurationProperties(prefix = "skillhub.access-policy")
public class AccessPolicyFactory {
    private String mode = "OPEN";
    private List<String> allowedEmailDomains = List.of();
    private List<String> allowedProviders = List.of();
    private List<String> whitelistedSubjects = List.of();

    @Bean
    public AccessPolicy accessPolicy() {
        return switch (mode.toUpperCase()) {
            case "EMAIL_DOMAIN" -> new EmailDomainAccessPolicy(Set.copyOf(allowedEmailDomains));
            case "PROVIDER_ALLOWLIST" -> new ProviderAllowlistAccessPolicy(Set.copyOf(allowedProviders));
            case "SUBJECT_WHITELIST" -> new SubjectWhitelistAccessPolicy(Set.copyOf(whitelistedSubjects));
            default -> new OpenAccessPolicy();
        };
    }

    public void setMode(String mode) { this.mode = mode; }
    public void setAllowedEmailDomains(List<String> d) { this.allowedEmailDomains = d; }
    public void setAllowedProviders(List<String> p) { this.allowedProviders = p; }
    public void setWhitelistedSubjects(List<String> s) { this.whitelistedSubjects = s; }
}
