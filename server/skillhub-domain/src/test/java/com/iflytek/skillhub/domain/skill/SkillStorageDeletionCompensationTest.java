package com.iflytek.skillhub.domain.skill;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class SkillStorageDeletionCompensationTest {

    @Test
    void constructorInitializesFields() {
        SkillStorageDeletionCompensation compensation = new SkillStorageDeletionCompensation(
                1L, "global", "test-skill", "[\"key1\",\"key2\"]", "error msg"
        );

        assertThat(compensation.getSkillId()).isEqualTo(1L);
        assertThat(compensation.getNamespace()).isEqualTo("global");
        assertThat(compensation.getSlug()).isEqualTo("test-skill");
        assertThat(compensation.getStorageKeysJson()).isEqualTo("[\"key1\",\"key2\"]");
        assertThat(compensation.getLastError()).isEqualTo("error msg");
        assertThat(compensation.getStatus()).isEqualTo(SkillStorageDeletionCompensationStatus.PENDING);
        assertThat(compensation.getAttemptCount()).isEqualTo(0);
        assertThat(compensation.getLastAttemptAt()).isNull();
    }

    @Test
    void prePersistSetsTimestamps() {
        SkillStorageDeletionCompensation compensation = new SkillStorageDeletionCompensation(
                1L, "global", "test-skill", "[]", null
        );

        compensation.onCreate();

        assertThat(compensation.getCreatedAt()).isNotNull();
        assertThat(compensation.getUpdatedAt()).isNotNull();
        assertThat(compensation.getCreatedAt()).isEqualTo(compensation.getUpdatedAt());
    }

    @Test
    void markAttemptIncrementsCounterAndSetsError() {
        SkillStorageDeletionCompensation compensation = new SkillStorageDeletionCompensation(
                1L, "global", "test-skill", "[]", null
        );
        compensation.onCreate();

        try {
            Thread.sleep(5);
        } catch (InterruptedException ignored) {}
        compensation.markAttempt("new error");

        assertThat(compensation.getAttemptCount()).isEqualTo(1);
        assertThat(compensation.getLastError()).isEqualTo("new error");
        assertThat(compensation.getLastAttemptAt()).isNotNull();
        assertThat(compensation.getUpdatedAt()).isAfterOrEqualTo(compensation.getCreatedAt());
    }

    @Test
    void markAttemptMultipleTimes() {
        SkillStorageDeletionCompensation compensation = new SkillStorageDeletionCompensation(
                1L, "global", "test-skill", "[]", null
        );

        compensation.markAttempt("error 1");
        compensation.markAttempt("error 2");

        assertThat(compensation.getAttemptCount()).isEqualTo(2);
        assertThat(compensation.getLastError()).isEqualTo("error 2");
    }

    @Test
    void markCompletedSetsStatusAndUpdatedAt() {
        SkillStorageDeletionCompensation compensation = new SkillStorageDeletionCompensation(
                1L, "global", "test-skill", "[]", null
        );
        compensation.onCreate();

        try {
            Thread.sleep(5);
        } catch (InterruptedException ignored) {}
        compensation.markCompleted();

        assertThat(compensation.getStatus()).isEqualTo(SkillStorageDeletionCompensationStatus.COMPLETED);
        assertThat(compensation.getUpdatedAt()).isAfterOrEqualTo(compensation.getCreatedAt());
    }

    @Test
    void gettersWork() {
        SkillStorageDeletionCompensation compensation = new SkillStorageDeletionCompensation(
                1L, "global", "test-skill", "[]", "err"
        );

        assertThat(compensation.getId()).isNull();
        assertThat(compensation.getSkillId()).isEqualTo(1L);
        assertThat(compensation.getNamespace()).isEqualTo("global");
        assertThat(compensation.getSlug()).isEqualTo("test-skill");
        assertThat(compensation.getStorageKeysJson()).isEqualTo("[]");
        assertThat(compensation.getLastError()).isEqualTo("err");
    }

    @Test
    void protectedConstructorExistsForJpa() {
        SkillStorageDeletionCompensation compensation = new SkillStorageDeletionCompensation();
        assertThat(compensation).isNotNull();
    }
}
