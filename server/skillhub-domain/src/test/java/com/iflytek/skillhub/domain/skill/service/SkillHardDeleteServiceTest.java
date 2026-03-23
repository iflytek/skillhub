package com.iflytek.skillhub.domain.skill.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.iflytek.skillhub.domain.audit.AuditLogService;
import com.iflytek.skillhub.domain.report.SkillReportRepository;
import com.iflytek.skillhub.domain.review.PromotionRequestRepository;
import com.iflytek.skillhub.domain.review.ReviewTaskRepository;
import com.iflytek.skillhub.domain.security.SecurityScanService;
import com.iflytek.skillhub.domain.skill.Skill;
import com.iflytek.skillhub.domain.skill.SkillFile;
import com.iflytek.skillhub.domain.skill.SkillFileRepository;
import com.iflytek.skillhub.domain.skill.SkillRepository;
import com.iflytek.skillhub.domain.skill.SkillTagRepository;
import com.iflytek.skillhub.domain.skill.SkillVersion;
import com.iflytek.skillhub.domain.skill.SkillVersionRepository;
import com.iflytek.skillhub.domain.skill.SkillVersionStatsRepository;
import com.iflytek.skillhub.domain.skill.SkillVisibility;
import com.iflytek.skillhub.domain.social.SkillRatingRepository;
import com.iflytek.skillhub.domain.social.SkillStarRepository;
import com.iflytek.skillhub.storage.ObjectStorageService;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.InOrder;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class SkillHardDeleteServiceTest {

    @Mock
    private SkillRepository skillRepository;
    @Mock
    private SkillVersionRepository skillVersionRepository;
    @Mock
    private SkillFileRepository skillFileRepository;
    @Mock
    private SkillTagRepository skillTagRepository;
    @Mock
    private ReviewTaskRepository reviewTaskRepository;
    @Mock
    private PromotionRequestRepository promotionRequestRepository;
    @Mock
    private SkillStarRepository skillStarRepository;
    @Mock
    private SkillRatingRepository skillRatingRepository;
    @Mock
    private SkillReportRepository skillReportRepository;
    @Mock
    private SkillVersionStatsRepository skillVersionStatsRepository;
    @Mock
    private ObjectStorageService objectStorageService;
    @Mock
    private SkillStorageDeletionCompensationService compensationService;
    @Mock
    private SecurityScanService securityScanService;
    @Mock
    private AuditLogService auditLogService;

    private SkillHardDeleteService service;

    @AfterEach
    void tearDown() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @BeforeEach
    void setUp() {
        service = new SkillHardDeleteService(
                skillRepository,
                skillVersionRepository,
                skillFileRepository,
                skillTagRepository,
                reviewTaskRepository,
                promotionRequestRepository,
                skillStarRepository,
                skillRatingRepository,
                skillReportRepository,
                skillVersionStatsRepository,
                objectStorageService,
                compensationService,
                securityScanService,
                auditLogService,
                new ObjectMapper()
        );
    }

    @Test
    void hardDeleteSkill_removesStorageArtifactsAndRelatedRecords() {
        Skill skill = new Skill(9L, "demo-skill", "owner-1", SkillVisibility.PUBLIC);
        setField(skill, "id", 7L);
        skill.setLatestVersionId(22L);

        SkillVersion pending = new SkillVersion(7L, "1.0.0-rc1", "owner-1");
        setField(pending, "id", 21L);
        SkillVersion published = new SkillVersion(7L, "1.0.0", "owner-1");
        setField(published, "id", 22L);

        given(skillVersionRepository.findBySkillId(7L)).willReturn(List.of(pending, published));
        given(skillFileRepository.findByVersionId(21L)).willReturn(List.of(
                new SkillFile(21L, "SKILL.md", 12L, "text/markdown", "sha1", "skills/7/21/SKILL.md")
        ));
        given(skillFileRepository.findByVersionId(22L)).willReturn(List.of(
                new SkillFile(22L, "README.md", 20L, "text/markdown", "sha2", "skills/7/22/README.md")
        ));

        service.hardDeleteSkill(skill, "global", "super-1", "127.0.0.1", "JUnit");

        InOrder inOrder = inOrder(skillRepository, skillVersionRepository);
        inOrder.verify(skillRepository).save(skill);
        inOrder.verify(skillRepository).flush();
        inOrder.verify(skillVersionRepository).deleteBySkillId(7L);
        verify(reviewTaskRepository).deleteBySkillVersionIdIn(List.of(21L, 22L));
        verify(promotionRequestRepository).deleteBySourceSkillIdOrTargetSkillId(7L, 7L);
        verify(skillTagRepository).deleteBySkillId(7L);
        verify(skillStarRepository).deleteBySkillId(7L);
        verify(skillRatingRepository).deleteBySkillId(7L);
        verify(skillReportRepository).deleteBySkillId(7L);
        verify(skillVersionStatsRepository).deleteBySkillId(7L);
        verify(objectStorageService).deleteObjects(argThat(keys ->
                keys.contains("skills/7/21/SKILL.md")
                        && keys.contains("skills/7/22/README.md")
                        && keys.contains("packages/7/21/bundle.zip")
                        && keys.contains("packages/7/22/bundle.zip")
                        && keys.size() == 4));
        verify(skillFileRepository).deleteByVersionId(21L);
        verify(skillFileRepository).deleteByVersionId(22L);
        verify(securityScanService).softDeleteByVersionId(21L);
        verify(securityScanService).softDeleteByVersionId(22L);
        verify(skillVersionRepository).deleteBySkillId(7L);
        verify(skillRepository).delete(skill);
        verify(auditLogService).record(
                "super-1",
                "DELETE_SKILL_HARD",
                "SKILL",
                7L,
                null,
                "127.0.0.1",
                "JUnit",
                "{\"namespaceId\":9,\"slug\":\"demo-skill\"}"
        );
    }

    @Test
    void hardDeleteSkill_deletesStorageAfterCommitWhenSynchronizationIsActive() {
        Skill skill = new Skill(9L, "demo-skill", "owner-1", SkillVisibility.PUBLIC);
        setField(skill, "id", 7L);
        SkillVersion version = new SkillVersion(7L, "1.0.0", "owner-1");
        setField(version, "id", 22L);

        given(skillVersionRepository.findBySkillId(7L)).willReturn(List.of(version));
        given(skillFileRepository.findByVersionId(22L)).willReturn(List.of(
                new SkillFile(22L, "README.md", 20L, "text/markdown", "sha2", "skills/7/22/README.md")
        ));

        TransactionSynchronizationManager.initSynchronization();

        service.hardDeleteSkill(skill, "global", "super-1", "127.0.0.1", "JUnit");

        verify(objectStorageService, never()).deleteObjects(argThat(keys -> !keys.isEmpty()));

        for (TransactionSynchronization synchronization : TransactionSynchronizationManager.getSynchronizations()) {
            synchronization.afterCommit();
        }

        verify(objectStorageService).deleteObjects(argThat(keys ->
                keys.contains("skills/7/22/README.md")
                        && keys.contains("packages/7/22/bundle.zip")
                        && keys.size() == 2));
    }

    @Test
    void hardDeleteSkill_recordsCompensationWhenStorageDeleteFails() {
        Skill skill = new Skill(9L, "demo-skill", "owner-1", SkillVisibility.PUBLIC);
        setField(skill, "id", 7L);
        SkillVersion version = new SkillVersion(7L, "1.0.0", "owner-1");
        setField(version, "id", 22L);

        given(skillVersionRepository.findBySkillId(7L)).willReturn(List.of(version));
        given(skillFileRepository.findByVersionId(22L)).willReturn(List.of(
                new SkillFile(22L, "README.md", 20L, "text/markdown", "sha2", "skills/7/22/README.md")
        ));
        doThrow(new RuntimeException("s3 down")).when(objectStorageService).deleteObjects(anyList());

        TransactionSynchronizationManager.initSynchronization();

        service.hardDeleteSkill(skill, "global", "super-1", "127.0.0.1", "JUnit");

        for (TransactionSynchronization synchronization : TransactionSynchronizationManager.getSynchronizations()) {
            synchronization.afterCommit();
        }

        verify(compensationService).recordFailure(
                org.mockito.ArgumentMatchers.eq(7L),
                org.mockito.ArgumentMatchers.eq("global"),
                org.mockito.ArgumentMatchers.eq("demo-skill"),
                argThat(keys -> keys.contains("skills/7/22/README.md") && keys.contains("packages/7/22/bundle.zip")),
                org.mockito.ArgumentMatchers.contains("s3 down")
        );
    }

    private void setField(Object target, String fieldName, Object value) {
        try {
            java.lang.reflect.Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }
}
