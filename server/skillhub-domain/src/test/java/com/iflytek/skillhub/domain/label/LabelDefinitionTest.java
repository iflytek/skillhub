package com.iflytek.skillhub.domain.label;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class LabelDefinitionTest {

    @Test
    void constructorInitializesFields() {
        LabelDefinition label = new LabelDefinition("test-label", LabelType.RECOMMENDED, true, 1, "user-1");

        assertThat(label.getSlug()).isEqualTo("test-label");
        assertThat(label.getType()).isEqualTo(LabelType.RECOMMENDED);
        assertThat(label.isVisibleInFilter()).isTrue();
        assertThat(label.getSortOrder()).isEqualTo(1);
        assertThat(label.getCreatedBy()).isEqualTo("user-1");
    }

    @Test
    void prePersistSetsTimestamps() {
        LabelDefinition label = new LabelDefinition("test", LabelType.PRIVILEGED, false, 0, "user-1");

        label.onCreate();

        assertThat(label.getCreatedAt()).isNotNull();
        assertThat(label.getUpdatedAt()).isNotNull();
        assertThat(label.getCreatedAt()).isEqualTo(label.getUpdatedAt());
    }

    @Test
    void preUpdateSetsUpdatedAt() {
        LabelDefinition label = new LabelDefinition("test", LabelType.PRIVILEGED, false, 0, "user-1");
        label.onCreate();

        try {
            Thread.sleep(5);
        } catch (InterruptedException ignored) {}
        label.onUpdate();

        assertThat(label.getUpdatedAt()).isAfterOrEqualTo(label.getCreatedAt());
    }

    @Test
    void settersWork() {
        LabelDefinition label = new LabelDefinition("test", LabelType.PRIVILEGED, true, 0, "user-1");

        label.setType(LabelType.RECOMMENDED);
        label.setVisibleInFilter(false);
        label.setSortOrder(5);

        assertThat(label.getType()).isEqualTo(LabelType.RECOMMENDED);
        assertThat(label.isVisibleInFilter()).isFalse();
        assertThat(label.getSortOrder()).isEqualTo(5);
    }

    @Test
    void protectedConstructorExistsForJpa() {
        LabelDefinition label = new LabelDefinition();
        assertThat(label).isNotNull();
    }
}
