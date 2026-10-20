package com.iflytek.skillhub.repository;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.iflytek.skillhub.domain.audit.AuditLogService;
import com.iflytek.skillhub.domain.namespace.Namespace;
import com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException;
import com.iflytek.skillhub.domain.shared.exception.DomainForbiddenException;
import com.iflytek.skillhub.domain.skill.*;
import com.iflytek.skillhub.domain.skill.metadata.SkillMetadataParser;
import com.iflytek.skillhub.domain.skill.service.SkillPublishService;
import com.iflytek.skillhub.domain.skill.service.SkillQueryService;
import com.iflytek.skillhub.domain.skill.service.SkillStorageDeletionCompensationService;
import com.iflytek.skillhub.domain.skill.validation.*;
import com.iflytek.skillhub.domain.security.SecurityScanService;
import com.iflytek.skillhub.dto.AvatarReviewSettingsResponse;
import com.iflytek.skillhub.infra.jpa.*;
import com.iflytek.skillhub.observability.RequestIdAccessor;
import com.iflytek.skillhub.service.*;
import com.iflytek.skillhub.task.SkillAvatarCleanupTask;
import com.iflytek.skillhub.storage.ObjectStorageService;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Real publication and staged-image transactions, with controlled external dependencies. */
@DataJpaTest(showSql = false)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Testcontainers
@Import({com.iflytek.skillhub.auth.rbac.RbacService.class, com.iflytek.skillhub.domain.skill.SkillOwnerAccessPolicy.class, com.iflytek.skillhub.domain.review.ReviewService.class, com.iflytek.skillhub.domain.review.ReviewPermissionChecker.class, SkillAvatarAppService.class, SkillAvatarUploadService.class, SkillPublishAppService.class,
        SkillPublishService.class, SkillStorageDeletionCompensationService.class, JpaSkillStorageDeletionCompensationRepositoryAdapter.class, SkillAvatarCleanupTask.class,
        SkillAvatarUploadPostgresTest.Configuration.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class SkillAvatarUploadPostgresTest {
    @Container static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");
    @DynamicPropertySource static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.jpa.database-platform", () -> "org.hibernate.dialect.PostgreSQLDialect");
        registry.add("spring.flyway.enabled", () -> true);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
    }
    @TestConfiguration static class Configuration {
        @Bean SkillMetadataParser metadata() { return new SkillMetadataParser(); }
        @Bean SkillPackageValidator validator(SkillMetadataParser metadata) { return new SkillPackageValidator(metadata); }
        @Bean PrePublishValidator preValidator() { return new NoOpPrePublishValidator(); }
        @Bean ObjectMapper mapper() { return new ObjectMapper().disable(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES); }
        @Bean Clock clock() { return Clock.systemUTC(); }
    }
    @Autowired SkillAvatarUploadService uploads;
    @Autowired SkillPublishAppService publications;
    @Autowired SkillAvatarAppService avatars;
    @Autowired SkillAvatarCleanupTask cleanup;
    @Autowired SkillAvatarUploadJpaRepository uploadRows;
    @Autowired SkillJpaRepository skills;
    @Autowired SkillVersionJpaRepository versions;
    @Autowired SkillAvatarCandidateJpaRepository candidates;
    @Autowired NamespaceJpaRepository namespaces;
    @Autowired NamespaceMemberJpaRepository members;
    @Autowired UserAccountJpaRepository users;
    @Autowired PlatformTransactionManager transactions;
    @Autowired JdbcTemplate jdbc;
    @MockBean private com.iflytek.skillhub.domain.skill.service.SkillVisibilityService visibilityChanges;
    @MockBean ObjectStorageService storage;
    @MockBean SkillAvatarImageProcessor images;
    @MockBean AvatarReviewSettingsService settings;
    @MockBean AvatarModerationGateway gateway;
    @MockBean VisibilityChecker visibility;
    @MockBean SkillQueryService queries;
    @MockBean AuditLogService audit;
    @MockBean RequestIdAccessor requestIds;
    @MockBean SecurityScanService scanner;
    @MockBean com.iflytek.skillhub.domain.skill.service.SkillGovernanceService governance;
    @MockBean com.iflytek.skillhub.domain.governance.GovernanceNotificationService notifications;
    @Autowired com.iflytek.skillhub.domain.review.ReviewService reviews;
    @Autowired ReviewTaskJpaRepository reviewRows;

    private String namespace;
    private final AuditRequestContext context = new AuditRequestContext(null, null);

    @BeforeEach void fixtures() {
        for (String actor : List.of("owner", "stranger")) {
            if (((com.iflytek.skillhub.domain.user.UserAccountRepository) users).findById(actor).isEmpty()) {
                users.saveAndFlush(new com.iflytek.skillhub.domain.user.UserAccount(actor, actor, null, null));
            }
        }
        namespace = "upload-" + UUID.randomUUID().toString().substring(0, 8);
        var createdNamespace = namespaces.saveAndFlush(new Namespace(namespace, "Upload testing", "owner"));
        members.saveAndFlush(new com.iflytek.skillhub.domain.namespace.NamespaceMember(createdNamespace.getId(), "owner", com.iflytek.skillhub.domain.namespace.NamespaceRole.OWNER));
        when(images.normalize(any())).thenAnswer(call -> call.getArgument(0));
        when(storage.exists(anyString())).thenReturn(true);
        when(storage.getObject(anyString())).thenAnswer(call -> new ByteArrayInputStream(new byte[]{1,2,3}));
        when(settings.current()).thenReturn(new AvatarReviewSettingsResponse(false, true, 1, null));
        when(scanner.isEnabled()).thenReturn(true);
        org.mockito.Mockito.lenient().when(gateway.context(any())).thenReturn(
                new AvatarModerationProvider.Context("fixture-image", "test-v1", "fixture-policy-v1", null));
    }

    @Test void sameSlugPrivateDraftOwnedByAnotherUserDoesNotHijackAvatarBindingOrRemoval() {
        Long otherId = new TransactionTemplate(transactions).execute(ignored ->
                skills.saveAndFlush(new Skill(namespaceId(), "skill", "stranger", SkillVisibility.PRIVATE)).getId());
        String id = upload();
        var mine = publish("1.0.0", SkillPublishAppService.AvatarAction.REPLACE, id, "skill");
        Long myCandidate = uploadRows.findById(id).orElseThrow().getCandidateId();
        assertThat(candidates.findById(myCandidate).orElseThrow().getSkillId()).isEqualTo(mine.skillId());
        assertThat(avatars.ownerStatus(namespace, "skill", "owner").candidateId()).isEqualTo(myCandidate);
        assertThat(avatars.ownerStatus(namespace, "skill", "stranger").candidateId()).isNull();
        assertThat(avatars.readCandidate(namespace, "skill", myCandidate, "owner", java.util.Map.of(), Set.of())).isNotNull();
        var metadataTask = reviewRows.findByAvatarCandidateIdAndStatus(myCandidate,
                com.iflytek.skillhub.domain.review.ReviewTaskStatus.PENDING).getFirst();
        reviews.approveReview(metadataTask.getId(), "owner", "approved",
                java.util.Map.of(namespaceId(), com.iflytek.skillhub.domain.namespace.NamespaceRole.OWNER), Set.of());
        SkillQueryService.SkillDetailDTO detail = new SkillQueryService.SkillDetailDTO(
                mine.skillId(), null, null, null, null, null, null, null, null, null, null, null, null,
                false, null, null, null, false, false, false, false, null, null, null, null, null, null, null);
        when(queries.getSkillDetail(eq(namespace), eq("skill"), eq("owner"), any())).thenReturn(detail);
        when(visibility.canAccess(any(Skill.class), eq("owner"), any(), any())).thenReturn(true);
        assertThat(avatars.readActive(namespace, "skill", "owner", java.util.Map.of(), Set.of())).isNotNull();
        publish("1.0.1", SkillPublishAppService.AvatarAction.REMOVE, null, "skill");
        assertThat(read(mine.skillId()).getPendingAvatarId()).isNull();
        assertThat(read(otherId).getPendingAvatarId()).isNull();
        assertThat(read(otherId).getVisibility()).isEqualTo(SkillVisibility.PRIVATE);
    }

    @Test void uploadDoesNotCreateSkillOrCandidateAndPreviewIsUploaderOnly() {
        long initialCandidates = candidates.count();
        String id = upload();
        assertThat(candidates.count()).isEqualTo(initialCandidates);
        assertThat(skills.findByNamespaceIdAndSlug(namespaceId(), "skill")).isEmpty();
        assertThat(uploads.preview(id, "owner")).isNotNull();
        assertThatThrownBy(() -> uploads.preview(id, "stranger")).isInstanceOf(DomainForbiddenException.class);
    }

    @Test void publishConsumesUploadAndRepeatedReferenceDoesNotCreateAnotherCandidate() {
        String id = upload();
        var published = publish("1.0.0", SkillPublishAppService.AvatarAction.REPLACE, id, null);
        var candidate = candidates.findById(uploadRows.findById(id).orElseThrow().getCandidateId()).orElseThrow();
        assertThat(candidate.getSkillId()).isEqualTo(published.skillId());
        assertThat(candidate.getReviewStatus()).isEqualTo(SkillAvatarCandidate.ReviewStatus.PENDING);
        assertThat(candidate.getObjectKey()).isEqualTo(uploadRows.findById(id).orElseThrow().getObjectKey());
        assertThat(uploads.submit(namespace, "skill", "owner", id, context).candidateId()).isEqualTo(candidate.getId());
        assertThat(candidates.findBySkillIdOrderByCreatedAtDesc(published.skillId())).hasSize(1);
        assertThat(read(published.skillId()).getActiveAvatarId()).isNull();
        verify(storage, times(1)).putObject(startsWith("skill-avatars/"), any(), anyLong(), anyString());
    }

    @Test void invalidReferencesAndWrongTargetRejectBeforePackageWrites() {
        String foreign = uploads.upload("stranger", new byte[]{5}).uploadId();
        assertThatThrownBy(() -> publish("1.0.0", SkillPublishAppService.AvatarAction.REPLACE, foreign, null))
                .isInstanceOf(DomainForbiddenException.class);
        String expired = upload();
        jdbc.update("UPDATE skill_avatar_upload SET expires_at = TIMESTAMP '2000-01-01 00:00:00' WHERE id = ?", expired);
        assertThatThrownBy(() -> publish("1.0.0", SkillPublishAppService.AvatarAction.REPLACE, expired, null))
                .isInstanceOf(DomainBadRequestException.class);
        String missingObject = upload();
        when(storage.exists(uploadRows.findById(missingObject).orElseThrow().getObjectKey())).thenReturn(false);
        assertThatThrownBy(() -> publish("1.0.0", SkillPublishAppService.AvatarAction.REPLACE, missingObject, null))
                .isInstanceOf(DomainBadRequestException.class);
        assertThatThrownBy(() -> publish("1.0.0", SkillPublishAppService.AvatarAction.KEEP, null, "different-skill"))
                .isInstanceOf(DomainBadRequestException.class);
        verify(storage, never()).putObject(startsWith("skills/"), any(), anyLong(), anyString());
        assertThat(skills.findByNamespaceIdAndSlug(namespaceId(), "skill")).isEmpty();
    }

    @Test void lateAvatarFailureRollsBackVersionReplacementAndPreservesOldCandidateAndObjects() {
        var old = publish("1.0.0", SkillPublishAppService.AvatarAction.REPLACE, upload(), null);
        Long oldCandidate = read(old.skillId()).getPendingAvatarId();
        new TransactionTemplate(transactions).executeWithoutResult(ignored ->
                ((SkillVersionRepository) versions).findById(old.version().getId()).orElseThrow().setStatus(SkillVersionStatus.UPLOADED));
        String fresh = upload();
        when(audit.record(anyString(), eq("SKILL_AVATAR_SUBMIT"), anyString(), anyLong(), any(), any(), any(), anyString()))
                .thenThrow(new IllegalStateException("audit unavailable"));
        clearInvocations(storage);
        assertThatThrownBy(() -> publish("1.0.0", SkillPublishAppService.AvatarAction.REPLACE, fresh, null))
                .isInstanceOf(IllegalStateException.class);
        assertThat(versions.findBySkillIdAndVersion(old.skillId(), "1.0.0").orElseThrow().getId()).isEqualTo(old.version().getId());
        assertThat(read(old.skillId()).getPendingAvatarId()).isEqualTo(oldCandidate);
        assertThat(candidates.findById(oldCandidate).orElseThrow().getStatus()).isEqualTo(SkillAvatarCandidate.Status.PENDING);
        assertThat(uploadRows.findById(fresh).orElseThrow().getCandidateId()).isNull();
        @SuppressWarnings("unchecked")
        var deleted = (org.mockito.ArgumentCaptor<List<String>>) (org.mockito.ArgumentCaptor<?>) org.mockito.ArgumentCaptor.forClass(List.class);
        verify(storage).deleteObjects(deleted.capture());
        assertThat(deleted.getValue()).hasSize(2).allMatch(key -> !key.contains("/" + old.version().getId() + "/"));
        verify(storage, never()).deleteObject(uploadRows.findById(fresh).orElseThrow().getObjectKey());
    }

    @Test void failedNewPublicationLeavesNoSkillVersionOrCandidate() {
        String id = upload();
        when(settings.current()).thenThrow(new IllegalStateException("settings unavailable"));
        assertThatThrownBy(() -> publish("1.0.0", SkillPublishAppService.AvatarAction.REPLACE, id, null))
                .isInstanceOf(IllegalStateException.class);
        assertThat(skills.findByNamespaceIdAndSlug(namespaceId(), "skill")).isEmpty();
        assertThat(uploadRows.findById(id).orElseThrow().getCandidateId()).isNull();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM skill_avatar_candidate WHERE object_key = ?", Long.class,
                uploadRows.findById(id).orElseThrow().getObjectKey())).isZero();
        verify(storage).deleteObjects(argThat(keys -> keys.size() == 2));
    }

    @Test void failedRollbackObjectDeletionIsPersistedForRetryWithoutOriginalErrorText() {
        String id = upload();
        when(settings.current()).thenThrow(new IllegalStateException("settings unavailable"));
        doThrow(new IllegalStateException("sensitive-storage-error")).when(storage).deleteObjects(anyList());
        assertThatThrownBy(() -> publish("1.0.0", SkillPublishAppService.AvatarAction.REPLACE, id, null))
                .isInstanceOf(IllegalStateException.class);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM skill_storage_delete_compensation WHERE namespace = ? AND status = 'PENDING'", Long.class, namespace))
                .isEqualTo(1L);
        assertThat(jdbc.queryForObject("SELECT last_error FROM skill_storage_delete_compensation WHERE namespace = ?", String.class, namespace))
                .isEqualTo("IllegalStateException");
    }

    @Test void removeOnlyTakesEffectOnSuccessfulCommitAndOldReviewCannotRestoreIt() {
        var first = publish("1.0.0", SkillPublishAppService.AvatarAction.REPLACE, upload(), null);
        Long candidate = read(first.skillId()).getPendingAvatarId();
        assertThatThrownBy(() -> publish("1.0.1", SkillPublishAppService.AvatarAction.REMOVE, null, "wrong"))
                .isInstanceOf(DomainBadRequestException.class);
        assertThat(read(first.skillId()).getPendingAvatarId()).isEqualTo(candidate);
        publish("1.0.1", SkillPublishAppService.AvatarAction.REMOVE, null, "skill");
        assertThat(read(first.skillId()).getPendingAvatarId()).isNull();
        assertThat(candidates.findById(candidate).orElseThrow().getStatus()).isEqualTo(SkillAvatarCandidate.Status.REMOVED);
        assertThatThrownBy(() -> avatars.decide(namespace, "skill", candidate, true, "late", "owner",
                java.util.Map.of(namespaceId(), com.iflytek.skillhub.domain.namespace.NamespaceRole.OWNER), Set.of(), context))
                .isInstanceOf(com.iflytek.skillhub.domain.shared.exception.DomainConflictException.class);
    }

    @Test void cleanupExcludesLinkedImagesAndWaitsForConsumptionLock() throws Exception {
        String abandoned = upload();
        String linked = upload();
        var skill = publish("1.0.0", SkillPublishAppService.AvatarAction.REPLACE, linked, null);
        String abandonedKey = uploadRows.findById(abandoned).orElseThrow().getObjectKey();
        String linkedKey = uploadRows.findById(linked).orElseThrow().getObjectKey();
        jdbc.update("UPDATE skill_avatar_upload SET expires_at = TIMESTAMP '2000-01-01 00:00:00' WHERE id IN (?, ?)", abandoned, linked);
        cleanup.removeExpiredImages();
        verify(storage).deleteObject(abandonedKey);
        verify(storage, never()).deleteObject(linkedKey);
        assertThat(uploadRows.findById(abandoned).orElseThrow().getObjectKey()).isNull();
        assertThat(candidates.findById(read(skill.skillId()).getPendingAvatarId()).orElseThrow().getObjectKey()).isEqualTo(linkedKey);
        String racing = upload();
        CountDownLatch locked = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var consuming = pool.submit(() -> new TransactionTemplate(transactions).execute(status -> {
                uploads.submit(namespace, "skill", "owner", racing, context);
                jdbc.update("UPDATE skill_avatar_upload SET expires_at = TIMESTAMP '2000-01-01 00:00:00' WHERE id = ?", racing);
                locked.countDown();
                try { assertThat(release.await(10, TimeUnit.SECONDS)).isTrue(); }
                catch (InterruptedException failure) { throw new RuntimeException(failure); }
                return null;
            }));
            try {
                assertThat(locked.await(10, TimeUnit.SECONDS)).isTrue();
                var scanning = pool.submit(cleanup::removeExpiredImages);
                release.countDown();
                consuming.get(10, TimeUnit.SECONDS);
                scanning.get(10, TimeUnit.SECONDS);
            } finally { release.countDown(); }
        }
        assertThat(uploadRows.findById(racing).orElseThrow().getCandidateId()).isNotNull();
        verify(storage, never()).deleteObject(uploadRows.findById(racing).orElseThrow().getObjectKey());
    }

    @Test void expiredCleanupLocksExcludeConcurrentConsumption() throws Exception {
        var published = publish("1.0.0", SkillPublishAppService.AvatarAction.KEEP, null, null);
        String id = upload();
        String key = uploadRows.findById(id).orElseThrow().getObjectKey();
        jdbc.update("UPDATE skill_avatar_upload SET expires_at = TIMESTAMP '2000-01-01 00:00:00' WHERE id = ?", id);
        CountDownLatch deleting = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        doAnswer(call -> {
            deleting.countDown();
            assertThat(release.await(10, TimeUnit.SECONDS)).isTrue();
            return null;
        }).when(storage).deleteObject(key);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var cleaning = pool.submit(cleanup::removeExpiredImages);
            try {
                assertThat(deleting.await(10, TimeUnit.SECONDS)).isTrue();
                var consuming = pool.submit(() -> uploads.submit(namespace, "skill", "owner", id, context));
                assertThatThrownBy(() -> consuming.get(250, TimeUnit.MILLISECONDS))
                        .isInstanceOf(java.util.concurrent.TimeoutException.class);
                release.countDown();
                cleaning.get(10, TimeUnit.SECONDS);
                assertThatThrownBy(() -> consuming.get(10, TimeUnit.SECONDS))
                        .hasCauseInstanceOf(DomainBadRequestException.class);
            } finally { release.countDown(); }
        }
        assertThat(read(published.skillId()).getPendingAvatarId()).isNull();
        assertThat(uploadRows.findById(id).orElseThrow().getCandidateId()).isNull();
        assertThat(uploadRows.findById(id).orElseThrow().getObjectKey()).isNull();
    }

    @Test void concurrentSameReferenceIsConsumedOnceWithoutDeadlock() throws Exception {
        var published = publish("1.0.0", SkillPublishAppService.AvatarAction.KEEP, null, null);
        String id = upload();
        CountDownLatch start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var first = pool.submit(() -> { start.await(); return uploads.submit(namespace, "skill", "owner", id, context); });
            var second = pool.submit(() -> { start.await(); return uploads.submit(namespace, "skill", "owner", id, context); });
            start.countDown();
            assertThat(first.get(10, TimeUnit.SECONDS).candidateId()).isEqualTo(second.get(10, TimeUnit.SECONDS).candidateId());
        }
        assertThat(candidates.findBySkillIdOrderByCreatedAtDesc(published.skillId())).hasSize(1);
    }

    @Test void targetPublicAudienceCannotBypassReviewWhilePrivateVersionWaits() {
        var first = publications.publish(namespace, entries("1.0.0"), "owner", SkillVisibility.PRIVATE, Set.of("SUPER_ADMIN"),
                false, SkillPublishAppService.AvatarAction.REPLACE, upload(), null, context);
        Long active = read(first.skillId()).getActiveAvatarId();
        // The non-admin publisher must have namespace membership for the real domain service.
        var expanded = publications.publish(namespace, entries("1.0.1"), "owner", SkillVisibility.PUBLIC, Set.of(),
                false, SkillPublishAppService.AvatarAction.REPLACE, upload(), null, context);
        var pending = candidates.findById(read(expanded.skillId()).getPendingAvatarId()).orElseThrow();
        assertThat(pending.getAudience()).isEqualTo(SkillVisibility.PUBLIC);
        assertThat(pending.isManualEnabled()).isTrue();
        assertThat(read(first.skillId()).getActiveAvatarId()).isEqualTo(active);
        assertThat(avatars.prepareScopeReview(first.skillId(), "owner")).isNull();
        assertThat(read(first.skillId()).getPendingAvatarId()).isEqualTo(pending.getId());
    }

    private String upload() { return uploads.upload("owner", UUID.randomUUID().toString().getBytes(StandardCharsets.UTF_8)).uploadId(); }
    private Long namespaceId() { return namespaces.findBySlug(namespace).orElseThrow().getId(); }
    private Skill read(Long id) { return ((SkillRepository) skills).findById(id).orElseThrow(); }
    @Test void consumedReferenceCannotAttachToAnotherPackageTaskAndPublicationRollsBack() {
        String uploadId = upload();
        var first = publications.publish(namespace, entries("1.0.0"), "owner", SkillVisibility.PUBLIC, Set.of(),
                true, SkillPublishAppService.AvatarAction.REPLACE, uploadId, "skill", context);
        var originalTask = reviewRows.findBySkillVersionIdAndStatus(first.version().getId(),
                com.iflytek.skillhub.domain.review.ReviewTaskStatus.PENDING).orElseThrow();
        Long candidateId = originalTask.getAvatarCandidateId();
        assertThat(uploads.submit(namespace, "skill", "owner", uploadId, context).candidateId()).isEqualTo(candidateId);
        assertThat(uploads.submitForReview(namespace, "skill", "owner", uploadId, SkillVisibility.PUBLIC,
                context, originalTask).candidateId()).isEqualTo(candidateId);
        assertThatThrownBy(() -> publications.publish(namespace, entries("1.0.1"), "owner", SkillVisibility.PUBLIC, Set.of(),
                true, SkillPublishAppService.AvatarAction.REPLACE, uploadId, "skill", context))
                .isInstanceOf(com.iflytek.skillhub.domain.shared.exception.DomainConflictException.class);
        assertThat(versions.findBySkillIdAndVersion(first.skillId(), "1.0.1")).isEmpty();
        assertThat(reviewRows.findBySkillVersionIdAndStatus(first.version().getId(),
                com.iflytek.skillhub.domain.review.ReviewTaskStatus.PENDING)).isPresent();
        assertThat(candidates.findBySkillIdOrderByCreatedAtDesc(first.skillId())).hasSize(1);
        assertThat(read(first.skillId()).getPendingAvatarId()).isEqualTo(candidateId);
        assertThat(candidates.findById(candidateId).orElseThrow().getStatus()).isEqualTo(SkillAvatarCandidate.Status.PENDING);
    }

    @Test void withdrawalRefreshesTaskAfterWaitingForConcurrentApproval() throws Exception {
        var first = publish("1.0.0", SkillPublishAppService.AvatarAction.REPLACE, upload(), "skill");
        Long candidateId = read(first.skillId()).getPendingAvatarId();
        var task = reviewRows.findByAvatarCandidateIdAndStatus(candidateId,
                com.iflytek.skillhub.domain.review.ReviewTaskStatus.PENDING).getFirst();
        try (var executor = Executors.newSingleThreadExecutor()) {
            assertThatThrownBy(() -> new TransactionTemplate(transactions).executeWithoutResult(ignored -> {
                var stale = ((com.iflytek.skillhub.domain.review.ReviewTaskRepository) reviewRows).findById(task.getId()).orElseThrow();
                try {
                    executor.submit(() -> reviews.approveReview(task.getId(), "stranger", "approved", java.util.Map.of(), Set.of("SKILL_ADMIN")))
                            .get(10, TimeUnit.SECONDS);
                } catch (Exception failure) { throw new IllegalStateException(failure); }
                avatars.lock(stale);
                avatars.withdraw(stale);
            })).isInstanceOf(com.iflytek.skillhub.domain.shared.exception.DomainConflictException.class);
        }
        assertThat(read(first.skillId()).getActiveAvatarId()).isEqualTo(candidateId);
        assertThat(candidates.findById(candidateId).orElseThrow().getStatus()).isEqualTo(SkillAvatarCandidate.Status.ACTIVE);
    }

    @Test void machinePassDoesNotActivateImageBeforeCombinedReviewAndPlatformCanReviewTeam() {
        when(settings.current()).thenReturn(new AvatarReviewSettingsResponse(true, false, 1, null));
        when(gateway.requireConfiguredProvider()).thenReturn("xfyun");
        when(gateway.moderate(eq("xfyun"), any())).thenReturn(AvatarModerationProvider.Verdict.PASS);
        var result = publications.publish(namespace, entries("1.0.0"), "owner", SkillVisibility.PUBLIC, Set.of(),
                true, SkillPublishAppService.AvatarAction.REPLACE, upload(), "skill", context);
        var task = reviewRows.findBySkillVersionIdAndStatus(result.version().getId(),
                com.iflytek.skillhub.domain.review.ReviewTaskStatus.PENDING).orElseThrow();
        assertThatThrownBy(() -> reviews.approveReview(task.getId(), "stranger", "approved", java.util.Map.of(), Set.of("SKILL_ADMIN")))
                .isInstanceOf(com.iflytek.skillhub.domain.shared.exception.DomainConflictException.class);
        avatars.processMachine(task.getAvatarCandidateId(), context);
        assertThat(read(result.skillId()).getActiveAvatarId()).isNull();
        reviews.approveReview(task.getId(), "stranger", "approved", java.util.Map.of(), Set.of("SKILL_ADMIN"));
        assertThat(read(result.skillId()).getActiveAvatarId()).isEqualTo(task.getAvatarCandidateId());
    }

    @Test void removalWaitsForDecisionAndRejectKeepsActiveAvatar() {
        when(settings.current()).thenReturn(new AvatarReviewSettingsResponse(false, false, 1, null));
        var first = publish("1.0.0", SkillPublishAppService.AvatarAction.REPLACE, upload(), "skill");
        Long active = read(first.skillId()).getActiveAvatarId();
        var removal = publications.publish(namespace, entries("1.0.1"), "owner", SkillVisibility.PUBLIC, Set.of(),
                true, SkillPublishAppService.AvatarAction.REMOVE, null, "skill", context);
        var task = reviewRows.findBySkillVersionIdAndStatus(removal.version().getId(),
                com.iflytek.skillhub.domain.review.ReviewTaskStatus.PENDING).orElseThrow();
        assertThat(read(first.skillId()).getActiveAvatarId()).isEqualTo(active);
        reviews.rejectReview(task.getId(), "stranger", "keep", java.util.Map.of(), Set.of("SKILL_ADMIN"));
        assertThat(read(first.skillId()).getActiveAvatarId()).isEqualTo(active);
        var secondRemoval = publications.publish(namespace, entries("1.0.2"), "owner", SkillVisibility.PUBLIC, Set.of(),
                true, SkillPublishAppService.AvatarAction.REMOVE, null, "skill", context);
        var staleTask = reviewRows.findBySkillVersionIdAndStatus(secondRemoval.version().getId(),
                com.iflytek.skillhub.domain.review.ReviewTaskStatus.PENDING).orElseThrow();
        uploads.submit(namespace, "skill", "owner", upload(), context);
        Long newAvatar = read(first.skillId()).getActiveAvatarId();
        assertThat(newAvatar).isNotEqualTo(active);
        assertThatThrownBy(() -> reviews.approveReview(staleTask.getId(), "stranger", "approved", java.util.Map.of(), Set.of("SKILL_ADMIN")))
                .isInstanceOf(com.iflytek.skillhub.domain.shared.exception.DomainConflictException.class);
        assertThat(read(first.skillId()).getActiveAvatarId()).isEqualTo(newAvatar);
    }

    @Test void keepWhenAudienceExpandsIsPartOfSameTask() {
        var first = publications.publish(namespace, entries("1.0.0"), "owner", SkillVisibility.PRIVATE, Set.of("SUPER_ADMIN"),
                true, SkillPublishAppService.AvatarAction.REPLACE, upload(), "skill", context);
        var expanded = publications.publish(namespace, entries("1.0.1"), "owner", SkillVisibility.PUBLIC, Set.of(),
                true, SkillPublishAppService.AvatarAction.KEEP, null, "skill", context);
        var task = reviewRows.findBySkillVersionIdAndStatus(expanded.version().getId(),
                com.iflytek.skillhub.domain.review.ReviewTaskStatus.PENDING).orElseThrow();
        assertThat(task.getAvatarCandidateId()).isNotNull();
        assertThat(reviewRows.findByAvatarCandidateIdAndStatus(task.getAvatarCandidateId(),
                com.iflytek.skillhub.domain.review.ReviewTaskStatus.PENDING)).hasSize(1);
        reviews.approveReview(task.getId(), "stranger", "approved", java.util.Map.of(), Set.of("SKILL_ADMIN"));
        assertThat(read(first.skillId()).getActiveAvatarAudience()).isEqualTo(SkillVisibility.PUBLIC);
    }

    @Test void packageAndAvatarHaveOneTaskAndBecomePublishedTogether() {
        var result = publications.publish(namespace, entries("1.0.0"), "owner", SkillVisibility.PUBLIC, Set.of(),
                true, SkillPublishAppService.AvatarAction.REPLACE, upload(), "skill", context);
        var task = reviewRows.findBySkillVersionIdAndStatus(result.version().getId(),
                com.iflytek.skillhub.domain.review.ReviewTaskStatus.PENDING).orElseThrow();
        assertThat(task.getAvatarCandidateId()).isEqualTo(read(result.skillId()).getPendingAvatarId());
        assertThat(reviewRows.findByAvatarCandidateIdAndStatus(task.getAvatarCandidateId(),
                com.iflytek.skillhub.domain.review.ReviewTaskStatus.PENDING)).hasSize(1);
        assertThat(read(result.skillId()).getActiveAvatarId()).isNull();
        reviews.approveReview(task.getId(), "owner", "approved",
                java.util.Map.of(namespaceId(), com.iflytek.skillhub.domain.namespace.NamespaceRole.OWNER), Set.of());
        assertThat(read(result.skillId()).getActiveAvatarId()).isEqualTo(task.getAvatarCandidateId());
        assertThat(read(result.skillId()).getLatestVersionId()).isEqualTo(result.version().getId());
        assertThat(((SkillVersionRepository) versions).findById(result.version().getId()).orElseThrow().getStatus()).isEqualTo(SkillVersionStatus.PUBLISHED);
    }

    @Test void avatarOnlyDoesNotCreateVersionAndLateAuditFailureRollsBackBothDecisions() {
        var result = publish("1.0.0", SkillPublishAppService.AvatarAction.KEEP, null, null);
        long versionCount = versions.count();
        var status = uploads.submit(namespace, "skill", "owner", upload(), context);
        var task = reviewRows.findByAvatarCandidateIdAndStatus(status.candidateId(),
                com.iflytek.skillhub.domain.review.ReviewTaskStatus.PENDING).getFirst();
        assertThat(task.getSkillVersionId()).isNull();
        doThrow(new IllegalStateException("test audit failure")).when(audit).record(any(), eq("SKILL_AVATAR_APPROVE"), any(), any(), any(), any(), any(), any());
        assertThatThrownBy(() -> reviews.approveReview(task.getId(), "owner", "approved",
                java.util.Map.of(namespaceId(), com.iflytek.skillhub.domain.namespace.NamespaceRole.OWNER), Set.of()))
                .isInstanceOf(IllegalStateException.class);
        assertThat(((com.iflytek.skillhub.domain.review.ReviewTaskRepository) reviewRows).findById(task.getId()).orElseThrow().getStatus()).isEqualTo(com.iflytek.skillhub.domain.review.ReviewTaskStatus.PENDING);
        assertThat(candidates.findById(status.candidateId()).orElseThrow().getStatus()).isEqualTo(SkillAvatarCandidate.Status.PENDING);
        assertThat(read(result.skillId()).getActiveAvatarId()).isNull();
        assertThat(versions.count()).isEqualTo(versionCount);
    }

    @Test void supersededBundleCannotPublishPackageWithoutPromisedAvatar() {
        var result = publications.publish(namespace, entries("1.0.0"), "owner", SkillVisibility.PUBLIC, Set.of(),
                true, SkillPublishAppService.AvatarAction.REPLACE, upload(), "skill", context);
        var task = reviewRows.findBySkillVersionIdAndStatus(result.version().getId(),
                com.iflytek.skillhub.domain.review.ReviewTaskStatus.PENDING).orElseThrow();
        uploads.submit(namespace, "skill", "owner", upload(), context);
        assertThatThrownBy(() -> reviews.approveReview(task.getId(), "owner", "approved",
                java.util.Map.of(namespaceId(), com.iflytek.skillhub.domain.namespace.NamespaceRole.OWNER), Set.of()))
                .isInstanceOf(com.iflytek.skillhub.domain.shared.exception.DomainConflictException.class);
        assertThat(((SkillVersionRepository) versions).findById(result.version().getId()).orElseThrow().getStatus()).isNotEqualTo(SkillVersionStatus.PUBLISHED);
    }

    private SkillPublishService.PublishResult publish(String version, SkillPublishAppService.AvatarAction action, String id, String expected) {
        return publications.publish(namespace, entries(version), "owner", SkillVisibility.PUBLIC, Set.of("SUPER_ADMIN"),
                false, action, id, expected, context);
    }
    private List<PackageEntry> entries(String version) {
        byte[] markdown = ("---\nname: skill\ndescription: Test skill\nversion: " + version + "\n---\n# Skill\nInstructions\n").getBytes(StandardCharsets.UTF_8);
        return List.of(new PackageEntry("SKILL.md", markdown, markdown.length, "text/markdown"));
    }
}
