package com.iflytek.skillhub.auth.connection.oidc;

import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "skillhub.enterprise.oidc")
public class EnterpriseOidcProperties {

    private boolean enabled;
    private Set<String> privateHostAllowlist = Set.of();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Set<String> getPrivateHostAllowlist() {
        return privateHostAllowlist;
    }

    public void setPrivateHostAllowlist(Set<String> privateHostAllowlist) {
        this.privateHostAllowlist = privateHostAllowlist.stream()
                .map(host -> host.strip().toLowerCase(Locale.ROOT))
                .filter(host -> !host.isEmpty())
                .collect(Collectors.toUnmodifiableSet());
    }
}
