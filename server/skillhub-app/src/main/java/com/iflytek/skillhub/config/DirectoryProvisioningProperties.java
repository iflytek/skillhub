package com.iflytek.skillhub.config;

import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "skillhub.identity.directory")
public class DirectoryProvisioningProperties {

    private boolean enabled;
    private Set<String> organizationAllowlist = Set.of();
    private int maxRequestBytes = 64 * 1024;
    private int maxFieldLength = 256;
    private int maxFilterLength = 512;
    private int maxPatchOperations = 32;
    private int maxPageCount = 100;
    private int maxStartIndex = 10_000;

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

    public int getMaxRequestBytes() {
        return maxRequestBytes;
    }

    public void setMaxRequestBytes(int maxRequestBytes) {
        this.maxRequestBytes = positive(maxRequestBytes, "maxRequestBytes");
    }

    public int getMaxFieldLength() {
        return maxFieldLength;
    }

    public void setMaxFieldLength(int maxFieldLength) {
        this.maxFieldLength = positive(maxFieldLength, "maxFieldLength");
    }

    public int getMaxFilterLength() {
        return maxFilterLength;
    }

    public void setMaxFilterLength(int maxFilterLength) {
        this.maxFilterLength = positive(maxFilterLength, "maxFilterLength");
    }

    public int getMaxPatchOperations() {
        return maxPatchOperations;
    }

    public void setMaxPatchOperations(int maxPatchOperations) {
        this.maxPatchOperations = positive(maxPatchOperations, "maxPatchOperations");
    }

    public int getMaxPageCount() {
        return maxPageCount;
    }

    public void setMaxPageCount(int maxPageCount) {
        this.maxPageCount = positive(maxPageCount, "maxPageCount");
    }

    public int getMaxStartIndex() {
        return maxStartIndex;
    }

    public void setMaxStartIndex(int maxStartIndex) {
        this.maxStartIndex = positive(maxStartIndex, "maxStartIndex");
    }

    private int positive(int value, String field) {
        if (value < 1) {
            throw new IllegalArgumentException(field + " must be positive");
        }
        return value;
    }
}
