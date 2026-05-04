package com.iflytek.skillhub.search;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.iflytek.skillhub.infra.jpa.SkillSearchDocumentJpaRepository;
import org.junit.jupiter.api.Test;

class JpaSearchIndexServiceTest {

    @Test
    void constructorWiresDependencies() {
        SkillSearchDocumentJpaRepository repository = mock(SkillSearchDocumentJpaRepository.class);

        JpaSearchIndexService service = new JpaSearchIndexService(repository, new HashingSearchEmbeddingService());

        assertThat(service).isNotNull();
    }
}
