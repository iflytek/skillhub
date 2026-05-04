package com.iflytek.skillhub.auth.local;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class LocalCredentialTest {

    @Test
    void constructorInitializesFields() {
        LocalCredential credential = new LocalCredential("user-1", "alice", "hash123");

        assertThat(credential.getUserId()).isEqualTo("user-1");
        assertThat(credential.getUsername()).isEqualTo("alice");
        assertThat(credential.getPasswordHash()).isEqualTo("hash123");
        assertThat(credential.getFailedAttempts()).isEqualTo(0);
        assertThat(credential.getLockedUntil()).isNull();
    }

    @Test
    void settersAndGettersWork() {
        LocalCredential credential = new LocalCredential("user-1", "alice", "hash123");

        credential.setUserId("user-2");
        credential.setFailedAttempts(3);
        credential.setLockedUntil(Instant.parse("2026-12-31T00:00:00Z"));
        credential.setPasswordHash("newhash");

        assertThat(credential.getUserId()).isEqualTo("user-2");
        assertThat(credential.getFailedAttempts()).isEqualTo(3);
        assertThat(credential.getLockedUntil()).isEqualTo(Instant.parse("2026-12-31T00:00:00Z"));
        assertThat(credential.getPasswordHash()).isEqualTo("newhash");
    }

    @Test
    void prePersistSetsTimestamps() {
        LocalCredential credential = new LocalCredential("user-1", "alice", "hash123");

        credential.prePersist();

        assertThat(credential.getCreatedAt()).isNotNull();
        assertThat(credential.getUpdatedAt()).isNotNull();
        assertThat(credential.getCreatedAt()).isEqualTo(credential.getUpdatedAt());
    }

    @Test
    void preUpdateSetsUpdatedAt() {
        LocalCredential credential = new LocalCredential("user-1", "alice", "hash123");
        credential.prePersist();

        Instant beforeUpdate = credential.getUpdatedAt();
        try {
            Thread.sleep(5);
        } catch (InterruptedException ignored) {}
        credential.preUpdate();

        assertThat(credential.getUpdatedAt()).isNotNull();
        assertThat(credential.getUpdatedAt()).isAfterOrEqualTo(beforeUpdate);
    }

    @Test
    void protectedConstructorExistsForJpa() {
        LocalCredential credential = new LocalCredential();
        assertThat(credential).isNotNull();
    }
}
