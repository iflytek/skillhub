package com.iflytek.skillhub.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.iflytek.skillhub.domain.audit.AuditLogService;
import com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException;
import com.iflytek.skillhub.domain.shared.exception.DomainForbiddenException;
import com.iflytek.skillhub.domain.skill.Skill;
import com.iflytek.skillhub.domain.skill.SkillRepository;
import com.iflytek.skillhub.domain.skill.SkillStatus;
import com.iflytek.skillhub.domain.skill.SkillVersion;
import com.iflytek.skillhub.domain.skill.SkillVersionRepository;
import com.iflytek.skillhub.domain.skill.SkillVersionStatus;
import com.iflytek.skillhub.domain.skill.SkillVisibility;
import com.iflytek.skillhub.domain.skill.VisibilityChecker;
import com.iflytek.skillhub.domain.social.SkillRating;
import com.iflytek.skillhub.domain.social.SkillRatingService;
import com.iflytek.skillhub.observability.RequestIdAccessor;
import com.iflytek.skillhub.repository.SkillReviewQueryRepository;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
class SkillReviewAppServiceTest {

    @Mock private SkillRepository skillRepository;
    @Mock private SkillVersionRepository skillVersionRepository;
    @Mock private SkillRatingService ratingService;
    @Mock private SkillReviewQueryRepository queryRepository;
    @Mock private AuditLogService auditLogService;
    @Mock private RequestIdAccessor requestIdAccessor;

    private SkillReviewAppService service;

    @BeforeEach
    void setUp() {
        service = new SkillReviewAppService(
                skillRepository,
                skillVersionRepository,
                new VisibilityChecker(),
                ratingService,
                queryRepository,
                auditLogService,
                requestIdAccessor
        );
    }

    @Test
    void publicListingExcludesHiddenReviewsForRegularViewer() {
        Skill skill = publishedSkill(SkillVisibility.PUBLIC);
        when(skillRepository.findById(10L)).thenReturn(Optional.of(skill));
        when(queryRepository.list(eq(10L), eq(null), eq(false), any(Pageable.class)))
                .thenReturn(Page.empty());

        service.list(10L, null, Map.of(), Set.of(), 0, 20);

        verify(queryRepository).list(eq(10L), eq(null), eq(false), any(Pageable.class));
    }

    @Test
    void skillAdminListingIncludesHiddenReviews() {
        Skill skill = publishedSkill(SkillVisibility.PUBLIC);
        when(skillRepository.findById(10L)).thenReturn(Optional.of(skill));
        when(queryRepository.list(eq(10L), eq("admin"), eq(true), any(Pageable.class)))
                .thenReturn(Page.empty());

        service.list(10L, "admin", Map.of(), Set.of("SKILL_ADMIN"), 0, 20);

        verify(queryRepository).list(eq(10L), eq("admin"), eq(true), any(Pageable.class));
    }

    @Test
    void reviewPaginationRejectsNegativePageAndSizesOutsideOneToOneHundred() {
        Skill skill = publishedSkill(SkillVisibility.PUBLIC);
        when(skillRepository.findById(10L)).thenReturn(Optional.of(skill));

        assertThatThrownBy(() -> service.list(10L, null, Map.of(), Set.of(), -1, 20))
                .isInstanceOf(DomainBadRequestException.class);
        assertThatThrownBy(() -> service.list(10L, null, Map.of(), Set.of(), 0, 0))
                .isInstanceOf(DomainBadRequestException.class);
        assertThatThrownBy(() -> service.list(10L, null, Map.of(), Set.of(), 0, 101))
                .isInstanceOf(DomainBadRequestException.class);

        verify(queryRepository, never()).list(any(), any(), anyBoolean(), any(Pageable.class));
    }

    @Test
    void privateSkillReviewMutationRequiresSkillAccess() {
        Skill skill = publishedSkill(SkillVisibility.PRIVATE);
        when(skillRepository.findById(10L)).thenReturn(Optional.of(skill));

        assertThatThrownBy(() -> service.upsert(
                10L, "other-user", (short) 5, "great", Map.of(), Set.of()))
                .isInstanceOf(DomainForbiddenException.class);

        verify(ratingService, never()).upsertReview(any(), any(), any(Short.class), any());
    }

    @Test
    void unpublishedSkillReviewMutationIsRejectedServerSide() {
        Skill skill = publishedSkill(SkillVisibility.PUBLIC);
        SkillVersion pending = new SkillVersion(10L, "1.0.0", "owner");
        pending.setStatus(SkillVersionStatus.PENDING_REVIEW);
        when(skillRepository.findById(10L)).thenReturn(Optional.of(skill));
        when(skillVersionRepository.findById(100L)).thenReturn(Optional.of(pending));

        assertThatThrownBy(() -> service.upsert(
                10L, "owner", (short) 5, "not published", Map.of(), Set.of()))
                .isInstanceOf(com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException.class)
                .hasMessage("error.skillReview.notInteractable");

        verify(ratingService, never()).upsertReview(any(), any(), any(Short.class), any());
    }

    @Test
    void archivedSkillReviewMutationIsRejectedServerSide() {
        Skill skill = publishedSkill(SkillVisibility.PUBLIC);
        skill.setStatus(SkillStatus.ARCHIVED);
        SkillVersion published = new SkillVersion(10L, "1.0.0", "owner");
        published.setStatus(SkillVersionStatus.PUBLISHED);
        when(skillRepository.findById(10L)).thenReturn(Optional.of(skill));
        when(skillVersionRepository.findById(100L)).thenReturn(Optional.of(published));

        assertThatThrownBy(() -> service.upsert(
                10L, "owner", (short) 5, "archived", Map.of(), Set.of()))
                .isInstanceOf(DomainBadRequestException.class)
                .hasMessage("error.skillReview.notInteractable");

        verify(ratingService, never()).upsertReview(any(), any(), any(Short.class), any());
    }

    @Test
    void authorCanClearReviewAfterSkillStopsBeingInteractable() {
        SkillRating review = new SkillRating(10L, "author", (short) 4);
        review.updateReview((short) 4, "Remove me");
        review.clearReview();
        when(ratingService.clearReview(10L, "author")).thenReturn(review);

        service.clear(10L, "author", Map.of(), Set.of());

        verify(ratingService).clearReview(10L, "author");
        verify(skillRepository, never()).findById(any());
        verify(skillVersionRepository, never()).findById(any());
    }

    @Test
    void authorCanReadOwnReviewAfterSkillStopsBeingVisible() {
        SkillRating review = new SkillRating(10L, "author", (short) 4);
        review.updateReview((short) 4, "My review");
        when(ratingService.getUserFeedback(10L, "author")).thenReturn(Optional.of(review));

        service.getMine(10L, "author", Map.of(), Set.of());

        verify(ratingService).getUserFeedback(10L, "author");
        verify(skillRepository, never()).findById(any());
    }

    @Test
    void hideWritesModerationAuditInSameWorkflow() {
        SkillRating review = new SkillRating(10L, "author", (short) 4);
        review.updateReview((short) 4, "helpful review");
        when(ratingService.hideReview(null, "admin", "spam")).thenReturn(review);
        when(requestIdAccessor.current()).thenReturn("request-1");

        service.hide(null, "admin", "spam", new AuditRequestContext("127.0.0.1", "test"));

        verify(auditLogService).record(
                eq("admin"),
                eq("SKILL_REVIEW_HIDE"),
                eq("SKILL_REVIEW"),
                eq(null),
                eq("request-1"),
                eq("127.0.0.1"),
                eq("test"),
                eq("{\"skillId\":10,\"reason\":\"spam\"}")
        );
    }

    private Skill publishedSkill(SkillVisibility visibility) {
        Skill skill = new Skill(1L, "demo", "owner", visibility);
        skill.setLatestVersionId(100L);
        return skill;
    }
}
