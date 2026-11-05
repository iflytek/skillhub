package com.iflytek.skillhub.service.notificationemail;

import com.iflytek.skillhub.auth.rbac.RbacService;
import com.iflytek.skillhub.domain.namespace.NamespaceMemberRepository;
import com.iflytek.skillhub.domain.namespace.NamespaceRepository;
import com.iflytek.skillhub.domain.namespace.NamespaceRole;
import com.iflytek.skillhub.domain.namespace.NamespaceStatus;
import com.iflytek.skillhub.domain.review.ReviewPermissionChecker;
import com.iflytek.skillhub.domain.review.ReviewSubjectType;
import com.iflytek.skillhub.domain.review.ReviewTaskRepository;
import com.iflytek.skillhub.domain.review.ReviewTaskStatus;
import com.iflytek.skillhub.domain.suite.SkillSuiteRepository;
import com.iflytek.skillhub.domain.suite.SkillSuiteStatus;
import com.iflytek.skillhub.domain.suite.SkillSuiteVersionRepository;
import com.iflytek.skillhub.domain.suite.SkillSuiteVersionStatus;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Component;

/** Resolves the bound Suite review from authoritative storage, never from notification text. */
@Component
public class NotificationEmailSuiteReviewEligibility {
    private final ReviewTaskRepository reviews;
    private final SkillSuiteRepository suites;
    private final SkillSuiteVersionRepository versions;
    private final NamespaceRepository namespaces;
    private final NamespaceMemberRepository members;
    private final RbacService rbac;
    private final ReviewPermissionChecker permissions;

    public NotificationEmailSuiteReviewEligibility(ReviewTaskRepository reviews, SkillSuiteRepository suites,
            SkillSuiteVersionRepository versions, NamespaceRepository namespaces, NamespaceMemberRepository members,
            RbacService rbac, ReviewPermissionChecker permissions) {
        this.reviews = reviews;
        this.suites = suites;
        this.versions = versions;
        this.namespaces = namespaces;
        this.members = members;
        this.rbac = rbac;
        this.permissions = permissions;
    }

    public boolean eligible(String userId, Long reviewId) {
        if (reviewId == null) {
            return false;
        }
        var task = reviews.findById(reviewId).orElse(null);
        if (task == null || task.getSubjectType() != ReviewSubjectType.SUITE_VERSION
                || task.getStatus() != ReviewTaskStatus.PENDING
                || task.getSubjectId() == null || task.getSubjectVersionId() == null) {
            return false;
        }
        var suite = suites.findById(task.getSubjectId()).orElse(null);
        if (suite == null || suite.getStatus() != SkillSuiteStatus.ACTIVE
                || !Objects.equals(suite.getNamespaceId(), task.getNamespaceId())) {
            return false;
        }
        var version = versions.findById(task.getSubjectVersionId()).orElse(null);
        if (version == null || version.getStatus() != SkillSuiteVersionStatus.PENDING_REVIEW
                || !Objects.equals(version.getSuiteId(), suite.getId())
                || !Objects.equals(version.getVersion(), task.getSubjectVersion())) {
            return false;
        }
        var namespace = namespaces.findById(task.getNamespaceId()).orElse(null);
        if (namespace == null || namespace.getStatus() != NamespaceStatus.ACTIVE) {
            return false;
        }
        Map<Long, NamespaceRole> roles = members.findByNamespaceIdAndUserId(namespace.getId(), userId)
                .map(member -> Map.of(namespace.getId(), member.getRole()))
                .orElse(Map.of());
        return permissions.canReview(task, userId, namespace.getType(), roles, rbac.getUserRoleCodes(userId));
    }
}
