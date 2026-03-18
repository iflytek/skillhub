package com.iflytek.skillhub.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "skillhub.ratelimit.download")
public class DownloadRateLimitProperties {

    private String anonymousCookieName = "skillhub_anon_dl";
    private Duration anonymousCookieMaxAge = Duration.ofDays(30);
    private String anonymousCookieSecret = "change-me-in-production";

    public String getAnonymousCookieName() {
        return anonymousCookieName;
    }

    public void setAnonymousCookieName(String anonymousCookieName) {
        this.anonymousCookieName = anonymousCookieName;
    }

    public Duration getAnonymousCookieMaxAge() {
        return anonymousCookieMaxAge;
    }

    public void setAnonymousCookieMaxAge(Duration anonymousCookieMaxAge) {
        this.anonymousCookieMaxAge = anonymousCookieMaxAge;
    }

    public String getAnonymousCookieSecret() {
        return anonymousCookieSecret;
    }

    public void setAnonymousCookieSecret(String anonymousCookieSecret) {
        this.anonymousCookieSecret = anonymousCookieSecret;
    }
}
