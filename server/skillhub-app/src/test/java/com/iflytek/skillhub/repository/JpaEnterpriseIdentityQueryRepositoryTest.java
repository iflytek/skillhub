package com.iflytek.skillhub.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.iflytek.skillhub.domain.organization.MembershipSourceType;
import com.iflytek.skillhub.domain.organization.Organization;
import com.iflytek.skillhub.domain.organization.OrganizationDomain;
import com.iflytek.skillhub.domain.organization.OrganizationDomainVerificationMethod;
import com.iflytek.skillhub.domain.organization.OrganizationMembership;
import com.iflytek.skillhub.domain.organization.OrganizationRole;
import com.iflytek.skillhub.domain.organization.OrganizationRoleBinding;
import com.iflytek.skillhub.domain.user.UserAccount;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;

@DataJpaTest(properties = {
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@Import(JpaEnterpriseIdentityQueryRepository.class)
class JpaEnterpriseIdentityQueryRepositoryTest {

    private static final Instant NOW = Instant.parse("2026-09-08T10:00:00Z");

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private EnterpriseIdentityQueryRepository repository;

    @Test
    void ownOrganizationListRequiresAnActiveMembershipAndLoadsOnlyCallerRoles() {
        UserAccount caller = persistUser("caller");
        UserAccount other = persistUser("other");
        Organization visible = persistOrganization("visible", caller.getId(), NOW);
        Organization suspendedMembership = persistOrganization(
                "suspended-membership",
                caller.getId(),
                NOW.minusSeconds(10)
        );
        Organization otherOrganization = persistOrganization(
                "other-organization",
                other.getId(),
                NOW.minusSeconds(20)
        );
        persistMembership(visible, caller, false);
        persistMembership(suspendedMembership, caller, true);
        persistMembership(otherOrganization, other, false);
        entityManager.persist(OrganizationRoleBinding.grant(
                visible.getId(),
                caller.getId(),
                OrganizationRole.IDENTITY_ADMIN,
                caller.getId(),
                NOW
        ));
        entityManager.persist(OrganizationRoleBinding.grant(
                otherOrganization.getId(),
                other.getId(),
                OrganizationRole.ORG_OWNER,
                other.getId(),
                NOW
        ));
        entityManager.flush();
        entityManager.clear();

        var organizations = repository.findActiveOrganizationsByUserId(
                caller.getId(),
                PageRequest.of(0, 20)
        );
        var roles = repository.findActiveRoles(
                organizations.getContent().stream().map(Organization::getId).toList(),
                caller.getId()
        );

        assertThat(organizations.getContent())
                .extracting(Organization::getSlug)
                .containsExactly("visible");
        assertThat(roles).containsOnlyKeys(visible.getId());
        assertThat(roles.get(visible.getId()))
                .containsExactly(OrganizationRole.IDENTITY_ADMIN);
    }

    @Test
    void tenantScopedManagementProjectionsDoNotMixDomainsMembersOrRoles() {
        UserAccount first = persistUser("first-user");
        UserAccount second = persistUser("second-user");
        Organization firstOrganization = persistOrganization("first-org", first.getId(), NOW);
        Organization secondOrganization = persistOrganization(
                "second-org",
                second.getId(),
                NOW.minusSeconds(10)
        );
        OrganizationMembership firstMembership = persistMembership(
                firstOrganization,
                first,
                false
        );
        persistMembership(secondOrganization, second, false);
        OrganizationDomain firstDomain = OrganizationDomain.claim(
                firstOrganization.getId(),
                "first.example.com",
                OrganizationDomainVerificationMethod.DNS_TXT,
                "digest-a",
                NOW
        );
        entityManager.persist(firstDomain);
        entityManager.persist(OrganizationDomain.claim(
                secondOrganization.getId(),
                "second.example.com",
                OrganizationDomainVerificationMethod.DNS_TXT,
                "digest-b",
                NOW
        ));
        OrganizationRoleBinding firstBinding = OrganizationRoleBinding.grant(
                firstOrganization.getId(),
                first.getId(),
                OrganizationRole.ORG_OWNER,
                first.getId(),
                NOW
        );
        entityManager.persist(firstBinding);
        entityManager.persist(OrganizationRoleBinding.grant(
                secondOrganization.getId(),
                second.getId(),
                OrganizationRole.ORG_OWNER,
                second.getId(),
                NOW
        ));
        entityManager.flush();
        entityManager.clear();

        assertThat(repository.findDomains(
                firstOrganization.getId(),
                PageRequest.of(0, 20)
        ).getContent()).extracting(OrganizationDomain::getId)
                .containsExactly(firstDomain.getId());
        assertThat(repository.findMemberships(
                firstOrganization.getId(),
                PageRequest.of(0, 20)
        ).getContent()).singleElement().satisfies(view -> {
            assertThat(view.membership().getId()).isEqualTo(firstMembership.getId());
            assertThat(view.accountDisplayName()).isEqualTo("first-user");
            assertThat(view.accountEmail()).isEqualTo("first-user@example.com");
        });
        assertThat(repository.findRoleBindings(
                firstOrganization.getId(),
                PageRequest.of(0, 20)
        ).getContent()).singleElement().satisfies(view -> {
            assertThat(view.binding().getId()).isEqualTo(firstBinding.getId());
            assertThat(view.accountDisplayName()).isEqualTo("first-user");
        });
    }

    private UserAccount persistUser(String id) {
        UserAccount account = new UserAccount(
                id,
                id,
                id + "@example.com",
                null
        );
        entityManager.persist(account);
        return account;
    }

    private Organization persistOrganization(String slug, String createdBy, Instant createdAt) {
        Organization organization = Organization.create(slug, slug, createdBy, createdAt);
        entityManager.persist(organization);
        return organization;
    }

    private OrganizationMembership persistMembership(
            Organization organization,
            UserAccount user,
            boolean suspend
    ) {
        OrganizationMembership membership = OrganizationMembership.provisioned(
                organization.getId(),
                MembershipSourceType.MANUAL,
                user.getId(),
                user.getDisplayName(),
                user.getEmail(),
                NOW
        );
        membership.activate(user.getId(), NOW);
        if (suspend) {
            membership.suspend(NOW.plusSeconds(1));
        }
        entityManager.persist(membership);
        return membership;
    }
}
