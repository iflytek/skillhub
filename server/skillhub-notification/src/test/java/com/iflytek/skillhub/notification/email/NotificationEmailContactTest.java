package com.iflytek.skillhub.notification.email;

import static org.assertj.core.api.Assertions.*;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class NotificationEmailContactTest {
    private final Instant now = Instant.parse("2026-10-22T10:00:00Z");
    @Test
    void proofBindsRevisionAndChallengeIsConsumed() {
        var contact = new NotificationEmailContact("user");
        contact.challenge("id", "hash", "a@test.com", 1, "en", now);
        assertThat(contact.accepts("a@test.com", 1, now)).isTrue();
        contact.consume(now);
        assertThat(contact.verified("a@test.com", 1)).isTrue();
        assertThat(contact.verified("a@test.com", 3)).isFalse();
        assertThat(contact.accepts("a@test.com", 1, now)).isFalse();
    }
    @Test
    void expiryAttemptsAndCooldownAreBounded() {
        var contact = new NotificationEmailContact("user");
        contact.challenge("id", "hash", "a@test.com", 0, "zh", now);
        assertThatThrownBy(() -> contact.challenge("id2", "hash2", "a@test.com", 0, "zh", now.plusSeconds(59)))
                .isInstanceOf(com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException.class);
        assertThat(contact.accepts("a@test.com", 0, now.plusSeconds(600))).isFalse();
        for (int i = 0; i < 5; i++) contact.wrongCode();
        assertThat(contact.accepts("a@test.com", 0, now)).isFalse();
    }
    @Test
    void staleSendFailureCannotInvalidateReplacement() {
        var contact = new NotificationEmailContact("user");
        contact.challenge("old", "hash", "a@test.com", 0, "en", now);
        contact.challenge("new", "hash2", "a@test.com", 0, "en", now.plusSeconds(60));
        contact.failSend("old");
        assertThat(contact.getChallengeHash()).isEqualTo("hash2");
    }
    @Test
    void accountHourlyLimitPersistsAcrossCooldowns() {
        var contact = new NotificationEmailContact("user");
        for (int i = 0; i < 10; i++)
            contact.challenge("id" + i, "hash", "a@test.com", 0, "en", now.plusSeconds(i * 60));
        assertThatThrownBy(() -> contact.challenge("eleven", "hash", "a@test.com", 0, "en", now.plusSeconds(600)))
                .isInstanceOf(com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException.class);
        assertThatCode(() -> contact.challenge("reset", "hash", "a@test.com", 0, "en", now.plusSeconds(3600)))
                .doesNotThrowAnyException();
    }
    @Test
    void legacyEmailCaseAndWhitespaceUseOneCanonicalProofAndChallenge() {
        var contact = new NotificationEmailContact("user");
        contact.challenge("id", "hash", " A@Example.test ", 0, "en", now);
        assertThat(contact.accepts("a@example.test", 0, now)).isTrue();
        contact.consume(now);
        assertThat(contact.verified("a@example.test", 0)).isTrue();
        assertThat(contact.verified(" A@EXAMPLE.TEST ", 0)).isTrue();
        assertThat(contact.verified("a@example.test", 1)).isFalse();
    }
}
