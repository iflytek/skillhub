package com.iflytek.skillhub.domain.security;

/**
 * Types of security scanners that can audit skill versions.
 * Each scanner type represents a different automated security analysis tool.
 */
public enum ScannerType {
    /**
     * Cisco skill-scanner - static and behavioral analysis
     */
    SKILL_SCANNER("skill-scanner"),

    /**
     * Reserved for future scanner integrations
     */
    CUSTOM("custom");

    private final String value;

    ScannerType(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public static ScannerType fromValue(String value) {
        for (ScannerType type : values()) {
            if (type.value.equals(value)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown scanner type: " + value);
    }
}
