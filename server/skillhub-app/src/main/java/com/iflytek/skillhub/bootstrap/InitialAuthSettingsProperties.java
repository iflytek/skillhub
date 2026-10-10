package com.iflytek.skillhub.bootstrap;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "skillhub.auth.initial-settings")
public class InitialAuthSettingsProperties {
    private boolean passwordLoginEnabled = true;
    private boolean selfRegistrationEnabled = true;
    private String roleGrantsJson = "[]";

    public boolean isPasswordLoginEnabled() { return passwordLoginEnabled; }
    public void setPasswordLoginEnabled(boolean enabled) { this.passwordLoginEnabled = enabled; }
    public boolean isSelfRegistrationEnabled() { return selfRegistrationEnabled; }
    public void setSelfRegistrationEnabled(boolean enabled) { this.selfRegistrationEnabled = enabled; }
    public String getRoleGrantsJson() { return roleGrantsJson; }
    public void setRoleGrantsJson(String roleGrantsJson) { this.roleGrantsJson = roleGrantsJson; }
}
