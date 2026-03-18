package com.iflytek.skillhub.service;

import com.iflytek.skillhub.domain.namespace.Namespace;
import com.iflytek.skillhub.domain.namespace.NamespaceRepository;
import com.iflytek.skillhub.domain.review.PromotionRequest;
import com.iflytek.skillhub.domain.review.PromotionRequestRepository;
import com.iflytek.skillhub.domain.review.ReviewTaskStatus;
import com.iflytek.skillhub.domain.skill.Skill;
import com.iflytek.skillhub.domain.skill.SkillRepository;
import com.iflytek.skillhub.domain.skill.SkillVersion;
import com.iflytek.skillhub.domain.skill.SkillVersionRepository;
import com.iflytek.skillhub.domain.skill.SkillVersionStatus;
import com.iflytek.skillhub.domain.skill.SkillVisibility;
import com.iflytek.skillhub.domain.skill.service.SkillLifecycleProjectionService;
import com.iflytek.skillhub.domain.social.SkillStar;
import com.iflytek.skillhub.domain.social.SkillStarRepository;
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
    private PromotionRequestRepository promotionRequestRepository;

    private MySkillAppService service;
    private SkillLifecycleProjectionService skillLifecycleProjectionService;

    @BeforeEach
    void setUp() {
        skillLifecycleProjectionService = new SkillLifecycleProjectionService(skillVersionRepository);
        service = new MySkillAppService(
                skillRepository,
                namespaceRepository,
                skillVersionRepository,
                skillStarRepository,
                promotionRequestRepository,
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
        given(skillVersionRepository.findBySkillIdAndStatus(1L, SkillVersionStatus.PENDING_REVIEW)).willReturn(List.of(pendingVersion));
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
        given(namespaceRepository.findByIdIn(List.of(101L))).willReturn(List.of(namespace));
        given(promotionRequestRepository.findBySourceSkillIdAndStatus(2L, ReviewTaskStatus.PENDING))
                .willReturn(Optional.of(new PromotionRequest(2L, 22L, 999L, "user-1")));

        var skills = service.listMySkills("user-1", 0, 10);

        assertThat(skills.items()).hasSize(1);
        assertThat(skills.items().get(0).canSubmitPromotion()).isFalse();
    }
}
