package com.iflytek.skillhub.domain.review;

import com.iflytek.skillhub.domain.namespace.NamespaceRole;
import com.iflytek.skillhub.domain.namespace.NamespaceType;
import com.iflytek.skillhub.domain.skill.Skill;
import com.iflytek.skillhub.domain.skill.SkillVisibility;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class ReviewPermissionCheckerTest {

    private final ReviewPermissionChecker checker = new ReviewPermissionChecker();

    // --- canReview tests ---

    @Test
    void regularUserCannotReviewOwnSubmission() {
        String userId = "user-1";
        ReviewTask task = new ReviewTask(1L, 10L, userId);
        assertFalse(checker.canReview(task, userId,
                NamespaceType.TEAM, Map.of(), Set.of()));
    }

    @Test
    void skillAdminCannotReviewOwnSubmissionWithoutNamespaceRole() {
        String userId = "user-1";
        ReviewTask task = new ReviewTask(1L, 10L, userId);
        assertFalse(checker.canReview(task, userId,
                NamespaceType.TEAM, Map.of(), Set.of("SKILL_ADMIN")));
    }

    @Test
    void skillAdminNamespaceAdminCanReviewOwnSubmission() {
        String userId = "user-1";
        ReviewTask task = new ReviewTask(1L, 10L, userId);
        assertTrue(checker.canReview(task, userId,
                NamespaceType.TEAM, Map.of(10L, NamespaceRole.ADMIN), Set.of("SKILL_ADMIN")));
    }

    @Test
    void skillAdminNamespaceOwnerCanReviewOwnSubmission() {
        String userId = "user-1";
        ReviewTask task = new ReviewTask(1L, 10L, userId);
        assertTrue(checker.canReview(task, userId,
                NamespaceType.TEAM, Map.of(10L, NamespaceRole.OWNER), Set.of("SKILL_ADMIN")));
    }

    @Test
    void skillAdminNamespaceMemberCannotReviewOwnSubmission() {
        String userId = "user-1";
        ReviewTask task = new ReviewTask(1L, 10L, userId);
        assertFalse(checker.canReview(task, userId,
                NamespaceType.TEAM, Map.of(10L, NamespaceRole.MEMBER), Set.of("SKILL_ADMIN")));
    }

    @Test
    void superAdminCannotReviewOwnSubmission() {
        String userId = "user-1";
        ReviewTask task = new ReviewTask(1L, 10L, userId);
        assertTrue(checker.canReview(task, userId,
                NamespaceType.TEAM, Map.of(), Set.of("SUPER_ADMIN")));
    }

    @Test
    void regularUserCannotReviewOwnGlobalSubmission() {
        String userId = "user-1";
        ReviewTask task = new ReviewTask(1L, 1L, userId);
        assertFalse(checker.canReview(task, userId,
                NamespaceType.GLOBAL, Map.of(), Set.of()));
    }

    @Test
    void superAdminCanReviewOwnGlobalSubmission() {
        String userId = "user-1";
        ReviewTask task = new ReviewTask(1L, 1L, userId);
        assertTrue(checker.canReview(task, userId,
                NamespaceType.GLOBAL, Map.of(), Set.of("SUPER_ADMIN")));
    }

    @Test
    void teamAdminCanReviewOwnTeamSubmission() {
        String userId = "user-1";
        ReviewTask task = new ReviewTask(1L, 10L, userId);
        assertTrue(checker.canReview(task, userId,
                NamespaceType.TEAM,
                Map.of(10L, NamespaceRole.ADMIN), Set.of()));
    }

    @Test
    void teamOwnerCanReviewOwnTeamSubmission() {
        String userId = "user-1";
        ReviewTask task = new ReviewTask(1L, 10L, userId);
        assertTrue(checker.canReview(task, userId,
                NamespaceType.TEAM,
                Map.of(10L, NamespaceRole.OWNER), Set.of()));
    }

    @Test
    void teamAdminCanReviewTeamSkill() {
        ReviewTask task = new ReviewTask(1L, 10L, "user-2");
        assertTrue(checker.canReview(task, "user-1",
                NamespaceType.TEAM,
                Map.of(10L, NamespaceRole.ADMIN), Set.of()));
    }

    @Test
    void teamOwnerCanReviewTeamSkill() {
        ReviewTask task = new ReviewTask(1L, 10L, "user-2");
        assertTrue(checker.canReview(task, "user-1",
                NamespaceType.TEAM,
                Map.of(10L, NamespaceRole.OWNER), Set.of()));
    }

    @Test
    void teamMemberCannotReviewTeamSkill() {
        ReviewTask task = new ReviewTask(1L, 10L, "user-2");
        assertFalse(checker.canReview(task, "user-1",
                NamespaceType.TEAM,
                Map.of(10L, NamespaceRole.MEMBER), Set.of()));
    }

    @Test
    void skillAdminCanReviewGlobalSkill() {
        ReviewTask task = new ReviewTask(1L, 1L, "user-2");
        assertTrue(checker.canReview(task, "user-1",
                NamespaceType.GLOBAL,
                Map.of(), Set.of("SKILL_ADMIN")));
    }

    @Test
    void superAdminCanReviewGlobalSkill() {
        ReviewTask task = new ReviewTask(1L, 1L, "user-2");
        assertTrue(checker.canReview(task, "user-1",
                NamespaceType.GLOBAL,
                Map.of(), Set.of("SUPER_ADMIN")));
    }

    @Test
    void skillAdminCannotReviewTeamSkill() {
        ReviewTask task = new ReviewTask(1L, 10L, "user-2");
        assertTrue(checker.canReview(task, "user-1",
                NamespaceType.TEAM,
                Map.of(), Set.of("SKILL_ADMIN")));
    }

    @Test
    void superAdminCanReviewTeamSkill() {
        ReviewTask task = new ReviewTask(1L, 10L, "user-2");
        assertTrue(checker.canReview(task, "user-1",
                NamespaceType.TEAM,
                Map.of(), Set.of("SUPER_ADMIN")));
    }

    @Test
    void nonAdminCannotReviewGlobalSkill() {
        ReviewTask task = new ReviewTask(1L, 1L, "user-2");
        assertFalse(checker.canReview(task, "user-1",
                NamespaceType.GLOBAL,
                Map.of(), Set.of()));
    }

    // --- canReviewPromotion tests ---

    @Test
    void memberCanSubmitReview() {
        assertTrue(checker.canSubmitReview(10L, Map.of(10L, NamespaceRole.MEMBER)));
    }

    @Test
    void outsiderCannotSubmitReview() {
        assertFalse(checker.canSubmitReview(10L, Map.of()));
    }

    @Test
    void teamAdminCanManagePendingReviewList() {
        assertTrue(checker.canManageNamespaceReviews(
                10L, NamespaceType.TEAM, Map.of(10L, NamespaceRole.ADMIN), Set.of()));
    }

    @Test
    void submitterCanReadOwnReview() {
        ReviewTask task = new ReviewTask(1L, 10L, "user-1");
        assertTrue(checker.canReadReview(task, "user-1",
                NamespaceType.TEAM, Map.of(), Set.of()));
    }

    @Test
    void ownerCanSubmitPromotion() {
        Skill sourceSkill = new Skill(10L, "skill-a", "user-1", SkillVisibility.PUBLIC);
        assertTrue(checker.canSubmitPromotion(sourceSkill, "user-1", Map.of()));
    }

    @Test
    void teamAdminCanSubmitPromotionForForeignSkill() {
        Skill sourceSkill = new Skill(10L, "skill-a", "user-2", SkillVisibility.PUBLIC);
        assertTrue(checker.canSubmitPromotion(sourceSkill, "user-1",
                Map.of(10L, NamespaceRole.ADMIN)));
    }

    @Test
    void submitterCanReadOwnPromotion() {
        PromotionRequest req = new PromotionRequest(1L, 1L, 1L, "user-1");
        assertTrue(checker.canReadPromotion(req, "user-1", Set.of()));
    }

    @Test
    void skillAdminCanListPendingPromotions() {
        assertTrue(checker.canListPendingPromotions(Set.of("SKILL_ADMIN")));
    }

    @Test
    void skillAdminCanReviewPromotion() {
        PromotionRequest req = new PromotionRequest(1L, 1L, 1L, "user-2");
        assertTrue(checker.canReviewPromotion(req, "user-1",
                Set.of("SKILL_ADMIN")));
    }

    @Test
    void superAdminCanReviewPromotion() {
        PromotionRequest req = new PromotionRequest(1L, 1L, 1L, "user-2");
        assertTrue(checker.canReviewPromotion(req, "user-1",
                Set.of("SUPER_ADMIN")));
    }

    @Test
    void regularUserCannotReviewPromotion() {
        PromotionRequest req = new PromotionRequest(1L, 1L, 1L, "user-2");
        assertFalse(checker.canReviewPromotion(req, "user-1",
                Set.of()));
    }

    @Test
    void cannotReviewOwnPromotion() {
        String userId = "user-2";
        PromotionRequest req = new PromotionRequest(1L, 1L, 1L, userId);
        assertFalse(checker.canReviewPromotion(req, userId,
                Set.of("SKILL_ADMIN")));
    }
}
