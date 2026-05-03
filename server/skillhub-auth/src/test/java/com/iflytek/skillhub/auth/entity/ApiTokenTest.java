package com.iflytek.skillhub.auth.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class ApiTokenTest {

    @Test
    void protectedNoArgsConstructorSupportsJpaInstantiation() {
        ApiToken token = new ApiToken();

        assertThat(token.getId()).isNull();
        assertThat(token.getSubjectType()).isEqualTo("USER");
        assertThat(token.getSubjectId()).isNull();
        assertThat(token.getUserId()).isNull();
        assertThat(token.getName()).isNull();
        assertThat(token.getTokenPrefix()).isNull();
        assertThat(token.getTokenHash()).isNull();
        assertThat(token.getScopeJson()).isNull();
        assertThat(token.getCreatedAt()).isNull();
    }

    @Test
    void constructorAndLifecycleCallbacksPopulateCoreFields() {
        ApiToken token = new ApiToken("usr_1", "cli", "sk_live", "hash_123", "[\"skill:publish\"]");

        assertThat(token.getId()).isNull();
        assertThat(token.getSubjectType()).isEqualTo("USER");
        assertThat(token.getSubjectId()).isEqualTo("usr_1");
        assertThat(token.getUserId()).isEqualTo("usr_1");
        assertThat(token.getName()).isEqualTo("cli");
        assertThat(token.getTokenPrefix()).isEqualTo("sk_live");
        assertThat(token.getTokenHash()).isEqualTo("hash_123");
        assertThat(token.getScopeJson()).isEqualTo("[\"skill:publish\"]");
        assertThat(token.getCreatedAt()).isNull();

        token.prePersist();

        assertThat(token.getCreatedAt()).isNotNull();
    }

    @Test
    void settersAndRevocationFlagsReflectRuntimeState() {
        ApiToken token = new ApiToken("usr_1", "cli", "sk_live", "hash_123", "[]");
        Instant lastUsedAt = Instant.parse("2026-05-04T00:00:00Z");
        Instant revokedAt = Instant.parse("2026-05-04T01:00:00Z");

        token.setSubjectId("subject_2");
        token.setUserId("usr_2");
        token.setLastUsedAt(lastUsedAt);
        token.setRevokedAt(revokedAt);

        assertThat(token.getSubjectId()).isEqualTo("subject_2");
        assertThat(token.getUserId()).isEqualTo("usr_2");
        assertThat(token.getLastUsedAt()).isEqualTo(lastUsedAt);
        assertThat(token.getRevokedAt()).isEqualTo(revokedAt);
        assertThat(token.isRevoked()).isTrue();
        assertThat(token.isValid(lastUsedAt)).isFalse();
    }

    @Test
    void expirationChecksHandleMissingPastAndFutureExpiry() {
        ApiToken token = new ApiToken("usr_1", "cli", "sk_live", "hash_123", "[]");
        Instant referenceTime = Instant.parse("2026-05-04T12:00:00Z");

        assertThat(token.isExpired()).isFalse();
        assertThat(token.isExpired(referenceTime)).isFalse();
        assertThat(token.isValid(referenceTime)).isTrue();

        token.setExpiresAt(referenceTime.minusSeconds(1));

        assertThat(token.getExpiresAt()).isEqualTo(referenceTime.minusSeconds(1));
        assertThat(token.isExpired(referenceTime)).isTrue();
        assertThat(token.isValid(referenceTime)).isFalse();

        token.setExpiresAt(Instant.now().plusSeconds(60));
        token.setRevokedAt(null);

        assertThat(token.isExpired()).isFalse();
        assertThat(token.isValid()).isTrue();
    }
}
