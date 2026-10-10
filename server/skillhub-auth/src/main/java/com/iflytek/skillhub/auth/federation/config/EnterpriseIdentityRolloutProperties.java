package com.iflytek.skillhub.auth.federation.config;

import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Organization-scoped rollout controls shared by enterprise identity capabilities. */
@ConfigurationProperties(prefix = "skillhub.enterprise")
public class EnterpriseIdentityRolloutProperties {

    private Set<String> organizationAllowlist = Set.of();

    public Set<String> getOrganizationAllowlist() {
        return organizationAllowlist;
    }

    public void setOrganizationAllowlist(Set<String> organizationAllowlist) {
        Objects.requireNonNull(organizationAllowlist, "organization allowlist must not be null");
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (String organizationId : organizationAllowlist) {
            Objects.requireNonNull(organizationId, "organization allowlist must not contain null values");
            String value = organizationId.trim();
            if (value.isEmpty()) {
                throw new IllegalArgumentException("organization allowlist must not contain blank values");
            }
            normalized.add(value);
        }
        this.organizationAllowlist = Set.copyOf(normalized);
    }
}
