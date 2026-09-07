package com.iflytek.skillhub.domain.audit;

/** String-ID Organization resources supported by the audit schema. */
public enum OrganizationAuditTargetType {
    ORGANIZATION("ORGANIZATION"),
    DOMAIN("ORGANIZATION_DOMAIN"),
    MEMBERSHIP("ORGANIZATION_MEMBERSHIP"),
    ROLE_BINDING("ORGANIZATION_ROLE_BINDING");

    private final String storageValue;

    OrganizationAuditTargetType(String storageValue) {
        this.storageValue = storageValue;
    }

    public String storageValue() {
        return storageValue;
    }
}
