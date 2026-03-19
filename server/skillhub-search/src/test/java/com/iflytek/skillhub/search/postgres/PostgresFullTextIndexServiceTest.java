package com.iflytek.skillhub.search.postgres;

import com.iflytek.skillhub.infra.jpa.SkillSearchDocumentEntity;
import com.iflytek.skillhub.infra.jpa.SkillSearchDocumentJpaRepository;
import com.iflytek.skillhub.search.HashingSearchEmbeddingService;
import com.iflytek.skillhub.search.SkillSearchDocument;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PostgresFullTextIndexServiceTest {

    @Test
    void indexShouldTruncateOnlyColumnsThatStillHaveDatabaseLimits() {
        SkillSearchDocumentJpaRepository repository = mock(SkillSearchDocumentJpaRepository.class);
        when(repository.findBySkillId(1L)).thenReturn(Optional.empty());

        PostgresFullTextIndexService service = new PostgresFullTextIndexService(
                repository,
                new HashingSearchEmbeddingService()
        );

        SkillSearchDocument document = new SkillSearchDocument(
                1L,
                2L,
                "n".repeat(80),
                "o".repeat(140),
                "t".repeat(300),
                "summary",
                "k".repeat(700),
                "search text",
                null,
                "PUBLIC",
                "ACTIVE"
        );

        service.index(document);

        ArgumentCaptor<SkillSearchDocumentEntity> captor = ArgumentCaptor.forClass(SkillSearchDocumentEntity.class);
        verify(repository).save(captor.capture());

        SkillSearchDocumentEntity entity = captor.getValue();
        assertThat(entity.getNamespaceSlug()).hasSize(64);
        assertThat(entity.getOwnerId()).hasSize(128);
        assertThat(entity.getTitle()).hasSize(256);
        assertThat(entity.getKeywords()).hasSize(700);
        assertThat(entity.getSearchText()).isEqualTo("search text");
    }
}
