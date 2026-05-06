package com.iflytek.skillhub.domain.label;

import com.iflytek.skillhub.domain.namespace.NamespaceRole;
import com.iflytek.skillhub.domain.skill.Skill;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class LabelPermissionCheckerTest {

    private final LabelPermissionChecker checker = new LabelPermissionChecker();

    @Test
    void canManageDefinitions_returnsTrueForSuperAdmin() {
        assertThat(checker.canManageDefinitions(Set.of("SUPER_ADMIN"))).isTrue();
    }

    @Test
    void canManageDefinitions_returnsFalseForNonSuperAdmin() {
        assertThat(checker.canManageDefinitions(Set.of("ADMIN"))).isFalse();
    }

    @Test
    void canManageSkillLabel_superAdminAlwaysTrue() {
        Skill skill = new Skill(1L, "slug", "owner", null);
        LabelDefinition label = new LabelDefinition("lbl", LabelType.RECOMMENDED, true, 0, "creator");

        assertThat(checker.canManageSkillLabel(skill, label, "user-1", Map.of(), Set.of("SUPER_ADMIN"))).isTrue();
    }

    @Test
    void canManageSkillLabel_nullUserIdReturnsFalse() {
        Skill skill = new Skill(1L, "slug", "owner", null);
        LabelDefinition label = new LabelDefinition("lbl", LabelType.RECOMMENDED, true, 0, "creator");

        assertThat(checker.canManageSkillLabel(skill, label, null, Map.of(), Set.of())).isFalse();
    }

    @Test
    void canManageSkillLabel_privilegedLabelReturnsFalse() {
        Skill skill = new Skill(1L, "slug", "owner", null);
        LabelDefinition label = new LabelDefinition("lbl", LabelType.PRIVILEGED, true, 0, "creator");

        assertThat(checker.canManageSkillLabel(skill, label, "owner", Map.of(), Set.of())).isFalse();
    }

    @Test
    void canManageSkillLabel_ownerReturnsTrue() {
        Skill skill = new Skill(1L, "slug", "owner", null);
        LabelDefinition label = new LabelDefinition("lbl", LabelType.RECOMMENDED, true, 0, "creator");

        assertThat(checker.canManageSkillLabel(skill, label, "owner", Map.of(), Set.of())).isTrue();
    }

    @Test
    void canManageSkillLabel_namespaceAdminReturnsTrue() {
        Skill skill = new Skill(1L, "slug", "other", null);
        LabelDefinition label = new LabelDefinition("lbl", LabelType.RECOMMENDED, true, 0, "creator");

        assertThat(checker.canManageSkillLabel(skill, label, "user-1",
                Map.of(1L, NamespaceRole.ADMIN), Set.of())).isTrue();
    }

    @Test
    void canManageSkillLabel_namespaceOwnerReturnsTrue() {
        Skill skill = new Skill(1L, "slug", "other", null);
        LabelDefinition label = new LabelDefinition("lbl", LabelType.RECOMMENDED, true, 0, "creator");

        assertThat(checker.canManageSkillLabel(skill, label, "user-1",
                Map.of(1L, NamespaceRole.OWNER), Set.of())).isTrue();
    }

    @Test
    void canManageSkillLabel_memberReturnsFalse() {
        Skill skill = new Skill(1L, "slug", "other", null);
        LabelDefinition label = new LabelDefinition("lbl", LabelType.RECOMMENDED, true, 0, "creator");

        assertThat(checker.canManageSkillLabel(skill, label, "user-1",
                Map.of(1L, NamespaceRole.MEMBER), Set.of())).isFalse();
    }

    @Test
    void canManageSkillLabel_noNamespaceRoleReturnsFalse() {
        Skill skill = new Skill(1L, "slug", "other", null);
        LabelDefinition label = new LabelDefinition("lbl", LabelType.RECOMMENDED, true, 0, "creator");

        assertThat(checker.canManageSkillLabel(skill, label, "user-1",
                Map.of(2L, NamespaceRole.ADMIN), Set.of())).isFalse();
    }
}
