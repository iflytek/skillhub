package com.iflytek.skillhub.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

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
}