package com.iflytek.skillhub.infra.jpa;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.iflytek.skillhub.domain.skill.Skill;
import com.iflytek.skillhub.domain.skill.SkillStatus;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

class JpaSkillRepositoryAdapterTest {

    private SkillJpaRepository delegate;
    private JpaSkillRepositoryAdapter adapter;

    @BeforeEach
    void setUp() {
        delegate = mock(SkillJpaRepository.class);
        adapter = new JpaSkillRepositoryAdapter(delegate);
    }

    @Test
    @SuppressWarnings("unchecked")
    void findById_shouldDelegateToJpaDelegate() {
        Skill skill = mock(Skill.class);
        doReturn(Optional.of(skill)).when((JpaRepository<Skill, Long>) delegate).findById(1L);

        Optional<Skill> result = adapter.findById(1L);

        assertThat(result).containsSame(skill);
    }

    @Test
    void findByIdIn_shouldDelegateToDelegate() {
        Skill skill = mock(Skill.class);
        doReturn(List.of(skill)).when(delegate).findByIdIn(List.of(1L));

        List<Skill> result = adapter.findByIdIn(List.of(1L));

        assertThat(result).containsExactly(skill);
    }

    @Test
    void findAll_shouldDelegateToJpaDelegate() {
        Skill skill = mock(Skill.class);
        doReturn(List.of(skill)).when(delegate).findAll();

        List<Skill> result = adapter.findAll();

        assertThat(result).containsExactly(skill);
    }

    @Test
    void findByNamespaceIdAndSlug_shouldDelegateToDelegate() {
        Skill skill = mock(Skill.class);
        doReturn(List.of(skill)).when(delegate).findByNamespaceIdAndSlug(1L, "slug");

        List<Skill> result = adapter.findByNamespaceIdAndSlug(1L, "slug");

        assertThat(result).containsExactly(skill);
    }

    @Test
    void findByNamespaceIdAndSlugAndOwnerId_shouldDelegateToDelegate() {
        Skill skill = mock(Skill.class);
        doReturn(Optional.of(skill)).when(delegate).findByNamespaceIdAndSlugAndOwnerId(1L, "slug", "owner");

        Optional<Skill> result = adapter.findByNamespaceIdAndSlugAndOwnerId(1L, "slug", "owner");

        assertThat(result).containsSame(skill);
    }

    @Test
    void findByNamespaceIdAndStatus_shouldDelegateToDelegate() {
        Skill skill = mock(Skill.class);
        doReturn(List.of(skill)).when(delegate).findByNamespaceIdAndStatus(1L, SkillStatus.ACTIVE);

        List<Skill> result = adapter.findByNamespaceIdAndStatus(1L, SkillStatus.ACTIVE);

        assertThat(result).containsExactly(skill);
    }

    @Test
    @SuppressWarnings("unchecked")
    void save_shouldDelegateToJpaDelegate() {
        Skill skill = mock(Skill.class);
        doReturn(skill).when((JpaRepository<Skill, Long>) delegate).save(skill);

        Skill result = adapter.save(skill);

        assertThat(result).isSameAs(skill);
    }

    @Test
    void flush_shouldDelegateToJpaDelegate() {
        adapter.flush();
        verify(delegate).flush();
    }

    @Test
    @SuppressWarnings("unchecked")
    void delete_shouldDelegateToJpaDelegate() {
        Skill skill = mock(Skill.class);
        doNothing().when((JpaRepository<Skill, Long>) delegate).delete(skill);
        adapter.delete(skill);
        verify((JpaRepository<Skill, Long>) delegate).delete(skill);
    }

    @Test
    void findByOwnerId_shouldDelegateToDelegate() {
        Skill skill = mock(Skill.class);
        doReturn(List.of(skill)).when(delegate).findByOwnerId("owner");

        List<Skill> result = adapter.findByOwnerId("owner");

        assertThat(result).containsExactly(skill);
    }

    @Test
    void findByOwnerId_withPageable_shouldDelegateToDelegate() {
        Skill skill = mock(Skill.class);
        Pageable pageable = PageRequest.of(0, 10);
        Page<Skill> page = new PageImpl<>(List.of(skill));
        doReturn(page).when(delegate).findByOwnerId("owner", pageable);

        Page<Skill> result = adapter.findByOwnerId("owner", pageable);

        assertThat(result.getContent()).containsExactly(skill);
    }

    @Test
    void incrementDownloadCount_shouldDelegateToDelegate() {
        adapter.incrementDownloadCount(1L);
        verify(delegate).incrementDownloadCount(1L);
    }

    @Test
    void findBySlug_shouldDelegateToDelegate() {
        Skill skill = mock(Skill.class);
        doReturn(List.of(skill)).when(delegate).findBySlug("slug");

        List<Skill> result = adapter.findBySlug("slug");

        assertThat(result).containsExactly(skill);
    }

    @Test
    void findByNamespaceSlugAndSlug_shouldDelegateToDelegate() {
        Skill skill = mock(Skill.class);
        doReturn(List.of(skill)).when(delegate).findByNamespaceSlugAndSlug("ns", "slug");

        List<Skill> result = adapter.findByNamespaceSlugAndSlug("ns", "slug");

        assertThat(result).containsExactly(skill);
    }
}
