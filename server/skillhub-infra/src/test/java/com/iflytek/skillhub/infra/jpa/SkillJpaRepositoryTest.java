package com.iflytek.skillhub.infra.jpa;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SkillJpaRepositoryTest {

    @Test
    void defaultMethods_delegateToOrderedVariants() {
        SkillJpaRepository repo = mock(SkillJpaRepository.class, CALLS_REAL_METHODS);
        when(repo.findByNamespaceIdAndStatusOrderByCreatedAtDesc(any(), any())).thenReturn(List.of());
        when(repo.findByOwnerIdOrderByUpdatedAtDesc(any(), any())).thenReturn(org.springframework.data.domain.Page.empty());

        assertThat(repo.findByNamespaceIdAndStatus(1L, null)).isNotNull();
        assertThat(repo.findByOwnerId("user-1", org.springframework.data.domain.PageRequest.of(0, 10))).isNotNull();
    }
}
