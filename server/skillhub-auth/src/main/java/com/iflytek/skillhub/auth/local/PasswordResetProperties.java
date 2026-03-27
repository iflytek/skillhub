package com.iflytek.skillhub.auth.local;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
@ConfigurationProperties(prefix = "skillhub.auth.password-reset")
public class PasswordResetProperties {
    private Duration tokenExpiry = Duration.ofMinutes(30);
    private String emailFromAddress = "noreply@skillhub.local";
    private String emailFromName = "SkillHub";

    public Duration getTokenExpiry() { return tokenExpiry; }
    public void setTokenExpiry(Duration tokenExpiry) { this.tokenExpiry = tokenExpiry; }
    public String getEmailFromAddress() { return emailFromAddress; }
    public void setEmailFromAddress(String emailFromAddress) { this.emailFromAddress = emailFromAddress; }
    public String getEmailFromName() { return emailFromName; }
    public void setEmailFromName(String emailFromName) { this.emailFromName = emailFromName; }
}
