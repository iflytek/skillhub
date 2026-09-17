package com.iflytek.skillhub.domain.label;

import com.iflytek.skillhub.domain.namespace.NamespaceRole;
import com.iflytek.skillhub.domain.suite.SkillSuite;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class LabelPermissionCheckerTest {

    private final LabelPermissionChecker checker = new LabelPermissionChecker();
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

    private LabelDefinition label(LabelType type) {
        return new LabelDefinition("verified", type, true, 0, "creator");
    }
}
