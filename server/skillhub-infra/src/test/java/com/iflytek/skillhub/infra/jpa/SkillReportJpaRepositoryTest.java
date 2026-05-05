package com.iflytek.skillhub.infra.jpa;

import com.iflytek.skillhub.domain.report.SkillReportStatus;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SkillReportJpaRepositoryTest {

    @Test
    void findByStatus_delegatesToOrderedVariant() {
        SkillReportJpaRepository repo = mock(SkillReportJpaRepository.class, CALLS_REAL_METHODS);
        when(repo.findByStatusOrderByCreatedAtDesc(any(), any())).thenReturn(org.springframework.data.domain.Page.empty());

        assertThat(repo.findByStatus(SkillReportStatus.PENDING, PageRequest.of(0, 10))).isNotNull();
    }
}
