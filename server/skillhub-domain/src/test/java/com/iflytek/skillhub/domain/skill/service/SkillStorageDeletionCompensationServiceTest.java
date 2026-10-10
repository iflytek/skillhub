package com.iflytek.skillhub.domain.skill.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.iflytek.skillhub.domain.skill.SkillStorageDeletionCompensation;
import com.iflytek.skillhub.domain.skill.SkillStorageDeletionCompensationRepository;
import com.iflytek.skillhub.domain.skill.SkillStorageDeletionCompensationStatus;
import com.iflytek.skillhub.storage.ObjectStorageService;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SkillStorageDeletionCompensationServiceTest {

    @Mock
    private SkillStorageDeletionCompensationRepository repository;
    @Mock
    private ObjectStorageService objectStorageService;

    private SkillStorageDeletionCompensationService service;

    @BeforeEach
    void setUp() {
        service = new SkillStorageDeletionCompensationService(repository, objectStorageService, new ObjectMapper());
    }

    @Test
    void recordFailure_persistsPendingKeysAndError() {
        service.recordFailure(11L, "global", "demo-skill", List.of("skills/11/101/SKILL.md"), "boom");

        ArgumentCaptor<SkillStorageDeletionCompensation> captor =
                ArgumentCaptor.forClass(SkillStorageDeletionCompensation.class);
        verify(repository).save(captor.capture());
        SkillStorageDeletionCompensation saved = captor.getValue();
        assertThat(saved.getSkillId()).isEqualTo(11L);
        assertThat(saved.getNamespace()).isEqualTo("global");
        assertThat(saved.getSlug()).isEqualTo("demo-skill");
        assertThat(saved.getStorageKeysJson()).contains("SKILL.md");
        assertThat(saved.getLastError()).isEqualTo("boom");
        assertThat(saved.getStatus()).isEqualTo(SkillStorageDeletionCompensationStatus.PENDING);
    }

    @Test
    void retryPendingCleanup_marksCompletedAfterSuccessfulDelete() {
        SkillStorageDeletionCompensation record = new SkillStorageDeletionCompensation(
                11L,
                "global",
                "demo-skill",
                "[\"skills/11/101/SKILL.md\"]",
                "boom"
        );
        given(repository.findTop100ByStatusOrderByCreatedAtAsc(SkillStorageDeletionCompensationStatus.PENDING))
                .willReturn(List.of(record));

        int retried = service.retryPendingCleanup();

        assertThat(retried).isEqualTo(1);
        verify(objectStorageService).deleteObjects(List.of("skills/11/101/SKILL.md"));
        assertThat(record.getStatus()).isEqualTo(SkillStorageDeletionCompensationStatus.COMPLETED);
    }

    @Test
    void retryPendingCleanup_keepsPendingAndTracksAttemptWhenDeleteFails() {
        SkillStorageDeletionCompensation record = new SkillStorageDeletionCompensation(
                11L,
                "global",
                "demo-skill",
                "[\"skills/11/101/SKILL.md\"]",
                "boom"
        );
        given(repository.findTop100ByStatusOrderByCreatedAtAsc(SkillStorageDeletionCompensationStatus.PENDING))
                .willReturn(List.of(record));
        doThrow(new RuntimeException("s3 down")).when(objectStorageService).deleteObjects(anyList());

        int retried = service.retryPendingCleanup();

        assertThat(retried).isEqualTo(1);
        assertThat(record.getStatus()).isEqualTo(SkillStorageDeletionCompensationStatus.PENDING);
        assertThat(record.getAttemptCount()).isEqualTo(1);
        assertThat(record.getLastError()).contains("s3 down");
        verify(repository).findTop100ByStatusOrderByCreatedAtAsc(SkillStorageDeletionCompensationStatus.PENDING);
    }
}
