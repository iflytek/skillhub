package com.iflytek.skillhub.config;

import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "skillhub.identity.directory")
public class DirectoryProvisioningProperties {

    private boolean enabled;
    private Set<String> organizationAllowlist = Set.of();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Set<String> getOrganizationAllowlist() {
        return organizationAllowlist;
    }

    public void setOrganizationAllowlist(Set<String> organizationAllowlist) {
        this.organizationAllowlist = organizationAllowlist == null
                ? Set.of()
                : organizationAllowlist.stream()
                .map(value -> value.strip().toLowerCase(Locale.ROOT))
                .filter(value -> !value.isEmpty())
                .collect(Collectors.toUnmodifiableSet());
    }

    public boolean allows(String organizationId) {
        return enabled && organizationAllowlist.contains(organizationId.toLowerCase(Locale.ROOT));
    }
}
