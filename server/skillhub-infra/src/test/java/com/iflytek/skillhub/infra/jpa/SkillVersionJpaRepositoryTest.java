package com.iflytek.skillhub.infra.jpa;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SkillVersionJpaRepositoryTest {

    @Test
    void defaultMethods_delegateToOrderedVariants() {
        SkillVersionJpaRepository repo = mock(SkillVersionJpaRepository.class, CALLS_REAL_METHODS);
        when(repo.findBySkillIdAndStatusOrderByCreatedAtDesc(any(), any())).thenReturn(List.of());
        when(repo.findBySkillIdInAndStatusOrderByCreatedAtDesc(any(), any())).thenReturn(List.of());

        assertThat(repo.findBySkillIdAndStatus(1L, null)).isNotNull();
        assertThat(repo.findBySkillIdInAndStatus(List.of(1L), null)).isNotNull();
    }
}
