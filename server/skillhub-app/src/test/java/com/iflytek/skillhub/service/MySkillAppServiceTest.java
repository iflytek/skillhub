package com.iflytek.skillhub.service;

import com.iflytek.skillhub.domain.namespace.Namespace;
import com.iflytek.skillhub.domain.namespace.NamespaceRepository;
import com.iflytek.skillhub.domain.review.PromotionRequest;
import com.iflytek.skillhub.domain.review.PromotionRequestRepository;
import com.iflytek.skillhub.domain.review.ReviewTaskStatus;
import com.iflytek.skillhub.domain.skill.Skill;
import com.iflytek.skillhub.domain.skill.SkillStatus;
import com.iflytek.skillhub.domain.skill.SkillRepository;
import com.iflytek.skillhub.domain.skill.SkillVersion;
import com.iflytek.skillhub.domain.skill.SkillVersionRepository;
import com.iflytek.skillhub.domain.skill.SkillVersionStatus;
import com.iflytek.skillhub.domain.skill.SkillVisibility;
import com.iflytek.skillhub.domain.skill.service.SkillLifecycleProjectionService;
import com.iflytek.skillhub.domain.social.SkillStar;
import com.iflytek.skillhub.domain.social.SkillStarRepository;
import com.iflytek.skillhub.domain.social.SkillSubscriptionRepository;
import com.iflytek.skillhub.repository.JpaMySkillQueryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class MySkillAppServiceTest {

    @Mock
    private SkillRepository skillRepository;

    @Mock
    private NamespaceRepository namespaceRepository;

    @Mock
    private SkillVersionRepository skillVersionRepository;

    @Mock
    private SkillStarRepository skillStarRepository;

    @Mock
    private SkillSubscriptionRepository skillSubscriptionRepository;

    @Mock
    private PromotionRequestRepository promotionRequestRepository;

    private MySkillAppService service;
    private SkillLifecycleProjectionService skillLifecycleProjectionService;
    private JpaMySkillQueryRepository mySkillQueryRepository;

    @BeforeEach
    void setUp() {
        skillLifecycleProjectionService = new SkillLifecycleProjectionService(skillVersionRepository);
        mySkillQueryRepository = new JpaMySkillQueryRepository(
                namespaceRepository,
                promotionRequestRepository,
                skillLifecycleProjectionService
        );
        service = new MySkillAppService(
                skillRepository,
                skillVersionRepository,
                skillStarRepository,
                skillSubscriptionRepository,
                mySkillQueryRepository,
                skillLifecycleProjectionService
        );
    }

    @Test
    void listMyStars_loadsAllPagesOfStars() {
        SkillStar firstStar = new SkillStar(1L, "user-1");
        ReflectionTestUtils.setField(firstStar, "createdAt", Instant.parse("2026-03-14T10:00:00Z"));
        SkillStar secondStar = new SkillStar(2L, "user-1");
        ReflectionTestUtils.setField(secondStar, "createdAt", Instant.parse("2026-03-14T11:00:00Z"));

        given(skillStarRepository.findByUserId("user-1", PageRequest.of(1, 1)))
                .willReturn(new PageImpl<>(List.of(secondStar), PageRequest.of(1, 1), 2));

        Skill firstSkill = new Skill(1L, "first-skill", "user-1", SkillVisibility.PUBLIC);
        firstSkill.setDisplayName("First Skill");
        firstSkill.setSummary("first summary");
        ReflectionTestUtils.setField(firstSkill, "id", 1L);
        ReflectionTestUtils.setField(firstSkill, "starCount", 1);
        ReflectionTestUtils.setField(firstSkill, "namespaceId", 101L);
        ReflectionTestUtils.setField(firstSkill, "updatedAt", Instant.parse("2026-03-14T10:00:00Z"));

        Skill secondSkill = new Skill(2L, "second-skill", "user-1", SkillVisibility.PUBLIC);
        secondSkill.setDisplayName("Second Skill");
        secondSkill.setSummary("second summary");
        ReflectionTestUtils.setField(secondSkill, "id", 2L);
        ReflectionTestUtils.setField(secondSkill, "starCount", 2);
        ReflectionTestUtils.setField(secondSkill, "namespaceId", 101L);
        ReflectionTestUtils.setField(secondSkill, "updatedAt", Instant.parse("2026-03-14T11:00:00Z"));

        given(skillRepository.findByIdIn(List.of(2L))).willReturn(List.of(secondSkill));
        given(skillVersionRepository.findBySkillIdAndStatus(2L, SkillVersionStatus.PUBLISHED)).willReturn(List.of());
        given(skillVersionRepository.findBySkillId(2L)).willReturn(List.of());
        given(namespaceRepository.findByIdIn(List.of(101L))).willReturn(List.of(new Namespace("team-ai", "Team AI", "user-1")));

        var stars = service.listMyStars("user-1", 1, 1);

        assertThat(stars.total()).isEqualTo(2);
        assertThat(stars.page()).isEqualTo(1);
        assertThat(stars.size()).isEqualTo(1);
        assertThat(stars.items()).extracting("slug").containsExactly("second-skill");
    }

    @Test
    void listMySkills_includes_pendingReviewVersionWhenNoPublishedPointerExists() {
        Skill skill = new Skill(101L, "draft-skill", "user-1", SkillVisibility.PUBLIC);
        skill.setDisplayName("Draft Skill");
        skill.setSummary("pending review");
        ReflectionTestUtils.setField(skill, "id", 1L);
        ReflectionTestUtils.setField(skill, "updatedAt", Instant.parse("2026-03-15T10:00:00Z"));

        SkillVersion pendingVersion = new SkillVersion(1L, "1.0.0", "user-1");
        pendingVersion.setStatus(SkillVersionStatus.PENDING_REVIEW);
        ReflectionTestUtils.setField(pendingVersion, "id", 11L);
        ReflectionTestUtils.setField(pendingVersion, "createdAt", Instant.parse("2026-03-15T09:30:00Z"));

        given(skillRepository.findByOwnerId("user-1", PageRequest.of(0, 10)))
                .willReturn(new PageImpl<>(List.of(skill), PageRequest.of(0, 10), 1));
        given(skillVersionRepository.findBySkillIdAndStatus(1L, SkillVersionStatus.PUBLISHED)).willReturn(List.of());
        given(skillVersionRepository.findBySkillId(1L)).willReturn(List.of(pendingVersion));
        given(namespaceRepository.findByIdIn(List.of(101L))).willReturn(List.of(new Namespace("team-ai", "Team AI", "user-1")));

        var skills = service.listMySkills("user-1", 0, 10);

        assertThat(skills.items()).hasSize(1);
        assertThat(skills.items().get(0).headlineVersion()).isNotNull();
        assertThat(skills.items().get(0).headlineVersion().version()).isEqualTo("1.0.0");
        assertThat(skills.items().get(0).headlineVersion().status()).isEqualTo("PENDING_REVIEW");
        assertThat(skills.items().get(0).ownerPreviewVersion()).isNotNull();
        assertThat(skills.items().get(0).ownerPreviewVersion().id()).isEqualTo(11L);
        assertThat(skills.items().get(0).canSubmitPromotion()).isFalse();
    }

    @Test
    void listMySkills_marksTeamPublishedSkillAsPromotable() {
        Skill skill = new Skill(101L, "team-skill", "user-1", SkillVisibility.PUBLIC);
        skill.setDisplayName("Team Skill");
        skill.setSummary("published");
        ReflectionTestUtils.setField(skill, "id", 2L);
        ReflectionTestUtils.setField(skill, "updatedAt", Instant.parse("2026-03-15T11:00:00Z"));

        SkillVersion publishedVersion = new SkillVersion(2L, "1.2.0", "user-1");
        publishedVersion.setStatus(SkillVersionStatus.PUBLISHED);
        ReflectionTestUtils.setField(publishedVersion, "id", 22L);
        ReflectionTestUtils.setField(publishedVersion, "createdAt", Instant.parse("2026-03-15T10:30:00Z"));

        Namespace namespace = new Namespace("team-ai", "Team AI", "user-1");
        ReflectionTestUtils.setField(namespace, "id", 101L);

        given(skillRepository.findByOwnerId("user-1", PageRequest.of(0, 10)))
                .willReturn(new PageImpl<>(List.of(skill), PageRequest.of(0, 10), 1));
        given(skillVersionRepository.findBySkillIdAndStatus(2L, SkillVersionStatus.PUBLISHED)).willReturn(List.of(publishedVersion));
        given(skillVersionRepository.findBySkillId(2L)).willReturn(List.of(publishedVersion));
        given(namespaceRepository.findByIdIn(List.of(101L))).willReturn(List.of(namespace));
        given(promotionRequestRepository.findBySourceSkillIdAndStatus(2L, ReviewTaskStatus.PENDING)).willReturn(Optional.empty());
        given(promotionRequestRepository.findBySourceSkillIdAndStatus(2L, ReviewTaskStatus.APPROVED)).willReturn(Optional.empty());

        var skills = service.listMySkills("user-1", 0, 10);

        assertThat(skills.items()).hasSize(1);
        assertThat(skills.items().get(0).publishedVersion()).isNotNull();
        assertThat(skills.items().get(0).publishedVersion().id()).isEqualTo(22L);
        assertThat(skills.items().get(0).headlineVersion()).isNotNull();
        assertThat(skills.items().get(0).headlineVersion().status()).isEqualTo("PUBLISHED");
        assertThat(skills.items().get(0).canSubmitPromotion()).isTrue();
    }

    @Test
    void listMySkills_hidesPromotionWhenPendingRequestExists() {
        Skill skill = new Skill(101L, "team-skill", "user-1", SkillVisibility.PUBLIC);
        skill.setDisplayName("Team Skill");
        ReflectionTestUtils.setField(skill, "id", 2L);

        SkillVersion publishedVersion = new SkillVersion(2L, "1.2.0", "user-1");
        publishedVersion.setStatus(SkillVersionStatus.PUBLISHED);
        ReflectionTestUtils.setField(publishedVersion, "id", 22L);
        ReflectionTestUtils.setField(publishedVersion, "createdAt", Instant.parse("2026-03-15T10:30:00Z"));

        Namespace namespace = new Namespace("team-ai", "Team AI", "user-1");
        ReflectionTestUtils.setField(namespace, "id", 101L);

        given(skillRepository.findByOwnerId("user-1", PageRequest.of(0, 10)))
                .willReturn(new PageImpl<>(List.of(skill), PageRequest.of(0, 10), 1));
        given(skillVersionRepository.findBySkillIdAndStatus(2L, SkillVersionStatus.PUBLISHED)).willReturn(List.of(publishedVersion));
        given(skillVersionRepository.findBySkillId(2L)).willReturn(List.of(publishedVersion));
        given(namespaceRepository.findByIdIn(List.of(101L))).willReturn(List.of(namespace));
        given(promotionRequestRepository.findBySourceSkillIdAndStatus(2L, ReviewTaskStatus.PENDING))
                .willReturn(Optional.of(new PromotionRequest(2L, 22L, 999L, "user-1")));

        var skills = service.listMySkills("user-1", 0, 10);

        assertThat(skills.items()).hasSize(1);
        assertThat(skills.items().get(0).canSubmitPromotion()).isFalse();
    }

    @Test
    void listMySkills_filtersPendingReviewForRegularUsers() {
        Skill pendingSkill = createSkill(1L, 101L, "pending-skill", "user-1");
        SkillVersion pendingVersion = createVersion(1L, 11L, "1.0.0", SkillVersionStatus.PENDING_REVIEW, "2026-03-15T09:30:00Z");
        Skill publishedSkill = createSkill(2L, 101L, "published-skill", "user-1");
        SkillVersion publishedVersion = createVersion(2L, 22L, "1.2.0", SkillVersionStatus.PUBLISHED, "2026-03-15T10:30:00Z");

        given(skillRepository.findByOwnerId("user-1")).willReturn(List.of(pendingSkill, publishedSkill));
        given(skillVersionRepository.findBySkillId(1L)).willReturn(List.of(pendingVersion));
        given(skillVersionRepository.findBySkillId(2L)).willReturn(List.of(publishedVersion));
        given(namespaceRepository.findByIdIn(List.of(101L))).willReturn(List.of(namespace(101L, "team-ai")));

        var result = service.listMySkills("user-1", 0, 10, "PENDING_REVIEW", Set.of("USER"));

        assertThat(result.total()).isEqualTo(1);
        assertThat(result.items()).extracting("slug").containsExactly("pending-skill");
    }

    @Test
    void listMySkills_filtersHiddenOnlyForSuperAdmins() {
        Skill hiddenSkill = createSkill(3L, 101L, "hidden-skill", "user-1");
        hiddenSkill.setHidden(true);
        Skill publishedSkill = createSkill(4L, 101L, "published-skill", "user-1");
        SkillVersion hiddenVersion = createVersion(3L, 33L, "1.0.0", SkillVersionStatus.PUBLISHED, "2026-03-15T09:30:00Z");

        given(skillRepository.findByOwnerId("user-1")).willReturn(List.of(hiddenSkill, publishedSkill));
        given(skillVersionRepository.findBySkillId(3L)).willReturn(List.of(hiddenVersion));
        given(namespaceRepository.findByIdIn(List.of(101L))).willReturn(List.of(namespace(101L, "team-ai")));

        var regularUserResult = service.listMySkills("user-1", 0, 10, "HIDDEN", Set.of("USER"));
        var superAdminResult = service.listMySkills("user-1", 0, 10, "HIDDEN", Set.of("SUPER_ADMIN"));

        assertThat(regularUserResult.total()).isZero();
        assertThat(superAdminResult.total()).isEqualTo(1);
        assertThat(superAdminResult.items()).extracting("slug").containsExactly("hidden-skill");
    }

    @Test
    void listMySkills_exposesRejectedOwnerPreviewInSummary() {
        Skill skill = createSkill(5L, 101L, "rejected-skill", "user-1");
        SkillVersion rejectedVersion = createVersion(5L, 55L, "1.1.0", SkillVersionStatus.REJECTED, "2026-03-15T09:30:00Z");

        given(skillRepository.findByOwnerId("user-1", PageRequest.of(0, 10)))
                .willReturn(new PageImpl<>(List.of(skill), PageRequest.of(0, 10), 1));
        given(skillVersionRepository.findBySkillId(5L)).willReturn(List.of(rejectedVersion));
        given(namespaceRepository.findByIdIn(List.of(101L))).willReturn(List.of(namespace(101L, "team-ai")));

        var result = service.listMySkills("user-1", 0, 10);

        assertThat(result.items()).hasSize(1);
        assertThat(result.items().get(0).ownerPreviewVersion()).isNotNull();
        assertThat(result.items().get(0).ownerPreviewVersion().status()).isEqualTo("REJECTED");
        assertThat(result.items().get(0).headlineVersion().status()).isEqualTo("REJECTED");
    }

    private Skill createSkill(Long id, Long namespaceId, String slug, String ownerId) {
        Skill skill = new Skill(namespaceId, slug, ownerId, SkillVisibility.PUBLIC);
        skill.setDisplayName(slug);
        ReflectionTestUtils.setField(skill, "id", id);
        ReflectionTestUtils.setField(skill, "namespaceId", namespaceId);
        ReflectionTestUtils.setField(skill, "updatedAt", Instant.parse("2026-03-15T11:00:00Z"));
        ReflectionTestUtils.setField(skill, "ratingAvg", BigDecimal.ZERO);
        ReflectionTestUtils.setField(skill, "ratingCount", 0);
        ReflectionTestUtils.setField(skill, "downloadCount", 0L);
        ReflectionTestUtils.setField(skill, "starCount", 0);
        skill.setStatus(SkillStatus.ACTIVE);
        return skill;
    }

    private SkillVersion createVersion(Long skillId, Long id, String version, SkillVersionStatus status, String createdAt) {
        SkillVersion skillVersion = new SkillVersion(skillId, version, "user-1");
        skillVersion.setStatus(status);
        ReflectionTestUtils.setField(skillVersion, "id", id);
        ReflectionTestUtils.setField(skillVersion, "createdAt", Instant.parse(createdAt));
        return skillVersion;
    }

    private Namespace namespace(Long id, String slug) {
        Namespace namespace = new Namespace(slug, slug, "user-1");
        ReflectionTestUtils.setField(namespace, "id", id);
        return namespace;
    }
}
