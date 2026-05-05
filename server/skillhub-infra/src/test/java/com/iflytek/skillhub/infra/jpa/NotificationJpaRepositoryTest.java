package com.iflytek.skillhub.infra.jpa;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class NotificationJpaRepositoryTest {

    @Test
    void defaultMethods_delegateToOrderedVariants() {
        NotificationJpaRepository repo = mock(NotificationJpaRepository.class, CALLS_REAL_METHODS);
        when(repo.findByRecipientIdOrderByCreatedAtDesc(any(), any())).thenReturn(org.springframework.data.domain.Page.empty());
        when(repo.findByRecipientIdAndCategoryOrderByCreatedAtDesc(any(), any(), any())).thenReturn(org.springframework.data.domain.Page.empty());

        assertThat(repo.findByRecipientId("user-1", PageRequest.of(0, 10))).isNotNull();
        assertThat(repo.findByRecipientIdAndCategory("user-1", null, PageRequest.of(0, 10))).isNotNull();
    }
}
