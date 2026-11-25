package com.iflytek.skillhub.auth.federation.config;

import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.net.URI;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Organization-scoped rollout controls shared by enterprise identity capabilities. */
@ConfigurationProperties(prefix = "skillhub.enterprise")
public class EnterpriseIdentityRolloutProperties {

    private Set<String> organizationAllowlist = Set.of();
    private boolean oidcLoginEnabled;
    private String oidcCallbackBaseUri = "";

    public Set<String> getOrganizationAllowlist() {
        return organizationAllowlist;
    }

    public boolean isOidcLoginEnabled() {
        return oidcLoginEnabled;
    }

    public void setOidcLoginEnabled(boolean oidcLoginEnabled) {
        this.oidcLoginEnabled = oidcLoginEnabled;
    }

    public String getOidcCallbackBaseUri() {
        return oidcCallbackBaseUri;
    }

    public void setOidcCallbackBaseUri(String oidcCallbackBaseUri) {
        this.oidcCallbackBaseUri = oidcCallbackBaseUri == null ? "" : oidcCallbackBaseUri.strip();
    }

    public URI callbackUri(String publicHandle) {
        if (oidcCallbackBaseUri.isBlank()) {
            throw new IllegalStateException("enterprise OIDC callback origin is unavailable");
        }
        URI base = URI.create(oidcCallbackBaseUri);
        if (!"https".equalsIgnoreCase(base.getScheme()) || base.getRawQuery() != null
                || base.getRawFragment() != null || base.getRawUserInfo() != null) {
            throw new IllegalStateException("enterprise OIDC callback origin is invalid");
        }
        String path = base.getPath() == null ? "" : base.getPath().replaceAll("/$", "");
        return URI.create(base.getScheme() + "://" + base.getRawAuthority() + path
                + "/api/v1/auth/enterprise/" + publicHandle + "/callback");
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
