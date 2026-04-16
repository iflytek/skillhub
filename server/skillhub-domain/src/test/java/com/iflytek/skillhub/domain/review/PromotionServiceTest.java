package com.iflytek.skillhub.domain.review;

import com.iflytek.skillhub.domain.event.SkillPublishedEvent;
import com.iflytek.skillhub.domain.governance.GovernanceNotificationService;
import com.iflytek.skillhub.domain.namespace.Namespace;
import com.iflytek.skillhub.domain.namespace.NamespaceRepository;
import com.iflytek.skillhub.domain.namespace.NamespaceStatus;
import com.iflytek.skillhub.domain.namespace.NamespaceType;
import com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException;
import com.iflytek.skillhub.domain.shared.exception.DomainForbiddenException;
import com.iflytek.skillhub.domain.shared.exception.DomainNotFoundException;
import com.iflytek.skillhub.domain.skill.*;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PromotionServiceTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-03-18T11:00:00Z"), ZoneOffset.UTC);

    @Mock private PromotionRequestRepository promotionRequestRepository;
    @Mock private SkillRepository skillRepository;
    @Mock private SkillVersionRepository skillVersionRepository;
    @Mock private SkillFileRepository skillFileRepository;
    @Mock private NamespaceRepository namespaceRepository;
    @Mock private ReviewPermissionChecker permissionChecker;
    @Mock private ApplicationEventPublisher eventPublisher;
    @Mock private GovernanceNotificationService governanceNotificationService;
    @Mock private EntityManager entityManager;

    private PromotionService promotionService;

    private static final Long SOURCE_SKILL_ID = 10L;
    private static final Long SOURCE_VERSION_ID = 20L;
    private static final Long TARGET_NAMESPACE_ID = 30L;
    private static final String USER_ID = "user-100";
    private static final String REVIEWER_ID = "user-200";
    private static final Long PROMOTION_ID = 1L;
    private static final Long NEW_SKILL_ID = 50L;
    private static final Long NEW_VERSION_ID = 60L;

    @BeforeEach
    void setUp() {
        promotionService = new PromotionService(
                promotionRequestRepository, skillRepository, skillVersionRepository,
                skillFileRepository, namespaceRepository, permissionChecker, eventPublisher, governanceNotificationService, entityManager, CLOCK);
    }

    private static void setField(Object target, String fieldName, Object value) {
        try {
            java.lang.reflect.Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private Skill createSourceSkill() {
        Skill skill = new Skill(5L, "my-skill", USER_ID, SkillVisibility.NAMESPACE_ONLY);
        setField(skill, "id", SOURCE_SKILL_ID);
        skill.setDisplayName("My Skill");
        skill.setSummary("A test skill");
        return skill;
    }

    private SkillVersion createPublishedVersion() {
        SkillVersion sv = new SkillVersion(SOURCE_SKILL_ID, "1.0.0", USER_ID);
        setField(sv, "id", SOURCE_VERSION_ID);
        sv.setStatus(SkillVersionStatus.PUBLISHED);
        sv.setChangelog("Initial release");
        sv.setParsedMetadataJson("{\"name\":\"test\"}");
        sv.setManifestJson("{\"version\":\"1.0.0\"}");
        sv.setFileCount(3);
        sv.setTotalSize(1024L);
        return sv;
    }

    private Namespace createGlobalNamespace() {
        Namespace ns = new Namespace("global", "Global", "user-1");
        setField(ns, "id", TARGET_NAMESPACE_ID);
        ns.setType(NamespaceType.GLOBAL);
        return ns;
    }

    private Namespace createTeamNamespace() {
        Namespace ns = new Namespace("team-a", "Team A", "user-1");
        setField(ns, "id", TARGET_NAMESPACE_ID);
        // default type is TEAM
        return ns;
    }

    private Namespace createSourceNamespace() {
        Namespace ns = new Namespace("team-a", "Team A", "user-1");
        setField(ns, "id", 5L);
        return ns;
    }

    private PromotionRequest createPendingPromotion() {
        PromotionRequest pr = new PromotionRequest(SOURCE_SKILL_ID, SOURCE_VERSION_ID, TARGET_NAMESPACE_ID, USER_ID);
        setField(pr, "id", PROMOTION_ID);
        return pr;
    }

    private List<SkillFile> createSourceFiles() {
        return List.of(
                new SkillFile(SOURCE_VERSION_ID, "main.py", 500L, "text/x-python", "sha1", "storage/key1"),
                new SkillFile(SOURCE_VERSION_ID, "config.json", 200L, "application/json", "sha2", "storage/key2")
        );
    }

    @Nested
    class SubmitPromotion {

        @Test
        void shouldSubmitPromotionSuccessfully() {
            Skill sourceSkill = createSourceSkill();
            SkillVersion sourceVersion = createPublishedVersion();
            Namespace globalNs = createGlobalNamespace();

            when(skillRepository.findById(SOURCE_SKILL_ID)).thenReturn(Optional.of(sourceSkill));
            when(skillVersionRepository.findById(SOURCE_VERSION_ID)).thenReturn(Optional.of(sourceVersion));
            when(namespaceRepository.findById(sourceSkill.getNamespaceId())).thenReturn(Optional.of(createSourceNamespace()));
            when(permissionChecker.canSubmitPromotion(sourceSkill, USER_ID, Map.of())).thenReturn(true);
            when(namespaceRepository.findById(TARGET_NAMESPACE_ID)).thenReturn(Optional.of(globalNs));
            when(promotionRequestRepository.findBySourceSkillIdAndStatus(SOURCE_SKILL_ID, ReviewTaskStatus.PENDING))
                    .thenReturn(Optional.empty());
            when(promotionRequestRepository.findBySourceSkillIdAndStatus(SOURCE_SKILL_ID, ReviewTaskStatus.APPROVED))
                    .thenReturn(Optional.empty());
            when(promotionRequestRepository.save(any(PromotionRequest.class)))
                    .thenAnswer(inv -> {
                        PromotionRequest pr = inv.getArgument(0);
                        setField(pr, "id", PROMOTION_ID);
                        return pr;
                    });

            PromotionRequest result = promotionService.submitPromotion(
                    SOURCE_SKILL_ID, SOURCE_VERSION_ID, TARGET_NAMESPACE_ID, USER_ID, Map.of());

            assertNotNull(result);
            assertEquals(SOURCE_SKILL_ID, result.getSourceSkillId());
            assertEquals(SOURCE_VERSION_ID, result.getSourceVersionId());
            assertEquals(TARGET_NAMESPACE_ID, result.getTargetNamespaceId());
            assertEquals(USER_ID, result.getSubmittedBy());
            verify(promotionRequestRepository).save(any(PromotionRequest.class));
        }

        @Test
        void shouldThrowWhenSourceSkillNotFound() {
            when(skillRepository.findById(SOURCE_SKILL_ID)).thenReturn(Optional.empty());

            assertThrows(DomainNotFoundException.class,
                    () -> promotionService.submitPromotion(SOURCE_SKILL_ID, SOURCE_VERSION_ID, TARGET_NAMESPACE_ID, USER_ID, Map.of()));
        }

        @Test
        void shouldThrowWhenSourceVersionNotFound() {
            when(skillRepository.findById(SOURCE_SKILL_ID)).thenReturn(Optional.of(createSourceSkill()));
            when(skillVersionRepository.findById(SOURCE_VERSION_ID)).thenReturn(Optional.empty());

            assertThrows(DomainNotFoundException.class,
                    () -> promotionService.submitPromotion(SOURCE_SKILL_ID, SOURCE_VERSION_ID, TARGET_NAMESPACE_ID, USER_ID, Map.of()));
        }

        @Test
        void shouldThrowWhenVersionDoesNotBelongToSkill() {
            Skill sourceSkill = createSourceSkill();
            SkillVersion sv = createPublishedVersion();
            setField(sv, "skillId", 999L); // different skill

            when(skillRepository.findById(SOURCE_SKILL_ID)).thenReturn(Optional.of(sourceSkill));
            when(skillVersionRepository.findById(SOURCE_VERSION_ID)).thenReturn(Optional.of(sv));

            assertThrows(DomainBadRequestException.class,
                    () -> promotionService.submitPromotion(SOURCE_SKILL_ID, SOURCE_VERSION_ID, TARGET_NAMESPACE_ID, USER_ID, Map.of()));
        }

        @Test
        void shouldThrowWhenVersionNotPublished() {
            Skill sourceSkill = createSourceSkill();
            SkillVersion sv = createPublishedVersion();
            sv.setStatus(SkillVersionStatus.DRAFT);

            when(skillRepository.findById(SOURCE_SKILL_ID)).thenReturn(Optional.of(sourceSkill));
            when(skillVersionRepository.findById(SOURCE_VERSION_ID)).thenReturn(Optional.of(sv));

            assertThrows(DomainBadRequestException.class,
                    () -> promotionService.submitPromotion(SOURCE_SKILL_ID, SOURCE_VERSION_ID, TARGET_NAMESPACE_ID, USER_ID, Map.of()));
        }

        @Test
        void shouldThrowWhenTargetNamespaceNotFound() {
            Skill sourceSkill = createSourceSkill();
            when(skillRepository.findById(SOURCE_SKILL_ID)).thenReturn(Optional.of(sourceSkill));
            when(skillVersionRepository.findById(SOURCE_VERSION_ID)).thenReturn(Optional.of(createPublishedVersion()));
            when(namespaceRepository.findById(sourceSkill.getNamespaceId())).thenReturn(Optional.of(createSourceNamespace()));
            when(permissionChecker.canSubmitPromotion(sourceSkill, USER_ID, Map.of())).thenReturn(true);
            when(namespaceRepository.findById(TARGET_NAMESPACE_ID)).thenReturn(Optional.empty());

            assertThrows(DomainNotFoundException.class,
                    () -> promotionService.submitPromotion(SOURCE_SKILL_ID, SOURCE_VERSION_ID, TARGET_NAMESPACE_ID, USER_ID, Map.of()));
        }

        @Test
        void shouldThrowWhenTargetNamespaceNotGlobal() {
            Skill sourceSkill = createSourceSkill();
            when(skillRepository.findById(SOURCE_SKILL_ID)).thenReturn(Optional.of(sourceSkill));
            when(skillVersionRepository.findById(SOURCE_VERSION_ID)).thenReturn(Optional.of(createPublishedVersion()));
            when(namespaceRepository.findById(sourceSkill.getNamespaceId())).thenReturn(Optional.of(createSourceNamespace()));
            when(permissionChecker.canSubmitPromotion(sourceSkill, USER_ID, Map.of())).thenReturn(true);
            when(namespaceRepository.findById(TARGET_NAMESPACE_ID)).thenReturn(Optional.of(createTeamNamespace()));

            assertThrows(DomainBadRequestException.class,
                    () -> promotionService.submitPromotion(SOURCE_SKILL_ID, SOURCE_VERSION_ID, TARGET_NAMESPACE_ID, USER_ID, Map.of()));
        }

        @Test
        void shouldThrowWhenDuplicatePendingExists() {
            Skill sourceSkill = createSourceSkill();
            when(skillRepository.findById(SOURCE_SKILL_ID)).thenReturn(Optional.of(sourceSkill));
            when(skillVersionRepository.findById(SOURCE_VERSION_ID)).thenReturn(Optional.of(createPublishedVersion()));
            when(namespaceRepository.findById(sourceSkill.getNamespaceId())).thenReturn(Optional.of(createSourceNamespace()));
            when(permissionChecker.canSubmitPromotion(sourceSkill, USER_ID, Map.of())).thenReturn(true);
            when(namespaceRepository.findById(TARGET_NAMESPACE_ID)).thenReturn(Optional.of(createGlobalNamespace()));
            when(promotionRequestRepository.findBySourceSkillIdAndStatus(SOURCE_SKILL_ID, ReviewTaskStatus.PENDING))
                    .thenReturn(Optional.of(createPendingPromotion()));

            assertThrows(DomainBadRequestException.class,
                    () -> promotionService.submitPromotion(SOURCE_SKILL_ID, SOURCE_VERSION_ID, TARGET_NAMESPACE_ID, USER_ID, Map.of()));
        }

        @Test
        void shouldThrowWhenSkillAlreadyPromoted() {
            Skill sourceSkill = createSourceSkill();
            PromotionRequest approvedPromotion = createPendingPromotion();
            setField(approvedPromotion, "status", ReviewTaskStatus.APPROVED);

            when(skillRepository.findById(SOURCE_SKILL_ID)).thenReturn(Optional.of(sourceSkill));
            when(skillVersionRepository.findById(SOURCE_VERSION_ID)).thenReturn(Optional.of(createPublishedVersion()));
            when(namespaceRepository.findById(sourceSkill.getNamespaceId())).thenReturn(Optional.of(createSourceNamespace()));
            when(permissionChecker.canSubmitPromotion(sourceSkill, USER_ID, Map.of())).thenReturn(true);
            when(namespaceRepository.findById(TARGET_NAMESPACE_ID)).thenReturn(Optional.of(createGlobalNamespace()));
            when(promotionRequestRepository.findBySourceSkillIdAndStatus(SOURCE_SKILL_ID, ReviewTaskStatus.PENDING))
                    .thenReturn(Optional.empty());
            when(promotionRequestRepository.findBySourceSkillIdAndStatus(SOURCE_SKILL_ID, ReviewTaskStatus.APPROVED))
                    .thenReturn(Optional.of(approvedPromotion));

            assertThrows(DomainBadRequestException.class,
                    () -> promotionService.submitPromotion(SOURCE_SKILL_ID, SOURCE_VERSION_ID, TARGET_NAMESPACE_ID, USER_ID, Map.of()));
        }

        @Test
        void shouldThrowWhenSubmitterIsNotOwnerOrNamespaceAdmin() {
            Skill sourceSkill = createSourceSkill();
            SkillVersion sourceVersion = createPublishedVersion();

            when(skillRepository.findById(SOURCE_SKILL_ID)).thenReturn(Optional.of(sourceSkill));
            when(skillVersionRepository.findById(SOURCE_VERSION_ID)).thenReturn(Optional.of(sourceVersion));
            when(namespaceRepository.findById(sourceSkill.getNamespaceId())).thenReturn(Optional.of(createSourceNamespace()));
            when(permissionChecker.canSubmitPromotion(
                    sourceSkill,
                    "user-999",
                    Map.of(sourceSkill.getNamespaceId(), com.iflytek.skillhub.domain.namespace.NamespaceRole.MEMBER)))
                    .thenReturn(false);

            assertThrows(DomainForbiddenException.class,
                    () -> promotionService.submitPromotion(
                            SOURCE_SKILL_ID,
                            SOURCE_VERSION_ID,
                            TARGET_NAMESPACE_ID,
                            "user-999",
                            Map.of(sourceSkill.getNamespaceId(), com.iflytek.skillhub.domain.namespace.NamespaceRole.MEMBER)
                    ));
            verify(promotionRequestRepository, never()).save(any(PromotionRequest.class));
        }

        @Test
        void shouldAllowNamespaceAdminToSubmitPromotionForForeignSkill() {
            Skill sourceSkill = createSourceSkill();
            SkillVersion sourceVersion = createPublishedVersion();
            Namespace globalNs = createGlobalNamespace();

            when(skillRepository.findById(SOURCE_SKILL_ID)).thenReturn(Optional.of(sourceSkill));
            when(skillVersionRepository.findById(SOURCE_VERSION_ID)).thenReturn(Optional.of(sourceVersion));
            when(namespaceRepository.findById(sourceSkill.getNamespaceId())).thenReturn(Optional.of(createSourceNamespace()));
            when(permissionChecker.canSubmitPromotion(
                    sourceSkill,
                    "user-999",
                    Map.of(sourceSkill.getNamespaceId(), com.iflytek.skillhub.domain.namespace.NamespaceRole.ADMIN)))
                    .thenReturn(true);
            when(namespaceRepository.findById(TARGET_NAMESPACE_ID)).thenReturn(Optional.of(globalNs));
            when(promotionRequestRepository.findBySourceSkillIdAndStatus(SOURCE_SKILL_ID, ReviewTaskStatus.PENDING))
                    .thenReturn(Optional.empty());
            when(promotionRequestRepository.findBySourceSkillIdAndStatus(SOURCE_SKILL_ID, ReviewTaskStatus.APPROVED))
                    .thenReturn(Optional.empty());
            when(promotionRequestRepository.save(any(PromotionRequest.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            PromotionRequest result = promotionService.submitPromotion(
                    SOURCE_SKILL_ID,
                    SOURCE_VERSION_ID,
                    TARGET_NAMESPACE_ID,
                    "user-999",
                    Map.of(sourceSkill.getNamespaceId(), com.iflytek.skillhub.domain.namespace.NamespaceRole.ADMIN)
            );

            assertNotNull(result);
        }

        @Test
        void shouldRejectSubmitWhenSourceNamespaceFrozen() {
            Skill sourceSkill = createSourceSkill();
            SkillVersion sourceVersion = createPublishedVersion();
            Namespace sourceNamespace = new Namespace("team-a", "Team A", "user-1");
            setField(sourceNamespace, "id", sourceSkill.getNamespaceId());
            sourceNamespace.setStatus(NamespaceStatus.FROZEN);

            when(skillRepository.findById(SOURCE_SKILL_ID)).thenReturn(Optional.of(sourceSkill));
            when(skillVersionRepository.findById(SOURCE_VERSION_ID)).thenReturn(Optional.of(sourceVersion));
            when(namespaceRepository.findById(sourceSkill.getNamespaceId())).thenReturn(Optional.of(sourceNamespace));

            assertThrows(DomainBadRequestException.class,
                    () -> promotionService.submitPromotion(SOURCE_SKILL_ID, SOURCE_VERSION_ID, TARGET_NAMESPACE_ID, USER_ID, Map.of()));
        }
    }

    @Nested
    class ReviewPromotion {

        @Test
        void shouldNotifySubmitterWhenPromotionApproved() {
            PromotionRequest request = createPendingPromotion();
            Skill sourceSkill = createSourceSkill();
            SkillVersion sourceVersion = createPublishedVersion();
            Skill newSkill = new Skill(TARGET_NAMESPACE_ID, "my-skill", REVIEWER_ID, SkillVisibility.PUBLIC);
            setField(newSkill, "id", NEW_SKILL_ID);
            SkillVersion newVersion = new SkillVersion(NEW_SKILL_ID, sourceVersion.getVersion(), REVIEWER_ID);
            setField(newVersion, "id", NEW_VERSION_ID);

            when(promotionRequestRepository.findById(PROMOTION_ID)).thenReturn(Optional.of(request));
            when(permissionChecker.canReviewPromotion(request, REVIEWER_ID, Set.of("SKILL_ADMIN"))).thenReturn(true);
            when(promotionRequestRepository.updateStatusWithVersion(
                    PROMOTION_ID, ReviewTaskStatus.APPROVED, REVIEWER_ID, "ok", null, request.getVersion()))
                    .thenReturn(1);
            when(skillRepository.findById(SOURCE_SKILL_ID)).thenReturn(Optional.of(sourceSkill));
            when(skillVersionRepository.findById(SOURCE_VERSION_ID)).thenReturn(Optional.of(sourceVersion));
            when(skillRepository.save(any(Skill.class))).thenReturn(newSkill);
            when(skillVersionRepository.save(any(SkillVersion.class))).thenReturn(newVersion);
            when(skillFileRepository.findByVersionId(SOURCE_VERSION_ID)).thenReturn(List.of());

            promotionService.approvePromotion(PROMOTION_ID, REVIEWER_ID, "ok", Set.of("SKILL_ADMIN"));

            verify(governanceNotificationService).notifyUser(eq(USER_ID), eq("PROMOTION"), eq("PROMOTION_REQUEST"), eq(PROMOTION_ID), eq("Promotion approved"), any());
        }

        @Test
        void shouldNotifySubmitterWhenPromotionRejected() {
            PromotionRequest request = createPendingPromotion();

            when(promotionRequestRepository.findById(PROMOTION_ID)).thenReturn(Optional.of(request));
            when(permissionChecker.canReviewPromotion(request, REVIEWER_ID, Set.of("SKILL_ADMIN"))).thenReturn(true);
            when(promotionRequestRepository.updateStatusWithVersion(
                    PROMOTION_ID, ReviewTaskStatus.REJECTED, REVIEWER_ID, "no", null, request.getVersion()))
                    .thenReturn(1);
            when(promotionRequestRepository.findById(PROMOTION_ID)).thenReturn(Optional.of(request));

            promotionService.rejectPromotion(PROMOTION_ID, REVIEWER_ID, "no", Set.of("SKILL_ADMIN"));

            verify(governanceNotificationService).notifyUser(eq(USER_ID), eq("PROMOTION"), eq("PROMOTION_REQUEST"), eq(PROMOTION_ID), eq("Promotion rejected"), any());
        }
    }

    @Nested
    class ApprovePromotion {

        @Test
        void shouldApprovePromotionSuccessfully() {
            PromotionRequest pr = createPendingPromotion();
            Skill sourceSkill = createSourceSkill();
            SkillVersion sourceVersion = createPublishedVersion();
            List<SkillFile> sourceFiles = createSourceFiles();

            when(promotionRequestRepository.findById(PROMOTION_ID)).thenReturn(Optional.of(pr));
            when(permissionChecker.canReviewPromotion(pr, REVIEWER_ID, Set.of("SKILL_ADMIN"))).thenReturn(true);
            when(promotionRequestRepository.updateStatusWithVersion(
                    PROMOTION_ID, ReviewTaskStatus.APPROVED, REVIEWER_ID, "LGTM", null, pr.getVersion()))
                    .thenReturn(1);
            when(skillRepository.findById(SOURCE_SKILL_ID)).thenReturn(Optional.of(sourceSkill));
            when(skillVersionRepository.findById(SOURCE_VERSION_ID)).thenReturn(Optional.of(sourceVersion));
            when(skillRepository.save(any(Skill.class))).thenAnswer(inv -> {
                Skill s = inv.getArgument(0);
                setField(s, "id", NEW_SKILL_ID);
                return s;
            });
            when(skillVersionRepository.save(any(SkillVersion.class))).thenAnswer(inv -> {
                SkillVersion v = inv.getArgument(0);
                setField(v, "id", NEW_VERSION_ID);
                return v;
            });
            when(skillFileRepository.findByVersionId(SOURCE_VERSION_ID)).thenReturn(sourceFiles);
            when(skillFileRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));
            when(promotionRequestRepository.save(pr)).thenReturn(pr);

            PromotionRequest result = promotionService.approvePromotion(
                    PROMOTION_ID, REVIEWER_ID, "LGTM", Set.of("SKILL_ADMIN"));

            assertNotNull(result);
            assertEquals(ReviewTaskStatus.APPROVED, result.getStatus());
            assertEquals(REVIEWER_ID, result.getReviewedBy());
            assertEquals("LGTM", result.getReviewComment());
            assertEquals(Instant.now(CLOCK), result.getReviewedAt());

            // Verify new skill created in global namespace
            ArgumentCaptor<Skill> skillCaptor = ArgumentCaptor.forClass(Skill.class);
            verify(skillRepository, times(2)).save(skillCaptor.capture());
            List<Skill> savedSkills = skillCaptor.getAllValues();
            Skill newSkill = savedSkills.get(0);
            assertEquals(TARGET_NAMESPACE_ID, newSkill.getNamespaceId());
            assertEquals("my-skill", newSkill.getSlug());
            assertEquals(USER_ID, newSkill.getOwnerId());
            assertEquals(SkillVisibility.PUBLIC, newSkill.getVisibility());
            assertEquals(SOURCE_SKILL_ID, newSkill.getSourceSkillId());

            // Verify new version created
            ArgumentCaptor<SkillVersion> versionCaptor = ArgumentCaptor.forClass(SkillVersion.class);
            verify(skillVersionRepository).save(versionCaptor.capture());
            SkillVersion newVersion = versionCaptor.getValue();
            assertEquals("1.0.0", newVersion.getVersion());
            assertEquals(SkillVersionStatus.PUBLISHED, newVersion.getStatus());
            assertEquals("Initial release", newVersion.getChangelog());
            assertEquals("{\"name\":\"test\"}", newVersion.getParsedMetadataJson());
            assertEquals("{\"version\":\"1.0.0\"}", newVersion.getManifestJson());
            assertEquals(3, newVersion.getFileCount());
            assertEquals(1024L, newVersion.getTotalSize());
            assertEquals(Instant.now(CLOCK), newVersion.getPublishedAt());

            // Verify files copied
            @SuppressWarnings("unchecked")
            ArgumentCaptor<List<SkillFile>> filesCaptor = ArgumentCaptor.forClass(List.class);
            verify(skillFileRepository).saveAll(filesCaptor.capture());
            List<SkillFile> copiedFiles = filesCaptor.getValue();
            assertEquals(2, copiedFiles.size());

            // Verify event published
            ArgumentCaptor<SkillPublishedEvent> eventCaptor = ArgumentCaptor.forClass(SkillPublishedEvent.class);
            verify(eventPublisher).publishEvent(eventCaptor.capture());
            SkillPublishedEvent event = eventCaptor.getValue();
            assertEquals(NEW_SKILL_ID, event.skillId());
            assertEquals(NEW_VERSION_ID, event.versionId());
            assertEquals(REVIEWER_ID, event.publisherId());

            // Verify targetSkillId updated on promotion request
            verify(promotionRequestRepository).save(pr);
            assertEquals(NEW_SKILL_ID, pr.getTargetSkillId());
        }

        @Test
        void shouldThrowWhenPromotionNotFound() {
            when(promotionRequestRepository.findById(PROMOTION_ID)).thenReturn(Optional.empty());

            assertThrows(DomainNotFoundException.class,
                    () -> promotionService.approvePromotion(PROMOTION_ID, REVIEWER_ID, "ok", Set.of("SKILL_ADMIN")));
        }

        @Test
        void shouldThrowWhenNotPending() {
            PromotionRequest pr = createPendingPromotion();
            setField(pr, "status", ReviewTaskStatus.APPROVED);
            when(promotionRequestRepository.findById(PROMOTION_ID)).thenReturn(Optional.of(pr));

            assertThrows(DomainBadRequestException.class,
                    () -> promotionService.approvePromotion(PROMOTION_ID, REVIEWER_ID, "ok", Set.of("SKILL_ADMIN")));
        }

        @Test
        void shouldThrowWhenNoPermission() {
            PromotionRequest pr = createPendingPromotion();
            when(promotionRequestRepository.findById(PROMOTION_ID)).thenReturn(Optional.of(pr));
            when(permissionChecker.canReviewPromotion(pr, REVIEWER_ID, Set.of())).thenReturn(false);

            assertThrows(DomainForbiddenException.class,
                    () -> promotionService.approvePromotion(PROMOTION_ID, REVIEWER_ID, "ok", Set.of()));
        }

        @Test
        void shouldThrowOnConcurrentModification() {
            PromotionRequest pr = createPendingPromotion();
            when(promotionRequestRepository.findById(PROMOTION_ID)).thenReturn(Optional.of(pr));
            when(permissionChecker.canReviewPromotion(pr, REVIEWER_ID, Set.of("SKILL_ADMIN"))).thenReturn(true);
            when(promotionRequestRepository.updateStatusWithVersion(
                    any(), any(), any(), any(), any(), any())).thenReturn(0);

            assertThrows(ConcurrentModificationException.class,
                    () -> promotionService.approvePromotion(PROMOTION_ID, REVIEWER_ID, "ok", Set.of("SKILL_ADMIN")));
        }

        @Test
        void shouldCopyDisplayNameAndSummaryToNewSkill() {
            PromotionRequest pr = createPendingPromotion();
            Skill sourceSkill = createSourceSkill();
            SkillVersion sourceVersion = createPublishedVersion();

            when(promotionRequestRepository.findById(PROMOTION_ID)).thenReturn(Optional.of(pr));
            when(permissionChecker.canReviewPromotion(pr, REVIEWER_ID, Set.of("SKILL_ADMIN"))).thenReturn(true);
            when(promotionRequestRepository.updateStatusWithVersion(any(), any(), any(), any(), any(), any())).thenReturn(1);
            when(skillRepository.findById(SOURCE_SKILL_ID)).thenReturn(Optional.of(sourceSkill));
            when(skillVersionRepository.findById(SOURCE_VERSION_ID)).thenReturn(Optional.of(sourceVersion));
            when(skillRepository.save(any(Skill.class))).thenAnswer(inv -> {
                Skill s = inv.getArgument(0);
                setField(s, "id", NEW_SKILL_ID);
                return s;
            });
            when(skillVersionRepository.save(any(SkillVersion.class))).thenAnswer(inv -> {
                SkillVersion v = inv.getArgument(0);
                setField(v, "id", NEW_VERSION_ID);
                return v;
            });
            when(skillFileRepository.findByVersionId(SOURCE_VERSION_ID)).thenReturn(List.of());
            when(skillFileRepository.saveAll(anyList())).thenReturn(List.of());

            promotionService.approvePromotion(PROMOTION_ID, REVIEWER_ID, "ok", Set.of("SKILL_ADMIN"));

            ArgumentCaptor<Skill> skillCaptor = ArgumentCaptor.forClass(Skill.class);
            verify(skillRepository, times(2)).save(skillCaptor.capture());
            Skill newSkill = skillCaptor.getAllValues().get(0);
            assertEquals("My Skill", newSkill.getDisplayName());
            assertEquals("A test skill", newSkill.getSummary());
        }
    }

    @Nested
    class RejectPromotion {

        @Test
        void shouldRejectPromotionSuccessfully() {
            PromotionRequest pr = createPendingPromotion();
            when(promotionRequestRepository.findById(PROMOTION_ID)).thenReturn(Optional.of(pr));
            when(permissionChecker.canReviewPromotion(pr, REVIEWER_ID, Set.of("SKILL_ADMIN"))).thenReturn(true);
            when(promotionRequestRepository.updateStatusWithVersion(
                    PROMOTION_ID, ReviewTaskStatus.REJECTED, REVIEWER_ID, "Not ready", null, pr.getVersion()))
                    .thenReturn(1);
            PromotionRequest result = promotionService.rejectPromotion(
                    PROMOTION_ID, REVIEWER_ID, "Not ready", Set.of("SKILL_ADMIN"));

            assertNotNull(result);
            assertEquals(ReviewTaskStatus.REJECTED, result.getStatus());
            assertEquals(REVIEWER_ID, result.getReviewedBy());
            assertEquals("Not ready", result.getReviewComment());
            assertEquals(Instant.now(CLOCK), result.getReviewedAt());
            verify(promotionRequestRepository).updateStatusWithVersion(
                    PROMOTION_ID, ReviewTaskStatus.REJECTED, REVIEWER_ID, "Not ready", null, pr.getVersion());
            verify(eventPublisher, never()).publishEvent(any());
        }

        @Test
        void shouldThrowWhenPromotionNotFound() {
            when(promotionRequestRepository.findById(PROMOTION_ID)).thenReturn(Optional.empty());

            assertThrows(DomainNotFoundException.class,
                    () -> promotionService.rejectPromotion(PROMOTION_ID, REVIEWER_ID, "no", Set.of("SKILL_ADMIN")));
        }

        @Test
        void shouldThrowWhenNotPending() {
            PromotionRequest pr = createPendingPromotion();
            setField(pr, "status", ReviewTaskStatus.REJECTED);
            when(promotionRequestRepository.findById(PROMOTION_ID)).thenReturn(Optional.of(pr));

            assertThrows(DomainBadRequestException.class,
                    () -> promotionService.rejectPromotion(PROMOTION_ID, REVIEWER_ID, "no", Set.of("SKILL_ADMIN")));
        }

        @Test
        void shouldThrowWhenNoPermission() {
            PromotionRequest pr = createPendingPromotion();
            when(promotionRequestRepository.findById(PROMOTION_ID)).thenReturn(Optional.of(pr));
            when(permissionChecker.canReviewPromotion(pr, REVIEWER_ID, Set.of())).thenReturn(false);

            assertThrows(DomainForbiddenException.class,
                    () -> promotionService.rejectPromotion(PROMOTION_ID, REVIEWER_ID, "no", Set.of()));
        }

        @Test
        void shouldThrowOnConcurrentModification() {
            PromotionRequest pr = createPendingPromotion();
            when(promotionRequestRepository.findById(PROMOTION_ID)).thenReturn(Optional.of(pr));
            when(permissionChecker.canReviewPromotion(pr, REVIEWER_ID, Set.of("SKILL_ADMIN"))).thenReturn(true);
            when(promotionRequestRepository.updateStatusWithVersion(any(), any(), any(), any(), any(), any()))
                    .thenReturn(0);

            assertThrows(ConcurrentModificationException.class,
                    () -> promotionService.rejectPromotion(PROMOTION_ID, REVIEWER_ID, "no", Set.of("SKILL_ADMIN")));
        }
    }
}
