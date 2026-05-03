package com.iflytek.skillhub.domain.skill;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Field;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class SkillVersionStatsTest {

    @Test
    void constructorAndLifecycleCallbacksMaintainTimestamps() {
        SkillVersionStats empty = new SkillVersionStats();

        assertThat(empty.getSkillVersionId()).isNull();
        assertThat(empty.getSkillId()).isNull();
        assertThat(empty.getDownloadCount()).isEqualTo(0L);
        assertThat(empty.getUpdatedAt()).isNull();

        SkillVersionStats stats = new SkillVersionStats(11L, 22L);

        assertThat(stats.getSkillVersionId()).isEqualTo(11L);
        assertThat(stats.getSkillId()).isEqualTo(22L);
        assertThat(stats.getDownloadCount()).isEqualTo(0L);

        stats.onCreate();

        assertThat(stats.getUpdatedAt()).isNotNull();

        Instant seedUpdatedAt = Instant.EPOCH;
        setField(stats, "updatedAt", seedUpdatedAt);

        stats.onUpdate();

        assertThat(stats.getUpdatedAt()).isAfter(seedUpdatedAt);
    }

    @Test
    void incrementDownloadCountHandlesNullAndExistingCounts() {
        SkillVersionStats stats = new SkillVersionStats(11L, 22L);

        stats.incrementDownloadCount();

        assertThat(stats.getDownloadCount()).isEqualTo(1L);

        setField(stats, "downloadCount", null);

        stats.incrementDownloadCount();

        assertThat(stats.getDownloadCount()).isEqualTo(1L);
    }

    private static void setField(Object target, String fieldName, Object value) {
        try {
            Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError(exception);
        }
    }
}
