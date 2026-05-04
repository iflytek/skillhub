package com.iflytek.skillhub.infra.jpa;

import com.iflytek.skillhub.domain.skill.SkillStorageDeletionCompensation;
import com.iflytek.skillhub.domain.skill.SkillStorageDeletionCompensationStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class JpaSkillStorageDeletionCompensationRepositoryAdapterTest {

    @Mock
    private SkillStorageDeletionCompensationJpaRepository delegate;

    private JpaSkillStorageDeletionCompensationRepositoryAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new JpaSkillStorageDeletionCompensationRepositoryAdapter(delegate);
    }

    @Test
    void save_shouldDelegateToJpaRepository() {
        SkillStorageDeletionCompensation compensation = mock(SkillStorageDeletionCompensation.class);
        when(delegate.save(compensation)).thenReturn(compensation);

        SkillStorageDeletionCompensation result = adapter.save(compensation);

        assertEquals(compensation, result);
        verify(delegate).save(compensation);
    }

    @Test
    void findTop100ByStatusOrderByCreatedAtAsc_shouldDelegateToJpaRepository() {
        SkillStorageDeletionCompensation compensation = mock(SkillStorageDeletionCompensation.class);
        when(delegate.findTop100ByStatusOrderByCreatedAtAsc(SkillStorageDeletionCompensationStatus.PENDING))
                .thenReturn(List.of(compensation));

        List<SkillStorageDeletionCompensation> result =
                adapter.findTop100ByStatusOrderByCreatedAtAsc(SkillStorageDeletionCompensationStatus.PENDING);

        assertEquals(1, result.size());
        verify(delegate).findTop100ByStatusOrderByCreatedAtAsc(SkillStorageDeletionCompensationStatus.PENDING);
    }
}
