package com.iflytek.skillhub.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import com.iflytek.skillhub.domain.namespace.Namespace;
import com.iflytek.skillhub.domain.namespace.NamespaceRepository;
import com.iflytek.skillhub.domain.review.PromotionRequestRepository;
import com.iflytek.skillhub.domain.review.ReviewTaskStatus;
import com.iflytek.skillhub.domain.skill.Skill;
import com.iflytek.skillhub.domain.skill.SkillVersion;
import com.iflytek.skillhub.domain.skill.SkillVersionRepository;
import com.iflytek.skillhub.domain.skill.SkillVersionStatus;
import com.iflytek.skillhub.domain.skill.SkillVisibility;
import com.iflytek.skillhub.domain.skill.service.SkillLifecycleProjectionService;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class JpaMySkillQueryRepositoryTest {

    @Mock
    private NamespaceRepository namespaceRepository;

    @Mock
    private PromotionRequestRepository promotionRequestRepository;

    @Mock
    private SkillVersionRepository skillVersionRepository;

    private JpaMySkillQueryRepository repository;

    @BeforeEach
    void setUp() {
        repository = new JpaMySkillQueryRepository(
                namespaceRepository,
                promotionRequestRepository,
                new SkillLifecycleProjectionService(skillVersionRepository)
        );
    }

    @Test
    void getSkillSummaries_projectsOwnerSummaryAndPromotionCapability() {
        Skill skill = new Skill(101L, "team-skill", "user-1", SkillVisibility.PUBLIC);
        skill.setDisplayName("Team Skill");
        skill.setSummary("published");
        ReflectionTestUtils.setField(skill, "id", 2L);
        ReflectionTestUtils.setField(skill, "updatedAt", Instant.parse("2026-03-15T11:00:00Z"));

        SkillVersion publishedVersion = new SkillVersion(2L, "1.2.0", "user-1");
        publishedVersion.setStatus(SkillVersionStatus.PUBLISHED);
        ReflectionTestUtils.setField(publishedVersion, "id", 22L);
        ReflectionTestUtils.setField(publishedVersion, "createdAt", Instant.parse("2026-03-15T10:30:00Z"));

        SkillVersion rejectedVersion = new SkillVersion(2L, "1.3.0", "user-1");
        rejectedVersion.setStatus(SkillVersionStatus.REJECTED);
        ReflectionTestUtils.setField(rejectedVersion, "id", 23L);
        ReflectionTestUtils.setField(rejectedVersion, "createdAt", Instant.parse("2026-03-15T11:30:00Z"));

        Namespace namespace = new Namespace("team-ai", "Team AI", "user-1");
        ReflectionTestUtils.setField(namespace, "id", 101L);

        given(namespaceRepository.findByIdIn(List.of(101L))).willReturn(List.of(namespace));
        given(skillVersionRepository.findBySkillIdAndStatus(2L, SkillVersionStatus.PUBLISHED)).willReturn(List.of(publishedVersion));
        given(skillVersionRepository.findBySkillId(2L)).willReturn(List.of(publishedVersion, rejectedVersion));
        given(promotionRequestRepository.findBySourceSkillIdAndStatus(2L, ReviewTaskStatus.PENDING)).willReturn(Optional.empty());
        given(promotionRequestRepository.findBySourceSkillIdAndStatus(2L, ReviewTaskStatus.APPROVED)).willReturn(Optional.empty());

        var responses = repository.getSkillSummaries(List.of(skill), "user-1");

        assertThat(responses).hasSize(1);
        assertThat(responses.get(0).namespace()).isEqualTo("team-ai");
        assertThat(responses.get(0).publishedVersion()).isNotNull();
        assertThat(responses.get(0).ownerPreviewVersion()).isNotNull();
        assertThat(responses.get(0).ownerPreviewVersion().status()).isEqualTo("REJECTED");
        assertThat(responses.get(0).canSubmitPromotion()).isTrue();
    }

    @Test
    void getSkillSummaries_usesViewerProjectionForNonOwner() {
        Skill skill = new Skill(101L, "shared-skill", "owner-1", SkillVisibility.PUBLIC);
        skill.setDisplayName("Shared Skill");
        ReflectionTestUtils.setField(skill, "id", 3L);

        SkillVersion publishedVersion = new SkillVersion(3L, "1.0.0", "owner-1");
        publishedVersion.setStatus(SkillVersionStatus.PUBLISHED);
        ReflectionTestUtils.setField(publishedVersion, "id", 33L);
        ReflectionTestUtils.setField(publishedVersion, "createdAt", Instant.parse("2026-03-15T10:30:00Z"));

        Namespace namespace = new Namespace("team-ai", "Team AI", "owner-1");
        ReflectionTestUtils.setField(namespace, "id", 101L);

        given(namespaceRepository.findByIdIn(List.of(101L))).willReturn(List.of(namespace));
        given(skillVersionRepository.findBySkillIdAndStatus(3L, SkillVersionStatus.PUBLISHED)).willReturn(List.of(publishedVersion));
        given(promotionRequestRepository.findBySourceSkillIdAndStatus(3L, ReviewTaskStatus.PENDING)).willReturn(Optional.empty());
        given(promotionRequestRepository.findBySourceSkillIdAndStatus(3L, ReviewTaskStatus.APPROVED)).willReturn(Optional.empty());

        var responses = repository.getSkillSummaries(List.of(skill), "viewer-1");

        assertThat(responses).hasSize(1);
        assertThat(responses.get(0).publishedVersion()).isNotNull();
        assertThat(responses.get(0).ownerPreviewVersion()).isNull();
        assertThat(responses.get(0).headlineVersion().status()).isEqualTo("PUBLISHED");
    }

    @Test
    void getSkillSummaries_disablesPromotionWhenNamespaceContextIsMissing() {
        Skill skill = new Skill(101L, "orphan-skill", "user-1", SkillVisibility.PUBLIC);
        skill.setDisplayName("Orphan Skill");
        ReflectionTestUtils.setField(skill, "id", 4L);

        SkillVersion publishedVersion = new SkillVersion(4L, "1.0.0", "user-1");
        publishedVersion.setStatus(SkillVersionStatus.PUBLISHED);
        ReflectionTestUtils.setField(publishedVersion, "id", 44L);
        ReflectionTestUtils.setField(publishedVersion, "createdAt", Instant.parse("2026-03-15T10:30:00Z"));

        given(namespaceRepository.findByIdIn(List.of(101L))).willReturn(List.of());
        given(skillVersionRepository.findBySkillIdAndStatus(4L, SkillVersionStatus.PUBLISHED)).willReturn(List.of(publishedVersion));
        given(skillVersionRepository.findBySkillId(4L)).willReturn(List.of(publishedVersion));

        var responses = repository.getSkillSummaries(List.of(skill), "user-1");

        assertThat(responses).hasSize(1);
        assertThat(responses.get(0).namespace()).isNull();
        assertThat(responses.get(0).publishedVersion()).isNotNull();
        assertThat(responses.get(0).canSubmitPromotion()).isFalse();
    }
}
