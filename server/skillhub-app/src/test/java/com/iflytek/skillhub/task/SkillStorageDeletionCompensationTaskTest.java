package com.iflytek.skillhub.task;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.iflytek.skillhub.domain.skill.service.SkillStorageDeletionCompensationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SkillStorageDeletionCompensationTaskTest {

    @Mock
    private SkillStorageDeletionCompensationService compensationService;

    private SkillStorageDeletionCompensationTask task;

    @BeforeEach
    void setUp() {
        task = new SkillStorageDeletionCompensationTask(compensationService);
    }

    @Test
    void retryPendingCleanup_delegatesToCompensationService() {
        when(compensationService.retryPendingCleanup()).thenReturn(2);

        task.retryPendingCleanup();

        verify(compensationService).retryPendingCleanup();
    }
}
