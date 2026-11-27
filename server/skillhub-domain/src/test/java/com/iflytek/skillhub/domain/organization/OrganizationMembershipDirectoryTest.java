package com.iflytek.skillhub.domain.organization;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class OrganizationMembershipDirectoryTest {

    private static final Instant NOW = Instant.parse("2026-11-26T00:00:00Z");

    @Test
    void directoryProfileChangesDoNotCreatePlatformIdentity() {
        OrganizationMembership membership = OrganizationMembership.directoryProvisioned(
                "org-1", "connection-1:user-1", "alice", "Alice", "alice@example.com",
                "Engineering", "E-1", NOW);

        assertThat(membership.getSourceType()).isEqualTo(MembershipSourceType.DIRECTORY);
        assertThat(membership.getUserId()).isNull();
        assertThat(membership.getStatus()).isEqualTo(OrganizationMembershipStatus.PROVISIONED);

        membership.updateDirectoryProfile(
                "alice", "Alice Chen", "alice.chen@example.com", "Platform", "E-1", NOW.plusSeconds(1));

        assertThat(membership.getDisplayName()).isEqualTo("Alice Chen");
        assertThat(membership.getPrimaryEmail()).isEqualTo("alice.chen@example.com");
        assertThat(membership.getUserId()).isNull();
    }

    @Test
    void directoryDeprovisionCanBeRestoredWithoutChangingSource() {
        OrganizationMembership membership = OrganizationMembership.directoryProvisioned(
                "org-1", "connection-1:user-1", "alice", "Alice", "alice@example.com",
                null, null, NOW);

        membership.deprovision(NOW.plusSeconds(1));
        membership.restoreDirectory(NOW.plusSeconds(2));

        assertThat(membership.getStatus()).isEqualTo(OrganizationMembershipStatus.PROVISIONED);
        assertThat(membership.getSourceId()).isEqualTo("connection-1:user-1");
    }
}
