package com.iflytek.skillhub.domain.namespace;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class NamespaceAccessPolicyTest {

    private final NamespaceAccessPolicy policy = new NamespaceAccessPolicy();

    @Test
    void globalNamespaceIsImmutable() {
        Namespace namespace = new Namespace("global", "Global", "owner");
        namespace.setType(NamespaceType.GLOBAL);

        assertThat(policy.isImmutable(namespace)).isTrue();
        assertThat(policy.canMutateSettings(namespace)).isFalse();
        assertThat(policy.canManageMembers(namespace)).isFalse();
        assertThat(policy.canTransferOwnership(namespace)).isFalse();
    }

    @Test
    void activeTeamNamespaceAllowsAdminAndOwnerToFreezeButNotMember() {
        Namespace namespace = new Namespace("team-a", "Team A", "owner");
        namespace.setType(NamespaceType.TEAM);
        namespace.setStatus(NamespaceStatus.ACTIVE);

        assertThat(policy.canFreeze(namespace, NamespaceRole.OWNER)).isTrue();
        assertThat(policy.canFreeze(namespace, NamespaceRole.ADMIN)).isTrue();
        assertThat(policy.canFreeze(namespace, NamespaceRole.MEMBER)).isFalse();
    }

    @Test
    void frozenTeamNamespaceAllowsAdminAndOwnerToUnfreezeButNotMember() {
        Namespace namespace = new Namespace("team-a", "Team A", "owner");
        namespace.setType(NamespaceType.TEAM);
        namespace.setStatus(NamespaceStatus.FROZEN);

        assertThat(policy.canUnfreeze(namespace, NamespaceRole.OWNER)).isTrue();
        assertThat(policy.canUnfreeze(namespace, NamespaceRole.ADMIN)).isTrue();
        assertThat(policy.canUnfreeze(namespace, NamespaceRole.MEMBER)).isFalse();
    }

    @Test
    void archiveAndRestoreAreOwnerOnly() {
        Namespace namespace = new Namespace("team-a", "Team A", "owner");
        namespace.setType(NamespaceType.TEAM);
        namespace.setStatus(NamespaceStatus.ACTIVE);

        assertThat(policy.canArchive(namespace, NamespaceRole.OWNER)).isTrue();
        assertThat(policy.canArchive(namespace, NamespaceRole.ADMIN)).isFalse();

        namespace.setStatus(NamespaceStatus.ARCHIVED);
        assertThat(policy.canRestore(namespace, NamespaceRole.OWNER)).isTrue();
        assertThat(policy.canRestore(namespace, NamespaceRole.ADMIN)).isFalse();
    }
}
