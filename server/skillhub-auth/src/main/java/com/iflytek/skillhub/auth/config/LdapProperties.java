package com.iflytek.skillhub.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import jakarta.annotation.PostConstruct;

/**
 * Configuration properties for LDAP authentication.
 */
@Component
@ConfigurationProperties(prefix = "skillhub.ldap")
public class LdapProperties {

    /**
     * Whether LDAP authentication is enabled.
     */
    private boolean enabled = false;

    /**
     * LDAP server URL (e.g., ldap://localhost:389).
     */
    private String url;

    /**
     * Base DN for LDAP searches (e.g., dc=example,dc=com).
     */
    private String base;

    /**
     * DN of the user to bind for LDAP searches.
     */
    private String username;

    /**
     * Password for the LDAP bind user.
     */
    private String password;

    /**
     * LDAP attribute to use for username lookup (e.g., uid, sAMAccountName).
     */
    private String userSearchAttribute = "uid";

    /**
     * Search base for user lookup (relative to base).
     */
    private String userSearchBase = "";
 
    /**
     * Stable directory identifier attribute used as the LDAP identity subject.
     * OpenLDAP uses "entryUUID", Active Directory uses "objectGUID".
     */
    private String subjectAttribute = "entryUUID";

    /**
     * LDAP attribute mapped to the local display name.
     */
    private String displayNameAttribute = "displayName";

    /**
     * Fallback LDAP attribute for the display name when the primary
     * {@link #displayNameAttribute} is absent or empty. Defaults to {@code cn}
     * (common name), the conventional fallback for directories that do not
     * populate a dedicated display name.
     */
    private String displayNameFallbackAttribute = "cn";

    /**
     * LDAP attribute mapped to the local email.
     */
    private String emailAttribute = "mail";

    /**
     * LDAP connection timeout in milliseconds.
     */
    private int connectTimeoutMillis = 5000;

    /**
     * LDAP read timeout in milliseconds.
     */
    private int readTimeoutMillis = 10000;

    /**
     * Path to a custom trust store used for LDAPS certificate validation. When empty,
     * the JVM default trust store is used. Configure this for directories signed by
     * internal/self-signed CAs.
     */
    private String tlsTrustStorePath = "";

    /**
     * Password for the custom trust store. Only used when {@link #tlsTrustStorePath} is set.
     */
    private String tlsTrustStorePassword = "";

    /**
     * Trust store type (JKS, PKCS12). Defaults to JKS for compatibility.
     */
    private String tlsTrustStoreType = "JKS";

    @PostConstruct
    void validate() {
        if (!enabled) {
            return;
        }
        if (url == null || url.isBlank()) {
            throw new IllegalStateException("skillhub.ldap.url must be configured when LDAP is enabled");
        }
        if (base == null || base.isBlank()) {
            throw new IllegalStateException("skillhub.ldap.base must be configured when LDAP is enabled");
        }
        if (connectTimeoutMillis <= 0) {
            throw new IllegalStateException("skillhub.ldap.connect-timeout-millis must be a positive number");
        }
        if (readTimeoutMillis <= 0) {
            throw new IllegalStateException("skillhub.ldap.read-timeout-millis must be a positive number");
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getBase() {
        return base;
    }

    public void setBase(String base) {
        this.base = base;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getUserSearchAttribute() {
        return userSearchAttribute;
    }

    public void setUserSearchAttribute(String userSearchAttribute) {
        this.userSearchAttribute = userSearchAttribute;
    }

    public String getUserSearchBase() {
        return userSearchBase;
    }

    public void setUserSearchBase(String userSearchBase) {
        this.userSearchBase = userSearchBase;
    }

    public String getSubjectAttribute() {
        return subjectAttribute;
    }

    public void setSubjectAttribute(String subjectAttribute) {
        this.subjectAttribute = subjectAttribute;
    }

    public String getDisplayNameAttribute() {
        return displayNameAttribute;
    }

    public void setDisplayNameAttribute(String displayNameAttribute) {
        this.displayNameAttribute = displayNameAttribute;
    }

    public String getDisplayNameFallbackAttribute() {
        return displayNameFallbackAttribute;
    }

    public void setDisplayNameFallbackAttribute(String displayNameFallbackAttribute) {
        this.displayNameFallbackAttribute = displayNameFallbackAttribute;
    }

    public String getEmailAttribute() {
        return emailAttribute;
    }

    public void setEmailAttribute(String emailAttribute) {
        this.emailAttribute = emailAttribute;
    }

    public int getConnectTimeoutMillis() {
        return connectTimeoutMillis;
    }

    public void setConnectTimeoutMillis(int connectTimeoutMillis) {
        this.connectTimeoutMillis = connectTimeoutMillis;
    }

    public int getReadTimeoutMillis() {
        return readTimeoutMillis;
    }

    public void setReadTimeoutMillis(int readTimeoutMillis) {
        this.readTimeoutMillis = readTimeoutMillis;
    }

    public String getTlsTrustStorePath() {
        return tlsTrustStorePath;
    }

    public void setTlsTrustStorePath(String tlsTrustStorePath) {
        this.tlsTrustStorePath = tlsTrustStorePath;
    }

    public String getTlsTrustStorePassword() {
        return tlsTrustStorePassword;
    }

    public void setTlsTrustStorePassword(String tlsTrustStorePassword) {
        this.tlsTrustStorePassword = tlsTrustStorePassword;
    }

    public String getTlsTrustStoreType() {
        return tlsTrustStoreType;
    }

    public void setTlsTrustStoreType(String tlsTrustStoreType) {
        this.tlsTrustStoreType = tlsTrustStoreType;
    }
}
