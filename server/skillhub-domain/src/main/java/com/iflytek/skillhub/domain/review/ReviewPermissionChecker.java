package com.iflytek.skillhub.domain.review;

import com.iflytek.skillhub.domain.namespace.NamespaceRole;
import com.iflytek.skillhub.domain.namespace.NamespaceType;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;

@Component
public class ReviewPermissionChecker {

    /**
     * Check if a user can review a ReviewTask.
     *
     * @param task               the review task
     * @param userId             the reviewer's user ID
     * @param namespaceType      the type of the namespace
     * @param userNamespaceRoles user's roles keyed by namespace ID
     * @param platformRoles      user's platform-level roles
     * @return true if the user is allowed to review
     */
    public boolean canReview(ReviewTask task,
                             String userId,
                             NamespaceType namespaceType,
                             Map<Long, NamespaceRole> userNamespaceRoles,
                             Set<String> platformRoles) {
        // Cannot review own submission
        if (task.getSubmittedBy().equals(userId)) {
            return false;
        }

        if (platformRoles.contains("SKILL_ADMIN")
                || platformRoles.contains("SUPER_ADMIN")) {
            return true;
        }

        // Global namespace: only SKILL_ADMIN or SUPER_ADMIN
        if (namespaceType == NamespaceType.GLOBAL) {
            return false;
        }

        // Team namespace: namespace ADMIN or OWNER
        NamespaceRole role = userNamespaceRoles.get(
                task.getNamespaceId());
        return role == NamespaceRole.ADMIN
                || role == NamespaceRole.OWNER;
    }

    /**
     * Check if a user can review a PromotionRequest.
     * Only SKILL_ADMIN or SUPER_ADMIN, and not own.
     */
    public boolean canReviewPromotion(
            PromotionRequest request,
            String userId,
            Set<String> platformRoles) {
        if (request.getSubmittedBy().equals(userId)) {
            return false;
        }
        return platformRoles.contains("SKILL_ADMIN")
                || platformRoles.contains("SUPER_ADMIN");
    }
}
