package com.iflytek.skillhub.auth.local;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Switches for first-party username-and-password accounts. Operators that rely on an external
 * identity provider can turn local accounts off entirely, or keep existing accounts while closing
 * self-registration.
 */
@Component
@ConfigurationProperties(prefix = "skillhub.auth.local")
public class LocalAuthProperties {

    /**
     * Default enabled to keep existing installs unchanged.
     */
    private boolean enabled = true;

    /**
     * Only takes effect while local accounts are enabled.
     */
    private boolean registrationEnabled = true;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isRegistrationEnabled() {
        return registrationEnabled;
    }

    public void setRegistrationEnabled(boolean registrationEnabled) {
        this.registrationEnabled = registrationEnabled;
    }

    public boolean isRegistrationAvailable() {
        return enabled && registrationEnabled;
    }
}
