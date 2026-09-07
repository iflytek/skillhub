package com.iflytek.skillhub.domain.organization;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.iflytek.skillhub.domain.shared.exception.DomainForbiddenException;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class OrganizationAuthorizationPolicyTest {

    private final OrganizationAuthorizationPolicy policy = new OrganizationAuthorizationPolicy();

    @Test
    void everyAdministrativeActionUsesTheExplicitLeastPrivilegeMatrix() {
        Map<OrganizationAdministrativeAction, Set<OrganizationRole>> expected = Map.ofEntries(
                Map.entry(
                        OrganizationAdministrativeAction.VIEW_ORGANIZATION,
                        Set.of(OrganizationRole.values())
                ),
                Map.entry(
                        OrganizationAdministrativeAction.VIEW_ORGANIZATION_ROLES,
                        Set.of(OrganizationRole.ORG_OWNER, OrganizationRole.ORG_AUDITOR)
                ),
                Map.entry(
                        OrganizationAdministrativeAction.VIEW_DOMAINS,
                        Set.of(
                                OrganizationRole.ORG_OWNER,
                                OrganizationRole.IDENTITY_ADMIN,
                                OrganizationRole.ORG_AUDITOR
                        )
                ),
                Map.entry(
                        OrganizationAdministrativeAction.VIEW_MEMBERS,
                        Set.of(
                                OrganizationRole.ORG_OWNER,
                                OrganizationRole.DIRECTORY_ADMIN,
                                OrganizationRole.ORG_AUDITOR
                        )
                ),
                Map.entry(
                        OrganizationAdministrativeAction.MANAGE_ORGANIZATION_LIFECYCLE,
                        Set.of(OrganizationRole.ORG_OWNER)
                ),
                Map.entry(
                        OrganizationAdministrativeAction.MANAGE_ORGANIZATION_ROLES,
                        Set.of(OrganizationRole.ORG_OWNER)
                ),
                Map.entry(
                        OrganizationAdministrativeAction.MANAGE_DOMAINS,
                        Set.of(OrganizationRole.IDENTITY_ADMIN)
                ),
                Map.entry(
                        OrganizationAdministrativeAction.MANAGE_LOGIN_CONNECTIONS,
                        Set.of(OrganizationRole.IDENTITY_ADMIN)
                ),
                Map.entry(
                        OrganizationAdministrativeAction.ROTATE_LOGIN_SECRETS,
                        Set.of(OrganizationRole.IDENTITY_ADMIN)
                ),
                Map.entry(
                        OrganizationAdministrativeAction.RESOLVE_IDENTITY_CONFLICTS,
                        Set.of(OrganizationRole.IDENTITY_ADMIN)
                ),
                Map.entry(
                        OrganizationAdministrativeAction.MANAGE_DIRECTORY_CONNECTIONS,
                        Set.of(OrganizationRole.DIRECTORY_ADMIN)
                ),
                Map.entry(
                        OrganizationAdministrativeAction.ROTATE_DIRECTORY_CREDENTIALS,
                        Set.of(OrganizationRole.DIRECTORY_ADMIN)
                ),
                Map.entry(
                        OrganizationAdministrativeAction.MANAGE_MEMBERS,
                        Set.of(OrganizationRole.DIRECTORY_ADMIN)
                ),
                Map.entry(
                        OrganizationAdministrativeAction.RUN_DIRECTORY_SYNCHRONIZATION,
                        Set.of(OrganizationRole.DIRECTORY_ADMIN)
                ),
                Map.entry(
                        OrganizationAdministrativeAction.MANAGE_ENTITLEMENT_MAPPINGS,
                        Set.of(OrganizationRole.ENTITLEMENT_ADMIN)
                ),
                Map.entry(
                        OrganizationAdministrativeAction.VIEW_MAPPING_IMPACT,
                        Set.of(OrganizationRole.ENTITLEMENT_ADMIN)
                ),
                Map.entry(
                        OrganizationAdministrativeAction.VIEW_AUDIT,
                        Set.of(OrganizationRole.ORG_OWNER, OrganizationRole.ORG_AUDITOR)
                ),
                Map.entry(
                        OrganizationAdministrativeAction.VIEW_HEALTH,
                        Set.of(OrganizationRole.values())
                )
        );

        assertThat(expected).hasSize(OrganizationAdministrativeAction.values().length);
        for (OrganizationAdministrativeAction action : OrganizationAdministrativeAction.values()) {
            for (OrganizationRole role : OrganizationRole.values()) {
                assertThat(policy.isAllowed(Set.of(role), action))
                        .as("%s for %s", action, role)
                        .isEqualTo(expected.get(action).contains(role));
            }
        }
    }

    @Test
    void combinedRolesReceiveOnlyTheUnionOfTheirExplicitActions() {
        Set<OrganizationRole> roles = Set.of(
                OrganizationRole.IDENTITY_ADMIN,
                OrganizationRole.ENTITLEMENT_ADMIN
        );

        assertThat(policy.isAllowed(
                roles,
                OrganizationAdministrativeAction.MANAGE_LOGIN_CONNECTIONS
        )).isTrue();
        assertThat(policy.isAllowed(
                roles,
                OrganizationAdministrativeAction.MANAGE_ENTITLEMENT_MAPPINGS
        )).isTrue();
        assertThat(policy.isAllowed(
                roles,
                OrganizationAdministrativeAction.MANAGE_MEMBERS
        )).isFalse();
        assertThat(policy.isAllowed(
                roles,
                OrganizationAdministrativeAction.MANAGE_ORGANIZATION_ROLES
        )).isFalse();
    }

    @Test
    void missingOrganizationRoleFailsClosed() {
        assertThat(policy.isAllowed(
                Set.of(),
                OrganizationAdministrativeAction.VIEW_ORGANIZATION
        )).isFalse();
        assertThatThrownBy(() -> policy.requireAllowed(
                Set.of(),
                OrganizationAdministrativeAction.VIEW_ORGANIZATION
        ))
                .isInstanceOf(DomainForbiddenException.class)
                .hasMessage("error.organization.permission.denied");
    }

    @Test
    void organizationRolesDoNotReusePlatformOrNamespaceRoleNames() {
        Set<String> organizationRoleNames = Set.of(OrganizationRole.values()).stream()
                .map(Enum::name)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());

        assertThat(organizationRoleNames).doesNotContain(
                "SUPER_ADMIN",
                "SKILL_ADMIN",
                "USER_ADMIN",
                "AUDITOR",
                "OWNER",
                "ADMIN",
                "MEMBER"
        );
    }
}
