package com.iflytek.skillhub.auth.local;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "skillhub.auth.password-reset")
public class PasswordResetProperties {

    private Duration codeExpiry = Duration.ofMinutes(10);
    private String emailFromAddress = "noreply@skillhub.local";
    private String emailFromName = "SkillHub";

    public Duration getCodeExpiry() {
        return codeExpiry;
    }

    public void setCodeExpiry(Duration codeExpiry) {
        this.codeExpiry = codeExpiry;
    }

    public String getEmailFromAddress() {
        return emailFromAddress;
    }

    public void setEmailFromAddress(String emailFromAddress) {
        this.emailFromAddress = emailFromAddress;
    }

    public String getEmailFromName() {
        return emailFromName;
    }

    public void setEmailFromName(String emailFromName) {
        this.emailFromName = emailFromName;
    }
}
