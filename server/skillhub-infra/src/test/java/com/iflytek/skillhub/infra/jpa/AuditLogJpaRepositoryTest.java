package com.iflytek.skillhub.infra.jpa;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.iflytek.skillhub.domain.audit.AuditLog;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

class AuditLogJpaRepositoryTest {

    @Test
    @SuppressWarnings("unchecked")
    void search_withBothFilters_shouldApplySpecification() {
        AuditLogJpaRepository repo = mock(AuditLogJpaRepository.class, CALLS_REAL_METHODS);
        AuditLog log = mock(AuditLog.class);
        Pageable pageable = PageRequest.of(0, 10);
        Page<AuditLog> page = new PageImpl<>(List.of(log));

        when(repo.findAll(any(Specification.class), any(Pageable.class))).thenReturn(page);

        Page<AuditLog> result = repo.search("user1", "CREATE", pageable);

        assertThat(result.getContent()).hasSize(1);
    }

    @Test
    @SuppressWarnings("unchecked")
    void search_withOnlyActorUserId_shouldApplySpecification() {
        AuditLogJpaRepository repo = mock(AuditLogJpaRepository.class, CALLS_REAL_METHODS);
        AuditLog log = mock(AuditLog.class);
        Pageable pageable = PageRequest.of(0, 10);
        Page<AuditLog> page = new PageImpl<>(List.of(log));

        when(repo.findAll(any(Specification.class), any(Pageable.class))).thenReturn(page);

        Page<AuditLog> result = repo.search("user1", null, pageable);

        assertThat(result.getContent()).hasSize(1);
    }

    @Test
    @SuppressWarnings("unchecked")
    void search_withOnlyAction_shouldApplySpecification() {
        AuditLogJpaRepository repo = mock(AuditLogJpaRepository.class, CALLS_REAL_METHODS);
        AuditLog log = mock(AuditLog.class);
        Pageable pageable = PageRequest.of(0, 10);
        Page<AuditLog> page = new PageImpl<>(List.of(log));

        when(repo.findAll(any(Specification.class), any(Pageable.class))).thenReturn(page);

        Page<AuditLog> result = repo.search(null, "DELETE", pageable);

        assertThat(result.getContent()).hasSize(1);
    }

    @Test
    @SuppressWarnings("unchecked")
    void search_withNoFilters_shouldReturnAll() {
        AuditLogJpaRepository repo = mock(AuditLogJpaRepository.class, CALLS_REAL_METHODS);
        AuditLog log = mock(AuditLog.class);
        Pageable pageable = PageRequest.of(0, 10);
        Page<AuditLog> page = new PageImpl<>(List.of(log));

        when(repo.findAll(any(Specification.class), any(Pageable.class))).thenReturn(page);

        Page<AuditLog> result = repo.search(null, null, pageable);

        assertThat(result.getContent()).hasSize(1);
    }

    @Test
    @SuppressWarnings("unchecked")
    void search_withBlankFilters_shouldReturnAll() {
        AuditLogJpaRepository repo = mock(AuditLogJpaRepository.class, CALLS_REAL_METHODS);
        AuditLog log = mock(AuditLog.class);
        Pageable pageable = PageRequest.of(0, 10);
        Page<AuditLog> page = new PageImpl<>(List.of(log));

        when(repo.findAll(any(Specification.class), any(Pageable.class))).thenReturn(page);

        Page<AuditLog> result = repo.search("   ", "   ", pageable);

        assertThat(result.getContent()).hasSize(1);
    }
}
