package com.iflytek.skillhub.domain.review;

import com.iflytek.skillhub.domain.namespace.NamespaceRole;
import com.iflytek.skillhub.domain.namespace.NamespaceType;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class ReviewPermissionCheckerTest {

    private final ReviewPermissionChecker checker = new ReviewPermissionChecker();

    // --- canReview tests ---

    @Test
    void cannotReviewOwnSubmission() {
        String userId = "user-1";
        ReviewTask task = new ReviewTask(1L, 10L, userId);
        assertFalse(checker.canReview(task, userId,
                NamespaceType.TEAM, Map.of(), Set.of()));
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
