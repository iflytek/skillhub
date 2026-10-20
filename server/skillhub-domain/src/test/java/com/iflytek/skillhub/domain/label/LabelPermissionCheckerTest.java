package com.iflytek.skillhub.domain.label;

import com.iflytek.skillhub.domain.namespace.NamespaceRole;
import com.iflytek.skillhub.domain.suite.SkillSuite;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class LabelPermissionCheckerTest {

    private final LabelPermissionChecker checker = new LabelPermissionChecker(org.mockito.Mockito.mock(com.iflytek.skillhub.domain.skill.SkillOwnerAccessPolicy.class));
    private final SkillSuite suite = new SkillSuite(1L, "starter", "Starter", "author");

    @Test
    void suiteCreatorCanUseRecommendedLabelWhileStillNamespaceMember() {
        assertThat(checker.canManageSuiteLabel(
                suite, label(LabelType.RECOMMENDED), "author",
                Map.of(1L, NamespaceRole.MEMBER), Set.of())).isTrue();
    }

    @Test
    void unrelatedNamespaceMemberCannotManageSuiteLabels() {
        assertThat(checker.canManageSuiteLabel(
                suite, label(LabelType.RECOMMENDED), "other",
                Map.of(1L, NamespaceRole.MEMBER), Set.of())).isFalse();
    }

    @Test
    void namespaceAdminCanUseRecommendedButNotPrivilegedLabel() {
        assertThat(checker.canManageSuiteLabel(
                suite, label(LabelType.RECOMMENDED), "admin",
                Map.of(1L, NamespaceRole.ADMIN), Set.of())).isTrue();
        assertThat(checker.canManageSuiteLabel(
                suite, label(LabelType.PRIVILEGED), "admin",
                Map.of(1L, NamespaceRole.ADMIN), Set.of())).isFalse();
    }

    @Test
    void superAdminCanUsePrivilegedLabel() {
        assertThat(checker.canManageSuiteLabel(
                suite, label(LabelType.PRIVILEGED), "platform-admin",
                Map.of(), Set.of("SUPER_ADMIN"))).isTrue();
    }

    @Test
    void realMembershipPolicyRevokesRemovedOwnerButPreservesAdminAndRejoinedOwner() {
        var namespaces = org.mockito.Mockito.mock(com.iflytek.skillhub.domain.namespace.NamespaceRepository.class);
        var members = org.mockito.Mockito.mock(com.iflytek.skillhub.domain.namespace.NamespaceMemberRepository.class);
        var namespace = new com.iflytek.skillhub.domain.namespace.Namespace("team", "Team", "admin");
        org.mockito.Mockito.when(namespaces.findById(1L)).thenReturn(java.util.Optional.of(namespace));
        var policy = new com.iflytek.skillhub.domain.skill.SkillOwnerAccessPolicy(namespaces, members);
        var realChecker = new LabelPermissionChecker(policy);
        var skill = new com.iflytek.skillhub.domain.skill.Skill(1L, "demo", "author",
                com.iflytek.skillhub.domain.skill.SkillVisibility.PRIVATE);
        assertThat(realChecker.canManageSkillLabel(skill, label(LabelType.RECOMMENDED), "author", Map.of(), Set.of())).isFalse();
        assertThat(realChecker.canManageSkillLabel(skill, label(LabelType.RECOMMENDED), "admin", Map.of(1L, NamespaceRole.ADMIN), Set.of())).isTrue();
        assertThat(realChecker.canManageSkillLabel(skill, label(LabelType.PRIVILEGED), "author", Map.of(), Set.of("SUPER_ADMIN"))).isTrue();
        org.mockito.Mockito.when(members.findByNamespaceIdAndUserId(1L, "author"))
                .thenReturn(java.util.Optional.of(org.mockito.Mockito.mock(com.iflytek.skillhub.domain.namespace.NamespaceMember.class)));
        assertThat(realChecker.canManageSkillLabel(skill, label(LabelType.RECOMMENDED), "author", Map.of(1L, NamespaceRole.MEMBER), Set.of())).isTrue();
    }

    private LabelDefinition label(LabelType type) {
        return new LabelDefinition("verified", type, true, 0, "creator");
    }
}
