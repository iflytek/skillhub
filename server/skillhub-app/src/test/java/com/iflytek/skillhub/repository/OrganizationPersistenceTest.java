package com.iflytek.skillhub.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.iflytek.skillhub.domain.organization.MembershipSourceType;
import com.iflytek.skillhub.domain.organization.Organization;
import com.iflytek.skillhub.domain.organization.OrganizationAccessGuard;
import com.iflytek.skillhub.domain.organization.OrganizationAuthorizationPolicy;
import com.iflytek.skillhub.domain.organization.OrganizationAuthorizationService;
import com.iflytek.skillhub.domain.organization.OrganizationDomain;
import com.iflytek.skillhub.domain.organization.OrganizationDomainRepository;
import com.iflytek.skillhub.domain.organization.OrganizationDomainStatus;
import com.iflytek.skillhub.domain.organization.OrganizationDomainVerificationMethod;
import com.iflytek.skillhub.domain.organization.OrganizationMembership;
import com.iflytek.skillhub.domain.organization.OrganizationMembershipRepository;
import com.iflytek.skillhub.domain.organization.OrganizationMembershipStatus;
import com.iflytek.skillhub.domain.organization.OrganizationRepository;
import com.iflytek.skillhub.domain.organization.OrganizationRole;
import com.iflytek.skillhub.domain.organization.OrganizationRoleBinding;
import com.iflytek.skillhub.domain.organization.OrganizationRoleBindingRepository;
import com.iflytek.skillhub.domain.organization.OrganizationRoleBindingService;
import com.iflytek.skillhub.domain.organization.OrganizationRoleBindingStatus;
import com.iflytek.skillhub.domain.shared.exception.DomainConflictException;
import com.iflytek.skillhub.domain.user.UserAccount;
import com.iflytek.skillhub.infra.jpa.JpaOrganizationMembershipRepositoryAdapter;
import com.iflytek.skillhub.infra.jpa.JpaOrganizationDomainRepositoryAdapter;
import com.iflytek.skillhub.infra.jpa.JpaOrganizationRepositoryAdapter;
import com.iflytek.skillhub.infra.jpa.JpaOrganizationRoleBindingRepositoryAdapter;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@DataJpaTest(properties = {
        "spring.flyway.enabled=true",
        "spring.jpa.hibernate.ddl-auto=validate"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Testcontainers
@Import({
        JpaOrganizationRepositoryAdapter.class,
        JpaOrganizationMembershipRepositoryAdapter.class,
        JpaOrganizationDomainRepositoryAdapter.class,
        JpaOrganizationRoleBindingRepositoryAdapter.class,
        OrganizationAccessGuard.class,
        OrganizationAuthorizationPolicy.class,
        OrganizationAuthorizationService.class,
        OrganizationRoleBindingService.class
})
class OrganizationPersistenceTest {

    private static final Instant CREATED_AT = Instant.parse("2026-09-08T00:00:00Z");

    @Container
    private static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void configurePostgres(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add(
                "spring.jpa.database-platform",
                () -> "org.hibernate.dialect.PostgreSQLDialect"
        );
    }

    @Autowired
    private OrganizationRepository organizationRepository;

    @Autowired
    private OrganizationMembershipRepository membershipRepository;

    @Autowired
    private OrganizationDomainRepository domainRepository;

    @Autowired
    private OrganizationRoleBindingRepository roleBindingRepository;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private OrganizationRoleBindingService roleBindingService;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    void duplicateOrganizationSlugUsesAStableConflictInsteadOfLeakingTheConstraint() {
        UserAccount owner = persistUser("duplicate-slug-owner");
        persistOrganization("duplicate-slug", "First", owner.getId());

        assertThatThrownBy(() -> organizationRepository.save(Organization.create(
                "duplicate-slug",
                "Second",
                owner.getId(),
                CREATED_AT.plusSeconds(1)
        )))
                .isInstanceOf(DomainConflictException.class)
                .hasMessage("error.organization.slug.conflict");
    }

    @Test
    void membershipReadsAlwaysRequireTheOwningOrganization() {
        UserAccount user = persistUser("organization-scope-user");
        Organization organizationA = persistOrganization("scope-a", "Scope A", user.getId());
        Organization organizationB = persistOrganization("scope-b", "Scope B", user.getId());
        OrganizationMembership membershipA = activeMembership(
                organizationA.getId(),
                user.getId(),
                "directory-user-a"
        );
        OrganizationMembership membershipB = activeMembership(
                organizationB.getId(),
                user.getId(),
                "directory-user-b"
        );
        membershipRepository.save(membershipA);
        membershipRepository.save(membershipB);
        entityManager.flush();
        entityManager.clear();

        assertThat(membershipRepository.findByOrganizationIdAndId(
                organizationA.getId(), membershipA.getId())).isPresent();
        assertThat(membershipRepository.findByOrganizationIdAndId(
                organizationB.getId(), membershipA.getId())).isEmpty();
        assertThat(membershipRepository.findCurrentByOrganizationIdAndUserId(
                organizationA.getId(), user.getId())).get()
                .extracting(OrganizationMembership::getId)
                .isEqualTo(membershipA.getId());
        assertThat(membershipRepository.findCurrentByOrganizationIdAndUserId(
                organizationB.getId(), user.getId())).get()
                .extracting(OrganizationMembership::getId)
                .isEqualTo(membershipB.getId());
    }

    @Test
    void domainReadsAlwaysRequireTheOwningOrganization() {
        UserAccount owner = persistUser("domain-scope-owner");
        Organization organizationA = persistOrganization(
                "domain-scope-a",
                "Domain Scope A",
                owner.getId()
        );
        Organization organizationB = persistOrganization(
                "domain-scope-b",
                "Domain Scope B",
                owner.getId()
        );
        OrganizationDomain claim = domainRepository.save(pendingDomain(
                organizationA.getId(),
                "scope.example.com",
                "digest-a"
        ));
        entityManager.clear();

        assertThat(domainRepository.findByOrganizationIdAndId(
                organizationA.getId(), claim.getId())).isPresent();
        assertThat(domainRepository.findByOrganizationIdAndId(
                organizationB.getId(), claim.getId())).isEmpty();
        assertThat(domainRepository.findByOrganizationIdAndDomain(
                organizationA.getId(), "scope.example.com")).isPresent();
        assertThat(domainRepository.findByOrganizationIdAndDomain(
                organizationB.getId(), "scope.example.com")).isEmpty();
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void pendingClaimsMayCoexistButDatabaseAllowsOnlyOneVerifiedOwner() {
        TransactionTemplate transactions = new TransactionTemplate(transactionManager);
        DomainOwnershipScenario scenario = transactions.execute(status ->
                createDomainOwnershipScenario());
        assertThat(scenario).isNotNull();

        transactions.executeWithoutResult(status -> {
            OrganizationDomain first = domainRepository.findByOrganizationIdAndId(
                    scenario.firstOrganizationId(),
                    scenario.firstClaimId()
            ).orElseThrow();
            first.verify(CREATED_AT.plusSeconds(60));
            domainRepository.save(first);
        });

        assertThatThrownBy(() -> transactions.executeWithoutResult(status -> {
            OrganizationDomain second = domainRepository.findByOrganizationIdAndId(
                    scenario.secondOrganizationId(),
                    scenario.secondClaimId()
            ).orElseThrow();
            second.verify(CREATED_AT.plusSeconds(60));
            domainRepository.save(second);
        }))
                .isInstanceOf(DomainConflictException.class)
                .hasMessage("error.organization.domain.ownership-conflict");

        OrganizationDomainStatus losingStatus = transactions.execute(status ->
                domainRepository.findByOrganizationIdAndId(
                        scenario.secondOrganizationId(),
                        scenario.secondClaimId()
                ).orElseThrow().getStatus());
        String verifiedOwner = transactions.execute(status ->
                domainRepository.findVerifiedByDomain("shared.example.com")
                        .orElseThrow()
                        .getOrganizationId());
        assertThat(losingStatus).isEqualTo(OrganizationDomainStatus.PENDING);
        assertThat(verifiedOwner).isEqualTo(scenario.firstOrganizationId());
    }

    @Test
    void duplicateClaimInsideOneOrganizationHasAStableConflict() {
        UserAccount owner = persistUser("duplicate-domain-owner");
        Organization organization = persistOrganization(
                "duplicate-domain",
                "Duplicate Domain",
                owner.getId()
        );
        domainRepository.save(pendingDomain(
                organization.getId(),
                "duplicate.example.com",
                "digest-a"
        ));

        assertThatThrownBy(() -> domainRepository.save(pendingDomain(
                organization.getId(),
                "duplicate.example.com",
                "digest-b"
        )))
                .isInstanceOf(DomainConflictException.class)
                .hasMessage("error.organization.domain.claim-conflict");
    }

    @Test
    void verifiedOwnershipLookupExcludesPendingAndDisabledClaims() {
        UserAccount owner = persistUser("verified-lookup-owner");
        Organization organization = persistOrganization(
                "verified-lookup",
                "Verified Lookup",
                owner.getId()
        );
        OrganizationDomain pending = domainRepository.save(pendingDomain(
                organization.getId(),
                "pending.example.com",
                "digest-a"
        ));
        OrganizationDomain disabled = pendingDomain(
                organization.getId(),
                "disabled.example.com",
                "digest-b"
        );
        disabled.disable(CREATED_AT.plusSeconds(60));
        domainRepository.save(disabled);
        OrganizationDomain verified = pendingDomain(
                organization.getId(),
                "verified.example.com",
                "digest-c"
        );
        verified.verify(CREATED_AT.plusSeconds(60));
        domainRepository.save(verified);
        entityManager.clear();

        assertThat(domainRepository.findVerifiedByDomain(pending.getDomain())).isEmpty();
        assertThat(domainRepository.findVerifiedByDomain(disabled.getDomain())).isEmpty();
        assertThat(domainRepository.findVerifiedByDomain(verified.getDomain())).get()
                .extracting(OrganizationDomain::getStatus)
                .isEqualTo(OrganizationDomainStatus.VERIFIED);
    }

    @Test
    void membershipCannotReferenceAnUnknownOrganization() {
        UserAccount user = persistUser("unknown-organization-user");
        OrganizationMembership membership = activeMembership(
                "missing-organization",
                user.getId(),
                "directory-user-missing-organization"
        );

        assertThatThrownBy(() -> {
            membershipRepository.save(membership);
            entityManager.flush();
        }).satisfies(failure -> assertThat(hasCause(
                failure,
                ConstraintViolationException.class
        )).isTrue());
    }

    @Test
    void duplicateCurrentMembershipForOneOrganizationAndUserIsRejected() {
        UserAccount user = persistUser("duplicate-membership-user");
        Organization organization = persistOrganization(
                "duplicate-membership",
                "Duplicate Membership",
                user.getId()
        );
        membershipRepository.save(activeMembership(
                organization.getId(),
                user.getId(),
                "directory-user-first"
        ));
        entityManager.flush();

        assertThatThrownBy(() -> {
            membershipRepository.save(activeMembership(
                    organization.getId(),
                    user.getId(),
                    "directory-user-second"
            ));
            entityManager.flush();
        }).satisfies(failure -> assertThat(hasCause(
                failure,
                ConstraintViolationException.class
        )).isTrue());
    }

    @Test
    void deprovisionedHistoryDoesNotBlockAReplacementMembership() {
        UserAccount user = persistUser("replacement-membership-user");
        Organization organization = persistOrganization(
                "replacement-membership",
                "Replacement Membership",
                user.getId()
        );
        OrganizationMembership history = activeMembership(
                organization.getId(),
                user.getId(),
                "directory-user-replacement"
        );
        history = membershipRepository.save(history);
        entityManager.flush();
        history.deprovision(CREATED_AT.plusSeconds(120));
        entityManager.flush();

        OrganizationMembership replacement = activeMembership(
                organization.getId(),
                user.getId(),
                "directory-user-replacement"
        );
        membershipRepository.save(replacement);
        entityManager.flush();
        entityManager.clear();

        assertThat(membershipRepository.findCurrentByOrganizationIdAndSource(
                organization.getId(),
                MembershipSourceType.JIT,
                "directory-user-replacement"
        )).get().extracting(OrganizationMembership::getId).isEqualTo(replacement.getId());
        assertThat(entityManager.find(OrganizationMembership.class, history.getId()).getStatus())
                .isEqualTo(OrganizationMembershipStatus.DEPROVISIONED);
    }

    @Test
    void roleBindingReadsAndCountsAreAlwaysOrganizationScoped() {
        UserAccount owner = persistUser("role-scope-owner");
        Organization organizationA = persistOrganization(
                "role-scope-a",
                "Role Scope A",
                owner.getId()
        );
        Organization organizationB = persistOrganization(
                "role-scope-b",
                "Role Scope B",
                owner.getId()
        );
        OrganizationRoleBinding bindingA = roleBindingRepository.save(roleBinding(
                organizationA.getId(),
                owner.getId(),
                OrganizationRole.ORG_OWNER,
                owner.getId()
        ));
        roleBindingRepository.save(roleBinding(
                organizationB.getId(),
                owner.getId(),
                OrganizationRole.ORG_OWNER,
                owner.getId()
        ));
        entityManager.flush();
        entityManager.clear();

        assertThat(roleBindingRepository.findByOrganizationIdAndId(
                organizationA.getId(), bindingA.getId())).isPresent();
        assertThat(roleBindingRepository.findByOrganizationIdAndId(
                organizationB.getId(), bindingA.getId())).isEmpty();
        assertThat(roleBindingRepository.findActiveByOrganizationIdAndUserId(
                organizationA.getId(), owner.getId())).hasSize(1);
        assertThat(roleBindingRepository.countActiveByOrganizationIdAndRole(
                organizationA.getId(), OrganizationRole.ORG_OWNER)).isEqualTo(1);
        assertThat(roleBindingRepository.countActiveByOrganizationIdAndRole(
                organizationB.getId(), OrganizationRole.ORG_OWNER)).isEqualTo(1);
    }

    @Test
    void duplicateActiveRoleIsRejected() {
        UserAccount owner = persistUser("role-history-owner");
        Organization organization = persistOrganization(
                "role-history",
                "Role History",
                owner.getId()
        );
        roleBindingRepository.save(roleBinding(
                organization.getId(),
                owner.getId(),
                OrganizationRole.IDENTITY_ADMIN,
                owner.getId()
        ));
        entityManager.flush();

        assertThatThrownBy(() -> {
            roleBindingRepository.save(roleBinding(
                    organization.getId(),
                    owner.getId(),
                    OrganizationRole.IDENTITY_ADMIN,
                    owner.getId()
            ));
            entityManager.flush();
        }).satisfies(failure -> assertThat(hasCause(
                failure,
                ConstraintViolationException.class
        )).isTrue());
    }

    @Test
    void revokedRoleHistoryAllowsAReplacement() {
        UserAccount owner = persistUser("role-replacement-owner");
        Organization organization = persistOrganization(
                "role-replacement",
                "Role Replacement",
                owner.getId()
        );
        OrganizationRoleBinding first = roleBindingRepository.save(roleBinding(
                organization.getId(),
                owner.getId(),
                OrganizationRole.IDENTITY_ADMIN,
                owner.getId()
        ));
        entityManager.flush();
        first.revoke(owner.getId(), CREATED_AT.plusSeconds(60));
        entityManager.flush();

        OrganizationRoleBinding replacement = roleBindingRepository.save(roleBinding(
                organization.getId(),
                owner.getId(),
                OrganizationRole.IDENTITY_ADMIN,
                owner.getId()
        ));
        entityManager.flush();
        entityManager.clear();

        assertThat(roleBindingRepository.findActiveByOrganizationIdAndUserIdAndRole(
                organization.getId(),
                owner.getId(),
                OrganizationRole.IDENTITY_ADMIN
        )).get().extracting(OrganizationRoleBinding::getId).isEqualTo(replacement.getId());
        assertThat(entityManager.find(OrganizationRoleBinding.class, first.getId()).getStatus())
                .isEqualTo(OrganizationRoleBindingStatus.REVOKED);
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void concurrentOwnerRevocationsCannotRemoveEveryOwner() throws Exception {
        TransactionTemplate transactions = new TransactionTemplate(transactionManager);
        ConcurrentOwnerScenario scenario = transactions.execute(status ->
                createConcurrentOwnerScenario());
        assertThat(scenario).isNotNull();

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Boolean> first = executor.submit(() -> revokeAfterBarrier(
                    ready,
                    start,
                    scenario.organizationId(),
                    scenario.secondBindingId(),
                    scenario.firstOwnerId()
            ));
            Future<Boolean> second = executor.submit(() -> revokeAfterBarrier(
                    ready,
                    start,
                    scenario.organizationId(),
                    scenario.firstBindingId(),
                    scenario.secondOwnerId()
            ));

            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            List<Boolean> outcomes = List.of(
                    first.get(15, TimeUnit.SECONDS),
                    second.get(15, TimeUnit.SECONDS)
            );

            assertThat(outcomes).containsExactlyInAnyOrder(true, false);
        } finally {
            start.countDown();
            executor.shutdownNow();
        }

        Long remainingOwners = transactions.execute(status ->
                roleBindingRepository.countActiveByOrganizationIdAndRole(
                        scenario.organizationId(),
                        OrganizationRole.ORG_OWNER
                ));
        assertThat(remainingOwners).isEqualTo(1L);
    }

    private ConcurrentOwnerScenario createConcurrentOwnerScenario() {
        UserAccount firstOwner = persistUser("concurrent-owner-a");
        UserAccount secondOwner = persistUser("concurrent-owner-b");
        Organization organization = persistOrganization(
                "concurrent-owner-org",
                "Concurrent Owner Org",
                firstOwner.getId()
        );
        membershipRepository.save(activeMembership(
                organization.getId(), firstOwner.getId(), "directory-concurrent-owner-a"));
        membershipRepository.save(activeMembership(
                organization.getId(), secondOwner.getId(), "directory-concurrent-owner-b"));
        OrganizationRoleBinding firstBinding = roleBindingRepository.save(roleBinding(
                organization.getId(),
                firstOwner.getId(),
                OrganizationRole.ORG_OWNER,
                firstOwner.getId()
        ));
        OrganizationRoleBinding secondBinding = roleBindingRepository.save(roleBinding(
                organization.getId(),
                secondOwner.getId(),
                OrganizationRole.ORG_OWNER,
                firstOwner.getId()
        ));
        entityManager.flush();
        return new ConcurrentOwnerScenario(
                organization.getId(),
                firstOwner.getId(),
                secondOwner.getId(),
                firstBinding.getId(),
                secondBinding.getId()
        );
    }

    private DomainOwnershipScenario createDomainOwnershipScenario() {
        UserAccount owner = persistUser("verified-domain-owner");
        Organization organizationA = persistOrganization(
                "verified-domain-a",
                "Verified Domain A",
                owner.getId()
        );
        Organization organizationB = persistOrganization(
                "verified-domain-b",
                "Verified Domain B",
                owner.getId()
        );
        OrganizationDomain first = domainRepository.save(pendingDomain(
                organizationA.getId(),
                "shared.example.com",
                "digest-a"
        ));
        OrganizationDomain second = domainRepository.save(pendingDomain(
                organizationB.getId(),
                "shared.example.com",
                "digest-b"
        ));
        return new DomainOwnershipScenario(
                organizationA.getId(),
                organizationB.getId(),
                first.getId(),
                second.getId()
        );
    }

    private boolean revokeAfterBarrier(
            CountDownLatch ready,
            CountDownLatch start,
            String organizationId,
            String bindingId,
            String actorUserId
    ) throws InterruptedException {
        ready.countDown();
        start.await();
        try {
            roleBindingService.revoke(
                    organizationId,
                    bindingId,
                    actorUserId,
                    CREATED_AT.plusSeconds(120)
            );
            return true;
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private UserAccount persistUser(String userId) {
        UserAccount user = new UserAccount(userId, userId, null, null);
        entityManager.persist(user);
        entityManager.flush();
        return user;
    }

    private Organization persistOrganization(String slug, String name, String createdBy) {
        Organization organization = Organization.create(slug, name, createdBy, CREATED_AT);
        organizationRepository.save(organization);
        entityManager.flush();
        return organization;
    }

    private OrganizationMembership activeMembership(
            String organizationId,
            String userId,
            String sourceId
    ) {
        OrganizationMembership membership = OrganizationMembership.provisioned(
                organizationId,
                MembershipSourceType.JIT,
                sourceId,
                userId,
                userId + "@example.com",
                CREATED_AT
        );
        membership.activate(userId, CREATED_AT.plusSeconds(60));
        return membership;
    }

    private OrganizationRoleBinding roleBinding(
            String organizationId,
            String userId,
            OrganizationRole role,
            String createdBy
    ) {
        return OrganizationRoleBinding.grant(
                organizationId,
                userId,
                role,
                createdBy,
                CREATED_AT
        );
    }

    private OrganizationDomain pendingDomain(
            String organizationId,
            String domain,
            String digest
    ) {
        return OrganizationDomain.claim(
                organizationId,
                domain,
                OrganizationDomainVerificationMethod.DNS_TXT,
                digest,
                CREATED_AT
        );
    }

    private boolean hasCause(Throwable error, Class<? extends Throwable> expectedType) {
        Throwable current = error;
        while (current != null) {
            if (expectedType.isInstance(current)) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private record ConcurrentOwnerScenario(
            String organizationId,
            String firstOwnerId,
            String secondOwnerId,
            String firstBindingId,
            String secondBindingId
    ) {
    }

    private record DomainOwnershipScenario(
            String firstOrganizationId,
            String secondOrganizationId,
            String firstClaimId,
            String secondClaimId
    ) {
    }
}
