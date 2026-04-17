package com.iflytek.skillhub.domain.skill.service;

import com.iflytek.skillhub.domain.namespace.NamespaceRole;
import com.iflytek.skillhub.domain.review.ReviewTask;
import com.iflytek.skillhub.domain.review.ReviewTaskRepository;
import com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException;
import com.iflytek.skillhub.domain.shared.exception.DomainForbiddenException;
import com.iflytek.skillhub.domain.skill.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Clock;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link SkillReviewSubmitService}.
 */
@ExtendWith(MockitoExtension.class)
class SkillReviewSubmitServiceTest {

    @Mock
    private SkillRepository skillRepository;

    @Mock
    private SkillVersionRepository skillVersionRepository;

    @Mock
    private ReviewTaskRepository reviewTaskRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    private SkillReviewSubmitService service;

    @BeforeEach
    void setUp() {
        service = new SkillReviewSubmitService(
                skillRepository,
                skillVersionRepository,
                reviewTaskRepository,
                null, // namespaceMemberRepository not used in these tests
                eventPublisher,
                Clock.systemUTC()
        );
    }

    @Nested
    @DisplayName("submitForReview")
    class SubmitForReviewTests {

        @Test
        @DisplayName("should transition UPLOADED version to PENDING_REVIEW")
        void shouldTransitionToPendingReview() {
            // Given
            Long skillId = 1L;
            Long versionId = 100L;
            String userId = "user-1";
            Long namespaceId = 10L;

            Skill skill = createSkill(skillId, userId, namespaceId, SkillVisibility.PRIVATE);
            SkillVersion version = createVersion(versionId, skillId, SkillVersionStatus.UPLOADED);

            when(skillRepository.findById(skillId)).thenReturn(Optional.of(skill));
            when(skillVersionRepository.findById(versionId)).thenReturn(Optional.of(version));
            when(reviewTaskRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            Map<Long, NamespaceRole> roles = Map.of();

            // When
            service.submitForReview(skillId, versionId, SkillVisibility.PUBLIC, userId, roles);

            // Then
            assertEquals(SkillVersionStatus.PENDING_REVIEW, version.getStatus());
            assertEquals(SkillVisibility.PUBLIC, version.getRequestedVisibility());
            verify(reviewTaskRepository).save(any(ReviewTask.class));
        }

        @Test
        @DisplayName("should accept DRAFT version (legacy compatibility)")
        void shouldAcceptDraftForLegacyCompatibility() {
            // Given
            Long skillId = 1L;
            Long versionId = 100L;
            String userId = "user-1";
            Long namespaceId = 10L;

            Skill skill = createSkill(skillId, userId, namespaceId, SkillVisibility.PRIVATE);
            SkillVersion version = createVersion(versionId, skillId, SkillVersionStatus.DRAFT);

            when(skillRepository.findById(skillId)).thenReturn(Optional.of(skill));
            when(skillVersionRepository.findById(versionId)).thenReturn(Optional.of(version));
            when(reviewTaskRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            Map<Long, NamespaceRole> roles = Map.of();

            // When
            service.submitForReview(skillId, versionId, SkillVisibility.PUBLIC, userId, roles);

            // Then
            assertEquals(SkillVersionStatus.PENDING_REVIEW, version.getStatus());
            assertEquals(SkillVisibility.PUBLIC, version.getRequestedVisibility());
            verify(reviewTaskRepository).save(any(ReviewTask.class));
        }

        @Test
        @DisplayName("should reject when version is neither UPLOADED nor DRAFT")
        void shouldRejectWhenNotUploadedOrDraft() {
            // Given
            Long skillId = 1L;
            Long versionId = 100L;
            String userId = "user-1";

            Skill skill = createSkill(skillId, userId, 10L, SkillVisibility.PRIVATE);
            SkillVersion version = createVersion(versionId, skillId, SkillVersionStatus.PUBLISHED);

            when(skillRepository.findById(skillId)).thenReturn(Optional.of(skill));
            when(skillVersionRepository.findById(versionId)).thenReturn(Optional.of(version));

            // When/Then
            assertThrows(DomainBadRequestException.class,
                    () -> service.submitForReview(skillId, versionId, SkillVisibility.PUBLIC, userId, Map.of()));
        }

        @Test
        @DisplayName("should reject when user is not owner")
        void shouldRejectWhenNotOwner() {
            // Given
            Long skillId = 1L;
            Long versionId = 100L;
            String ownerId = "owner-1";
            String otherUserId = "other-user";

            Skill skill = createSkill(skillId, ownerId, 10L, SkillVisibility.PRIVATE);
            SkillVersion version = createVersion(versionId, skillId, SkillVersionStatus.UPLOADED);

            when(skillRepository.findById(skillId)).thenReturn(Optional.of(skill));
            when(skillVersionRepository.findById(versionId)).thenReturn(Optional.of(version));

            // When/Then
            assertThrows(DomainForbiddenException.class,
                    () -> service.submitForReview(skillId, versionId, SkillVisibility.PUBLIC, otherUserId, Map.of()));
        }
    }

    @Nested
    @DisplayName("confirmPublish")
    class ConfirmPublishTests {

        @Test
        @DisplayName("should transition UPLOADED version to PUBLISHED for PRIVATE skill")
        void shouldTransitionToPublished() {
            // Given
            Long skillId = 1L;
            Long versionId = 100L;
            String userId = "user-1";
            Long namespaceId = 10L;

            Skill skill = createSkill(skillId, userId, namespaceId, SkillVisibility.PRIVATE);
            SkillVersion version = createVersion(versionId, skillId, SkillVersionStatus.UPLOADED);

            when(skillRepository.findById(skillId)).thenReturn(Optional.of(skill));
            when(skillVersionRepository.findById(versionId)).thenReturn(Optional.of(version));

            // When
            service.confirmPublish(skillId, versionId, userId, Map.of());

            // Then
            assertEquals(SkillVersionStatus.PUBLISHED, version.getStatus());
            assertNotNull(version.getPublishedAt());
            assertEquals(versionId, skill.getLatestVersionId());
            verify(skillRepository).save(skill);
        }

        @Test
        @DisplayName("should transition DRAFT version to PUBLISHED for PRIVATE skill (legacy compatibility)")
        void shouldTransitionDraftToPublished() {
            // Given
            Long skillId = 1L;
            Long versionId = 100L;
            String userId = "user-1";
            Long namespaceId = 10L;

            Skill skill = createSkill(skillId, userId, namespaceId, SkillVisibility.PRIVATE);
            SkillVersion version = createVersion(versionId, skillId, SkillVersionStatus.DRAFT);

            when(skillRepository.findById(skillId)).thenReturn(Optional.of(skill));
            when(skillVersionRepository.findById(versionId)).thenReturn(Optional.of(version));

            // When
            service.confirmPublish(skillId, versionId, userId, Map.of());

            // Then
            assertEquals(SkillVersionStatus.PUBLISHED, version.getStatus());
            assertNotNull(version.getPublishedAt());
            assertEquals(versionId, skill.getLatestVersionId());
            verify(skillRepository).save(skill);
        }

        @Test
        @DisplayName("should reject when skill is not PRIVATE")
        void shouldRejectWhenNotPrivate() {
            // Given
            Long skillId = 1L;
            Long versionId = 100L;
            String userId = "user-1";

            Skill skill = createSkill(skillId, userId, 10L, SkillVisibility.PUBLIC);
            SkillVersion version = createVersion(versionId, skillId, SkillVersionStatus.UPLOADED);

            when(skillRepository.findById(skillId)).thenReturn(Optional.of(skill));
            when(skillVersionRepository.findById(versionId)).thenReturn(Optional.of(version));

            // When/Then
            assertThrows(DomainBadRequestException.class,
                    () -> service.confirmPublish(skillId, versionId, userId, Map.of()));
        }

        @Test
        @DisplayName("should reject when version is neither UPLOADED nor DRAFT")
        void shouldRejectWhenNotUploadedOrDraft() {
            // Given
            Long skillId = 1L;
            Long versionId = 100L;
            String userId = "user-1";

            Skill skill = createSkill(skillId, userId, 10L, SkillVisibility.PRIVATE);
            SkillVersion version = createVersion(versionId, skillId, SkillVersionStatus.PUBLISHED);

            when(skillRepository.findById(skillId)).thenReturn(Optional.of(skill));
            when(skillVersionRepository.findById(versionId)).thenReturn(Optional.of(version));

            // When/Then
            assertThrows(DomainBadRequestException.class,
                    () -> service.confirmPublish(skillId, versionId, userId, Map.of()));
        }
    }

    private Skill createSkill(Long id, String ownerId, Long namespaceId, SkillVisibility visibility) {
        Skill skill = new Skill(namespaceId, "test-skill", ownerId, visibility);
        setField(skill, "id", id);
        return skill;
    }

    private SkillVersion createVersion(Long id, Long skillId, SkillVersionStatus status) {
        SkillVersion version = new SkillVersion(skillId, "1.0.0", "user-1");
        setField(version, "id", id);
        version.setStatus(status);
        return version;
    }

    private void setField(Object target, String fieldName, Object value) {
        try {
            java.lang.reflect.Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
