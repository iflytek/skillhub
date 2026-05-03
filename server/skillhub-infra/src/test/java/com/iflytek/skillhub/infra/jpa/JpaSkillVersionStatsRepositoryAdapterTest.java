package com.iflytek.skillhub.infra.jpa;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.iflytek.skillhub.domain.skill.SkillVersionStats;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class JpaSkillVersionStatsRepositoryAdapterTest {

    @Test
    void findBySkillVersionIdShouldDelegateToJpaRepository() {
        SkillVersionStatsJpaRepository delegate = mock(SkillVersionStatsJpaRepository.class);
        SkillVersionStats stats = new SkillVersionStats(100L, 200L);
        when(delegate.findById(100L)).thenReturn(Optional.of(stats));

        JpaSkillVersionStatsRepositoryAdapter adapter = new JpaSkillVersionStatsRepositoryAdapter(delegate);

        assertThat(adapter.findBySkillVersionId(100L)).containsSame(stats);
    }

    @Test
    void incrementDownloadCountShouldReturnAfterUpdateOrInsertAndRetryAsLastResort() {
        SkillVersionStatsJpaRepository delegate = mock(SkillVersionStatsJpaRepository.class);
        JpaSkillVersionStatsRepositoryAdapter adapter = new JpaSkillVersionStatsRepositoryAdapter(delegate);

        when(delegate.incrementExistingDownloadCount(1L)).thenReturn(1);
        adapter.incrementDownloadCount(1L, 10L);
        verify(delegate).incrementExistingDownloadCount(1L);

        when(delegate.incrementExistingDownloadCount(2L)).thenReturn(0);
        when(delegate.insertInitialDownloadCount(2L, 20L)).thenReturn(1);
        adapter.incrementDownloadCount(2L, 20L);
        verify(delegate).insertInitialDownloadCount(2L, 20L);

        when(delegate.incrementExistingDownloadCount(3L)).thenReturn(0, 1);
        when(delegate.insertInitialDownloadCount(3L, 30L)).thenReturn(0);
        adapter.incrementDownloadCount(3L, 30L);
        verify(delegate, org.mockito.Mockito.times(2)).incrementExistingDownloadCount(3L);
        verify(delegate).insertInitialDownloadCount(3L, 30L);
    }

    @Test
    void deleteBySkillIdShouldDelegate() {
        SkillVersionStatsJpaRepository delegate = mock(SkillVersionStatsJpaRepository.class);
        JpaSkillVersionStatsRepositoryAdapter adapter = new JpaSkillVersionStatsRepositoryAdapter(delegate);

        adapter.deleteBySkillId(88L);

        verify(delegate).deleteBySkillId(88L);
    }
}
