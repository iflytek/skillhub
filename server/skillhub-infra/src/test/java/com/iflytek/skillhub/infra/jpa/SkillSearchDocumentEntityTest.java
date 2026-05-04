package com.iflytek.skillhub.infra.jpa;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class SkillSearchDocumentEntityTest {

    @Test
    void protectedConstructor_shouldCreateEntity() {
        SkillSearchDocumentEntity entity = new SkillSearchDocumentEntity();
        assertThat(entity).isNotNull();
    }

    @Test
    void fullConstructor_shouldSetAllFields() {
        SkillSearchDocumentEntity entity = new SkillSearchDocumentEntity(
                1L, 2L, "ns", "owner", "title", "summary",
                "keywords", "searchText", "vector", "PUBLIC", "ACTIVE");

        assertThat(entity.getSkillId()).isEqualTo(1L);
        assertThat(entity.getNamespaceId()).isEqualTo(2L);
        assertThat(entity.getNamespaceSlug()).isEqualTo("ns");
        assertThat(entity.getOwnerId()).isEqualTo("owner");
        assertThat(entity.getTitle()).isEqualTo("title");
        assertThat(entity.getSummary()).isEqualTo("summary");
        assertThat(entity.getKeywords()).isEqualTo("keywords");
        assertThat(entity.getSearchText()).isEqualTo("searchText");
        assertThat(entity.getSemanticVector()).isEqualTo("vector");
        assertThat(entity.getVisibility()).isEqualTo("PUBLIC");
        assertThat(entity.getStatus()).isEqualTo("ACTIVE");
    }

    @Test
    void onUpdate_shouldSetUpdatedAt() {
        SkillSearchDocumentEntity entity = new SkillSearchDocumentEntity();
        assertThat(entity.getUpdatedAt()).isNull();

        entity.onUpdate();

        assertThat(entity.getUpdatedAt()).isNotNull();
        assertThat(entity.getUpdatedAt()).isBeforeOrEqualTo(LocalDateTime.now());
    }

    @Test
    void getters_shouldReturnValues() {
        SkillSearchDocumentEntity entity = new SkillSearchDocumentEntity(
                1L, 2L, "ns", "owner", "title", "summary",
                "keywords", "searchText", "vector", "PUBLIC", "ACTIVE");

        assertThat(entity.getId()).isNull();
        assertThat(entity.getSkillId()).isEqualTo(1L);
        assertThat(entity.getNamespaceId()).isEqualTo(2L);
        assertThat(entity.getNamespaceSlug()).isEqualTo("ns");
        assertThat(entity.getOwnerId()).isEqualTo("owner");
        assertThat(entity.getTitle()).isEqualTo("title");
        assertThat(entity.getSummary()).isEqualTo("summary");
        assertThat(entity.getKeywords()).isEqualTo("keywords");
        assertThat(entity.getSearchText()).isEqualTo("searchText");
        assertThat(entity.getSemanticVector()).isEqualTo("vector");
        assertThat(entity.getVisibility()).isEqualTo("PUBLIC");
        assertThat(entity.getStatus()).isEqualTo("ACTIVE");
        assertThat(entity.getUpdatedAt()).isNull();
    }

    @Test
    void setters_shouldUpdateValues() {
        SkillSearchDocumentEntity entity = new SkillSearchDocumentEntity();

        entity.setNamespaceId(10L);
        assertThat(entity.getNamespaceId()).isEqualTo(10L);

        entity.setNamespaceSlug("new-ns");
        assertThat(entity.getNamespaceSlug()).isEqualTo("new-ns");

        entity.setOwnerId("new-owner");
        assertThat(entity.getOwnerId()).isEqualTo("new-owner");

        entity.setTitle("new-title");
        assertThat(entity.getTitle()).isEqualTo("new-title");

        entity.setSummary("new-summary");
        assertThat(entity.getSummary()).isEqualTo("new-summary");

        entity.setKeywords("new-keywords");
        assertThat(entity.getKeywords()).isEqualTo("new-keywords");

        entity.setSearchText("new-search");
        assertThat(entity.getSearchText()).isEqualTo("new-search");

        entity.setSemanticVector("new-vector");
        assertThat(entity.getSemanticVector()).isEqualTo("new-vector");

        entity.setVisibility("PRIVATE");
        assertThat(entity.getVisibility()).isEqualTo("PRIVATE");

        entity.setStatus("HIDDEN");
        assertThat(entity.getStatus()).isEqualTo("HIDDEN");
    }
}
