package com.iflytek.skillhub.auth.federation.config;

import java.net.URI;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Conservative transport and outbound-network controls for dynamic enterprise OIDC. */
@ConfigurationProperties(prefix = "skillhub.enterprise.oidc")
public class EnterpriseOidcProperties {

    private boolean enabled;
    private String publicBaseUri = "";
    private Set<String> privateHostAllowlist = Set.of();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getPublicBaseUri() {
        return publicBaseUri;
    }

    public void setPublicBaseUri(String publicBaseUri) {
        this.publicBaseUri = Objects.requireNonNull(publicBaseUri, "publicBaseUri").trim();
    }

    public Set<String> getPrivateHostAllowlist() {
        return privateHostAllowlist;
    }

    public void setPrivateHostAllowlist(Set<String> privateHostAllowlist) {
        Objects.requireNonNull(privateHostAllowlist, "privateHostAllowlist");
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (String host : privateHostAllowlist) {
            Objects.requireNonNull(host, "privateHostAllowlist entry");
            String value = host.trim().toLowerCase(Locale.ROOT);
            if (value.isEmpty()) {
                throw new IllegalArgumentException("privateHostAllowlist must not contain blanks");
            }
            normalized.add(value);
        }
        this.privateHostAllowlist = Set.copyOf(normalized);
    }

    public void validateEnabledConfiguration() {
        URI base;
        try {
            base = URI.create(publicBaseUri);
        } catch (IllegalArgumentException invalid) {
            throw new IllegalStateException("Enterprise OIDC public base URI is invalid");
        }
        if (!"https".equals(base.getScheme())
                || base.getHost() == null
                || base.getRawUserInfo() != null
                || base.getRawQuery() != null
                || base.getRawFragment() != null) {
            throw new IllegalStateException("Enterprise OIDC public base URI must use HTTPS");
        }
    }
}
