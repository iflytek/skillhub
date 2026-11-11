package com.iflytek.skillhub.notification.email;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "notification_email_contact")
public class NotificationEmailContact {
    @Id @Column(name = "user_id", length = 128) private String userId;
    @Column(name = "verified_email", length = 256) private String verifiedEmail;
    @Column(name = "verified_revision") private Long verifiedRevision;
    @Column(name = "verified_at") private Instant verifiedAt;
    @Column(name = "account_enabled_since") private Instant accountEnabledSince;
    @Column(name = "challenge_hash", length = 256) private String challengeHash;
    @Column(name = "challenge_id", length = 36) private String challengeId;
    @Column(name = "challenge_email", length = 256) private String challengeEmail;
    @Column(name = "challenge_revision") private Long challengeRevision;
    @Column(name = "challenge_expires_at") private Instant challengeExpiresAt;
    @Column(name = "failed_attempts", nullable = false) private int failedAttempts;
    @Column(name = "resend_available_at") private Instant resendAvailableAt;
    @Column(name = "request_window_start") private Instant requestWindowStart;
    @Column(name = "request_count", nullable = false) private int requestCount;
    @Column(nullable = false, length = 8) private String locale = "en";
    @Version private long version;
    protected NotificationEmailContact() {}
    public NotificationEmailContact(String userId) {
        this.userId = userId;
    }
    public boolean verified(String email, long revision) {
        return verifiedAt != null && java.util.Objects.equals(normalize(email), normalize(verifiedEmail))
                && java.util.Objects.equals(revision, verifiedRevision);
    }
    public void challenge(String id, String hash, String email, long revision, String locale, Instant now) {
        if (requestWindowStart == null || !now.isBefore(requestWindowStart.plusSeconds(3600))) {
            requestWindowStart = now;
            requestCount = 0;
        }
        if ((resendAvailableAt != null && now.isBefore(resendAvailableAt)) || requestCount >= 10)
            throw new com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException(
                    "error.notification.email.rateLimited");
        this.challengeId = id;
        this.challengeHash = hash;
        this.challengeEmail = normalize(email);
        this.challengeRevision = revision;
        this.challengeExpiresAt = now.plusSeconds(600);
        this.resendAvailableAt = now.plusSeconds(60);
        this.failedAttempts = 0;
        this.locale = locale;
        requestCount++;
    }
    public boolean accepts(String email, long revision, Instant now) {
        return challengeHash != null && failedAttempts < 5 && now.isBefore(challengeExpiresAt)
                && java.util.Objects.equals(normalize(email), normalize(challengeEmail))
                && java.util.Objects.equals(revision, challengeRevision);
    }
    public void wrongCode() {
        failedAttempts++;
    }
    public void consume(Instant now) {
        verifiedEmail = challengeEmail;
        verifiedRevision = challengeRevision;
        verifiedAt = now;
        challengeHash = null;
        challengeId = null;
    }
    public void failSend(String id) {
        if (java.util.Objects.equals(id, challengeId)) {
            challengeHash = null;
            challengeId = null;
        }
    }
    private String normalize(String email) {
        return com.iflytek.skillhub.domain.user.UserAccount.normalizeEmail(email);
    }
    public void markActive(Instant now) {
        accountEnabledSince = java.util.Objects.requireNonNull(now);
    }
    public Instant eligibleSince() {
        if (verifiedAt == null)
            return null;
        return accountEnabledSince != null && accountEnabledSince.isAfter(verifiedAt)
                ? accountEnabledSince : verifiedAt;
    }
    public String getChallengeHash() {
        return challengeHash;
    }
    public String getLocale() {
        return locale;
    }
    public Instant getResendAvailableAt() {
        return resendAvailableAt;
    }
}
