package com.iflytek.skillhub.service.notificationemail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import com.iflytek.skillhub.auth.rbac.RbacService;
import com.iflytek.skillhub.domain.namespace.*;
import com.iflytek.skillhub.domain.review.*;
import com.iflytek.skillhub.domain.skill.SkillVisibility;
import com.iflytek.skillhub.domain.suite.*;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;

class NotificationEmailSuiteReviewEligibilityTest {
    ReviewTaskRepository reviews = mock(ReviewTaskRepository.class);
    SkillSuiteRepository suites = mock(SkillSuiteRepository.class);
    SkillSuiteVersionRepository versions = mock(SkillSuiteVersionRepository.class);
    NamespaceRepository namespaces = mock(NamespaceRepository.class);
    NamespaceMemberRepository members = mock(NamespaceMemberRepository.class);
    RbacService rbac = mock(RbacService.class);
    ReviewPermissionChecker permissions = new ReviewPermissionChecker();
    NotificationEmailSuiteReviewEligibility eligibility = new NotificationEmailSuiteReviewEligibility(
            reviews, suites, versions, namespaces, members, rbac, permissions);
    Namespace namespace;
    SkillSuite suite;
    SkillSuiteVersion version;
    ReviewTask task;

    @BeforeEach
    void setup() {
        namespace = new Namespace("team", "Team", "author");
        ReflectionTestUtils.setField(namespace, "id", 7L);
        suite = new SkillSuite(7L, "writers", "Writing suite", "author");
        ReflectionTestUtils.setField(suite, "id", 8L);
        version = new SkillSuiteVersion(8L, "1.1.0", SkillVisibility.PUBLIC, "author");
        ReflectionTestUtils.setField(version, "id", 9L);
        version.setStatus(SkillSuiteVersionStatus.PENDING_REVIEW);
        task = ReviewTask.forSuiteVersion(9L, 8L, 7L, "1.1.0", "author");
        ReflectionTestUtils.setField(task, "id", 101L);
        when(reviews.findById(101L)).thenReturn(Optional.of(task));
        when(suites.findById(8L)).thenReturn(Optional.of(suite));
        when(versions.findById(9L)).thenReturn(Optional.of(version));
        when(namespaces.findById(7L)).thenReturn(Optional.of(namespace));
        when(members.findByNamespaceIdAndUserId(7L, "reviewer"))
                .thenReturn(Optional.of(new NamespaceMember(7L, "reviewer", NamespaceRole.ADMIN)));
        when(rbac.getUserRoleCodes(anyString())).thenReturn(Set.of());
    }

    @ParameterizedTest
    @EnumSource(value = NamespaceRole.class, names = {"OWNER", "ADMIN"})
    void permitsCurrentTeamReviewersAndReflectsRevocation(NamespaceRole role) {
        when(members.findByNamespaceIdAndUserId(7L, "reviewer"))
                .thenReturn(Optional.of(new NamespaceMember(7L, "reviewer", role)));
        assertThat(eligibility.eligible("reviewer", 101L)).isTrue();
        when(members.findByNamespaceIdAndUserId(7L, "reviewer")).thenReturn(Optional.empty());
        assertThat(eligibility.eligible("reviewer", 101L)).isFalse();
    }

    @Test
    void submitterReadAccessDoesNotGrantReviewNotificationEligibility() {
        when(members.findByNamespaceIdAndUserId(7L, "author"))
                .thenReturn(Optional.of(new NamespaceMember(7L, "author", NamespaceRole.MEMBER)));
        assertThat(permissions.canReadReview(task, "author", NamespaceType.TEAM,
                Map.of(7L, NamespaceRole.MEMBER), Set.of())).isTrue();
        assertThat(eligibility.eligible("author", 101L)).isFalse();
        when(members.findByNamespaceIdAndUserId(7L, "author"))
                .thenReturn(Optional.of(new NamespaceMember(7L, "author", NamespaceRole.OWNER)));
        assertThat(eligibility.eligible("author", 101L)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"SKILL_ADMIN", "SUPER_ADMIN"})
    void globalQueueRequiresCurrentPlatformReviewRole(String role) {
        namespace.setType(NamespaceType.GLOBAL);
        assertThat(eligibility.eligible("reviewer", 101L)).isFalse();
        when(rbac.getUserRoleCodes("reviewer")).thenReturn(Set.of(role));
        assertThat(eligibility.eligible("reviewer", 101L)).isTrue();
        when(rbac.getUserRoleCodes("reviewer")).thenReturn(Set.of("USER_ADMIN"));
        assertThat(eligibility.eligible("reviewer", 101L)).isFalse();
    }

    @Test
    void globalSubmitterCannotUseSkillAdminRoleToBypassSelfReviewRule() {
        namespace.setType(NamespaceType.GLOBAL);
        when(rbac.getUserRoleCodes("author")).thenReturn(Set.of("SKILL_ADMIN"));
        assertThat(eligibility.eligible("author", 101L)).isFalse();
        when(rbac.getUserRoleCodes("author")).thenReturn(Set.of("SUPER_ADMIN"));
        assertThat(eligibility.eligible("author", 101L)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"missing-task", "wrong-subject", "missing-suite", "wrong-namespace",
            "missing-version", "wrong-suite", "wrong-version", "missing-namespace", "completed-task",
            "reopened-version", "archived-suite", "frozen-namespace", "archived-namespace"})
    void suppressesStaleOrMismatchedReviewSubjects(String state) {
        switch (state) {
            case "missing-task" -> when(reviews.findById(101L)).thenReturn(Optional.empty());
            case "wrong-subject" -> when(reviews.findById(101L))
                    .thenReturn(Optional.of(new ReviewTask(9L, 8L, 7L, "1.1.0", "author")));
            case "missing-suite" -> when(suites.findById(8L)).thenReturn(Optional.empty());
            case "wrong-namespace" -> ReflectionTestUtils.setField(suite, "namespaceId", 77L);
            case "missing-version" -> when(versions.findById(9L)).thenReturn(Optional.empty());
            case "wrong-suite" -> ReflectionTestUtils.setField(version, "suiteId", 88L);
            case "wrong-version" -> ReflectionTestUtils.setField(version, "version", "2.0.0");
            case "missing-namespace" -> when(namespaces.findById(7L)).thenReturn(Optional.empty());
            case "completed-task" -> task.setStatus(ReviewTaskStatus.APPROVED);
            case "reopened-version" -> version.setStatus(SkillSuiteVersionStatus.DRAFT);
            case "archived-suite" -> suite.setStatus(SkillSuiteStatus.ARCHIVED);
            case "frozen-namespace" -> namespace.setStatus(NamespaceStatus.FROZEN);
            case "archived-namespace" -> namespace.setStatus(NamespaceStatus.ARCHIVED);
            default -> throw new AssertionError(state);
        }
        assertThat(eligibility.eligible("reviewer", 101L)).isFalse();
    }

    @Test
    void missingTaskIdentityDoesNotQueryRepositories() {
        assertThat(eligibility.eligible("reviewer", null)).isFalse();
        verifyNoInteractions(reviews, suites, versions, namespaces, members, rbac);
    }
}
