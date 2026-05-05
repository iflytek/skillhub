package com.iflytek.skillhub.domain.label;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class LabelTranslationTest {

    @Test
    void constructorInitializesFields() {
        LabelTranslation translation = new LabelTranslation(1L, "zh-CN", "测试标签");

        assertThat(translation.getLabelId()).isEqualTo(1L);
        assertThat(translation.getLocale()).isEqualTo("zh-CN");
        assertThat(translation.getDisplayName()).isEqualTo("测试标签");
    }

    @Test
    void prePersistSetsTimestamps() {
        LabelTranslation translation = new LabelTranslation(1L, "en", "Test Label");

        translation.onCreate();

        assertThat(translation.getCreatedAt()).isNotNull();
        assertThat(translation.getUpdatedAt()).isNotNull();
        assertThat(translation.getCreatedAt()).isEqualTo(translation.getUpdatedAt());
    }

    @Test
    void preUpdateSetsUpdatedAt() {
        LabelTranslation translation = new LabelTranslation(1L, "en", "Test Label");
        translation.onCreate();

        try {
            Thread.sleep(5);
        } catch (InterruptedException ignored) {}
        translation.onUpdate();

        assertThat(translation.getUpdatedAt()).isAfterOrEqualTo(translation.getCreatedAt());
    }

    @Test
    void gettersWork() {
        LabelTranslation translation = new LabelTranslation(2L, "ja", "テスト");
        translation.onCreate();

        assertThat(translation.getId()).isNull();
        assertThat(translation.getLabelId()).isEqualTo(2L);
        assertThat(translation.getLocale()).isEqualTo("ja");
        assertThat(translation.getDisplayName()).isEqualTo("テスト");
        assertThat(translation.getCreatedAt()).isNotNull();
        assertThat(translation.getUpdatedAt()).isNotNull();
    }

    @Test
    void protectedConstructorExistsForJpa() {
        LabelTranslation translation = new LabelTranslation();
        assertThat(translation).isNotNull();
    }
}
