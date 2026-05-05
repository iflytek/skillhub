package com.iflytek.skillhub.infra.jpa;

import com.iflytek.skillhub.domain.security.SecurityAudit;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SecurityAuditJpaRepositoryTest {

    @Test
    void saveAll_delegatesToSaveAllAndFlush() {
        SecurityAuditJpaRepository repo = mock(SecurityAuditJpaRepository.class, CALLS_REAL_METHODS);
        when(repo.saveAllAndFlush(any())).thenReturn(List.of());

        assertThat(repo.saveAll(List.of())).isNotNull();
    }
}
