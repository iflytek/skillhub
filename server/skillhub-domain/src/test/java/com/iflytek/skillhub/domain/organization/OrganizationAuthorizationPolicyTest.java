package com.iflytek.skillhub.domain.organization;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import org.junit.jupiter.api.Test;

class OrganizationAuthorizationPolicyTest {

    private final OrganizationAuthorizationPolicy policy = new OrganizationAuthorizationPolicy();

    @Test
    void ownerCanManageMembersWithoutInheritingConnectionOrSecretPermissions() {
        Set<OrganizationRole> owner = Set.of(OrganizationRole.ORG_OWNER);

        assertThat(policy.isAllowed(owner, OrganizationAdministrativeAction.MANAGE_MEMBERS)).isTrue();
        assertThat(policy.isAllowed(owner, OrganizationAdministrativeAction.MANAGE_ORGANIZATION_ROLES)).isTrue();
        assertThat(policy.isAllowed(owner, OrganizationAdministrativeAction.MANAGE_LOGIN_CONNECTIONS)).isFalse();
        assertThat(policy.isAllowed(owner, OrganizationAdministrativeAction.ROTATE_LOGIN_SECRETS)).isFalse();
    }

    @Test
    void memberAdminCannotDelegateRoles() {
        Set<OrganizationRole> memberAdmin = Set.of(OrganizationRole.MEMBER_ADMIN);

        assertThat(policy.isAllowed(memberAdmin, OrganizationAdministrativeAction.MANAGE_MEMBERS)).isTrue();
        assertThat(policy.isAllowed(memberAdmin, OrganizationAdministrativeAction.MANAGE_ORGANIZATION_ROLES)).isFalse();
    }
}
