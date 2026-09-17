package com.iflytek.skillhub.auth.federation.association;

import com.iflytek.skillhub.auth.federation.core.ExternalIdentityCoordinate;
import com.iflytek.skillhub.auth.federation.core.ExternalIdentityLoginModule;
import com.iflytek.skillhub.auth.federation.core.EnterpriseIdentitySecurityEventRecorder;
import com.iflytek.skillhub.auth.federation.core.EnterpriseIdentitySecurityEventType;
import com.iflytek.skillhub.auth.federation.core.IdentityAssertion;
import com.iflytek.skillhub.auth.federation.core.IdentityCoreActivation;
import com.iflytek.skillhub.auth.federation.core.IdentityCorrelationCandidates;
import com.iflytek.skillhub.auth.federation.core.IdentityCorrelationPolicy;
import com.iflytek.skillhub.auth.federation.core.IdentityCorrelationPolicySettings;
import com.iflytek.skillhub.auth.federation.core.IdentityCorrelationStage;
import com.iflytek.skillhub.auth.federation.core.IdentityCorrelationTarget;
import com.iflytek.skillhub.auth.federation.core.IdentityLoginResolution;
import com.iflytek.skillhub.auth.federation.core.VerifiedEmail;
import com.iflytek.skillhub.domain.event.UserActivatedEvent;
import com.iflytek.skillhub.domain.namespace.GlobalNamespaceMembershipService;
import com.iflytek.skillhub.domain.organization.MembershipSourceType;
import com.iflytek.skillhub.domain.organization.OrganizationDomain;
import com.iflytek.skillhub.domain.organization.OrganizationDomainRepository;
import com.iflytek.skillhub.domain.organization.OrganizationMembership;
import com.iflytek.skillhub.domain.organization.OrganizationMembershipRepository;
import com.iflytek.skillhub.domain.organization.OrganizationMembershipStatus;
import com.iflytek.skillhub.domain.organization.OrganizationRepository;
import com.iflytek.skillhub.domain.organization.OrganizationStatus;
import com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException;
import com.iflytek.skillhub.domain.user.UserAccount;
import com.iflytek.skillhub.domain.user.UserAccountRepository;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionOperations;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Correlates one already-verified enterprise assertion and commits account, membership and binding
 * as one retryable transaction. Protocol/network I/O must complete before entering this service.
 */
public class EnterpriseIdentityAssociationService {

    private static final int MAX_ASSOCIATION_ATTEMPTS = 3;

    private final IdentityCoreActivation activation;
    private final ExternalIdentityRepository externalIdentities;
    private final PreProvisionedLoginSubjectRepository preProvisionedSubjects;
    private final OrganizationDomainRepository domains;
    private final OrganizationMembershipRepository memberships;
    private final OrganizationRepository organizations;
    private final UserAccountRepository accounts;
    private final GlobalNamespaceMembershipService globalMemberships;
    private final ApplicationEventPublisher events;
    private final TransactionOperations transactions;
    private final Clock clock;
    private final EnterpriseIdentitySecurityEventRecorder securityEvents;

    public EnterpriseIdentityAssociationService(
            IdentityCoreActivation activation,
            ExternalIdentityRepository externalIdentities,
            PreProvisionedLoginSubjectRepository preProvisionedSubjects,
            OrganizationDomainRepository domains,
            OrganizationMembershipRepository memberships,
            OrganizationRepository organizations,
            UserAccountRepository accounts,
            GlobalNamespaceMembershipService globalMemberships,
            ApplicationEventPublisher events,
            PlatformTransactionManager transactionManager,
            Clock clock,
            EnterpriseIdentitySecurityEventRecorder securityEvents
    ) {
        this.activation = Objects.requireNonNull(activation, "activation must not be null");
        this.externalIdentities = Objects.requireNonNull(
                externalIdentities,
                "externalIdentities must not be null"
        );
        this.preProvisionedSubjects = Objects.requireNonNull(
                preProvisionedSubjects,
                "preProvisionedSubjects must not be null"
        );
        this.domains = Objects.requireNonNull(domains, "domains must not be null");
        this.memberships = Objects.requireNonNull(memberships, "memberships must not be null");
        this.organizations = Objects.requireNonNull(
                organizations,
                "organizations must not be null"
        );
        this.accounts = Objects.requireNonNull(accounts, "accounts must not be null");
        this.globalMemberships = Objects.requireNonNull(
                globalMemberships,
                "globalMemberships must not be null"
        );
        this.events = Objects.requireNonNull(events, "events must not be null");
        this.transactions = requiresNewTransactions(transactionManager);
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
        this.securityEvents = Objects.requireNonNull(
                securityEvents,
                "securityEvents must not be null"
        );
    }

    public EnterpriseIdentityAssociation associate(IdentityAssertion assertion) {
        return associate(assertion, IdentityCorrelationPolicySettings.disabled());
    }

    public EnterpriseIdentityAssociation associate(
            IdentityAssertion assertion,
            IdentityCorrelationPolicySettings policySettings
    ) {
        Objects.requireNonNull(assertion, "assertion must not be null");
        Objects.requireNonNull(policySettings, "policySettings must not be null");
        if (assertion.organizationId().isEmpty()) {
            throw new EnterpriseIdentityAssociationException(
                    EnterpriseIdentityAssociationFailure.ORGANIZATION_REQUIRED
            );
        }

        try {
            RuntimeException lastConflict = null;
            for (int attempt = 1; attempt <= MAX_ASSOCIATION_ATTEMPTS; attempt++) {
                try {
                    EnterpriseIdentityAssociation result = transactions.execute(
                            ignored -> associateInTransaction(assertion, policySettings)
                    );
                    return Objects.requireNonNull(result, "association result must not be null");
                } catch (DataIntegrityViolationException | OptimisticLockingFailureException conflict) {
                    lastConflict = conflict;
                }
            }
            throw new EnterpriseIdentityAssociationException(
                    EnterpriseIdentityAssociationFailure.CONCURRENT_ASSOCIATION_FAILED,
                    lastConflict
            );
        } catch (EnterpriseIdentityAssociationException failure) {
            if (failure.getFailure() == EnterpriseIdentityAssociationFailure.CORRELATION_CONFLICT) {
                securityEvents.record(
                        EnterpriseIdentitySecurityEventType.IDENTITY_CORRELATION_CONFLICT,
                        assertion.organizationId().orElseThrow(),
                        assertion.connectionId(),
                        "subject:" + coordinateDigest(ExternalIdentityCoordinate.from(assertion))
                );
            }
            throw failure;
        }
    }

    private EnterpriseIdentityAssociation associateInTransaction(
            IdentityAssertion assertion,
            IdentityCorrelationPolicySettings policySettings
    ) {
        ExternalIdentityCoordinate coordinate = ExternalIdentityCoordinate.from(assertion);
        ExternalIdentityLoginModule loginModule = new ExternalIdentityLoginModule(
                activation,
                candidates(),
                effectivePolicy(assertion, policySettings)
        );
        IdentityLoginResolution resolution = loginModule.resolve(assertion);
        if (resolution instanceof IdentityLoginResolution.Matched matched) {
            return switch (matched.stage()) {
                case EXACT_BINDING -> useExactBinding(coordinate);
                case PRE_PROVISIONED_SUBJECT -> claimPreProvisionedMembership(
                        assertion,
                        coordinate,
                        matched.target()
                );
                case VERIFIED_EMAIL -> claimVerifiedEmailMembership(
                        assertion,
                        coordinate,
                        matched.target()
                );
                case JIT_PROVISIONING -> throw new IllegalStateException(
                        "JIT is a provisioning result, not a matched candidate stage"
                );
            };
        }
        if (resolution instanceof IdentityLoginResolution.ProvisionNew) {
            return provisionJitMembership(assertion, coordinate);
        }
        if (resolution instanceof IdentityLoginResolution.Conflict) {
            throw new EnterpriseIdentityAssociationException(
                    EnterpriseIdentityAssociationFailure.CORRELATION_CONFLICT
            );
        }
        throw new EnterpriseIdentityAssociationException(
                EnterpriseIdentityAssociationFailure.NO_SAFE_MATCH
        );
    }

    private IdentityCorrelationPolicy effectivePolicy(
            IdentityAssertion assertion,
            IdentityCorrelationPolicySettings settings
    ) {
        boolean verifiedDomain = settings.allowsVerifiedEmailCorrelation(assertion)
                && hasVerifiedOrganizationDomain(assertion);
        return new IdentityCorrelationPolicy() {
            @Override
            public boolean allowsVerifiedEmailCorrelation(IdentityAssertion ignored) {
                return verifiedDomain;
            }

            @Override
            public boolean allowsJitProvisioning(IdentityAssertion ignored) {
                return verifiedDomain && settings.allowsJitProvisioning(assertion);
            }
        };
    }

    private boolean hasVerifiedOrganizationDomain(IdentityAssertion assertion) {
        String organizationId = assertion.organizationId().orElseThrow();
        boolean organizationActive = organizations.findByIdForUpdate(organizationId)
                .filter(organization -> organization.getStatus() == OrganizationStatus.ACTIVE)
                .isPresent();
        if (!organizationActive) {
            return false;
        }
        return assertion.email()
                .map(VerifiedEmail::value)
                .flatMap(EnterpriseIdentityAssociationService::verifiedOrganizationDomain)
                .flatMap(domains::findVerifiedByDomainForUpdate)
                .filter(domain -> domain.getOrganizationId().equals(organizationId))
                .isPresent();
    }

    private EnterpriseIdentityAssociation useExactBinding(
            ExternalIdentityCoordinate coordinate
    ) {
        String organizationId = coordinate.organizationId().orElseThrow();
        ExternalIdentity identity = externalIdentities.findByCoordinate(coordinate)
                .orElseThrow(() -> new EnterpriseIdentityAssociationException(
                        EnterpriseIdentityAssociationFailure.BINDING_UNAVAILABLE
                ));
        identity.requireActive();
        UserAccount account = requireUsableAccount(identity.getUserId());
        OrganizationMembership membership = memberships
                .findCurrentByOrganizationIdAndUserId(organizationId, account.getId())
                .filter(candidate -> candidate.getStatus() == OrganizationMembershipStatus.ACTIVE)
                .orElseThrow(() -> new EnterpriseIdentityAssociationException(
                        EnterpriseIdentityAssociationFailure.MEMBERSHIP_UNAVAILABLE
                ));

        preProvisionedSubjects.findByCoordinate(coordinate)
                .filter(PreProvisionedLoginSubject::isActive)
                .ifPresent(claim -> {
                    if (!claim.getMembershipId().equals(membership.getId())) {
                        throw new EnterpriseIdentityAssociationException(
                                EnterpriseIdentityAssociationFailure.CORRELATION_CONFLICT
                        );
                    }
                });

        identity.recordAuthentication(clock.instant());
        identity = externalIdentities.saveAndFlush(identity);
        return new EnterpriseIdentityAssociation(
                identity.getId(),
                account.getId(),
                membership.getId(),
                IdentityCorrelationStage.EXACT_BINDING,
                false
        );
    }

    private EnterpriseIdentityAssociation claimPreProvisionedMembership(
            IdentityAssertion assertion,
            ExternalIdentityCoordinate coordinate,
            IdentityCorrelationTarget target
    ) {
        String organizationId = coordinate.organizationId().orElseThrow();
        PreProvisionedLoginSubject claim = preProvisionedSubjects.findByCoordinate(coordinate)
                .filter(PreProvisionedLoginSubject::isActive)
                .orElseThrow(() -> new EnterpriseIdentityAssociationException(
                        EnterpriseIdentityAssociationFailure.MEMBERSHIP_UNAVAILABLE
                ));
        if (!target.membershipId().orElseThrow().equals(claim.getMembershipId())) {
            throw new EnterpriseIdentityAssociationException(
                    EnterpriseIdentityAssociationFailure.CORRELATION_CONFLICT
            );
        }
        OrganizationMembership membership = memberships
                .findByOrganizationIdAndId(organizationId, claim.getMembershipId())
                .orElseThrow(() -> new EnterpriseIdentityAssociationException(
                        EnterpriseIdentityAssociationFailure.MEMBERSHIP_UNAVAILABLE
                ));

        Instant now = clock.instant();
        boolean accountCreated;
        UserAccount account;
        if (membership.getStatus() == OrganizationMembershipStatus.PROVISIONED) {
            account = createAccount(assertion, membership);
            membership.activate(account.getId(), now);
            memberships.save(membership);
            accountCreated = true;
        } else if (membership.getStatus() == OrganizationMembershipStatus.ACTIVE
                && membership.getUserId() != null) {
            account = requireUsableAccount(membership.getUserId());
            accountCreated = false;
        } else {
            throw new EnterpriseIdentityAssociationException(
                    EnterpriseIdentityAssociationFailure.MEMBERSHIP_UNAVAILABLE
            );
        }

        ExternalIdentity identity = ExternalIdentity.bind(coordinate, account.getId(), now);
        identity = externalIdentities.saveAndFlush(identity);
        if (accountCreated) {
            globalMemberships.ensureMember(account.getId());
            events.publishEvent(new UserActivatedEvent(
                    account.getId(),
                    assertion.loginName().orElse(account.getDisplayName()),
                    account.getEmail()
            ));
        }
        return new EnterpriseIdentityAssociation(
                identity.getId(),
                account.getId(),
                membership.getId(),
                IdentityCorrelationStage.PRE_PROVISIONED_SUBJECT,
                accountCreated
        );
    }

    private EnterpriseIdentityAssociation claimVerifiedEmailMembership(
            IdentityAssertion assertion,
            ExternalIdentityCoordinate coordinate,
            IdentityCorrelationTarget target
    ) {
        String organizationId = coordinate.organizationId().orElseThrow();
        String membershipId = target.membershipId().orElseThrow(
                () -> new EnterpriseIdentityAssociationException(
                        EnterpriseIdentityAssociationFailure.CORRELATION_CONFLICT
                )
        );
        OrganizationMembership membership = memberships
                .findByOrganizationIdAndId(organizationId, membershipId)
                .orElseThrow(() -> new EnterpriseIdentityAssociationException(
                        EnterpriseIdentityAssociationFailure.MEMBERSHIP_UNAVAILABLE
                ));
        String verifiedEmail = assertion.email().orElseThrow().value();
        if (membership.getPrimaryEmail() == null
                || !membership.getPrimaryEmail().equalsIgnoreCase(verifiedEmail)) {
            throw new EnterpriseIdentityAssociationException(
                    EnterpriseIdentityAssociationFailure.CORRELATION_CONFLICT
            );
        }
        if (membership.getStatus() == OrganizationMembershipStatus.ACTIVE
                && membership.getUserId() != null) {
            throw new EnterpriseIdentityAssociationException(
                    EnterpriseIdentityAssociationFailure.ACCOUNT_REAUTHENTICATION_REQUIRED
            );
        }
        if (membership.getStatus() != OrganizationMembershipStatus.PROVISIONED
                || membership.getUserId() != null
                || target.userId().isPresent()) {
            throw new EnterpriseIdentityAssociationException(
                    EnterpriseIdentityAssociationFailure.MEMBERSHIP_UNAVAILABLE
            );
        }
        return createAccountAssociation(
                assertion,
                coordinate,
                membership,
                IdentityCorrelationStage.VERIFIED_EMAIL
        );
    }

    private EnterpriseIdentityAssociation provisionJitMembership(
            IdentityAssertion assertion,
            ExternalIdentityCoordinate coordinate
    ) {
        String organizationId = coordinate.organizationId().orElseThrow();
        String verifiedEmail = assertion.email().orElseThrow().value();
        String displayName = firstPresent(
                assertion.displayName(),
                assertion.loginName()
        ).orElse(verifiedEmail.substring(0, verifiedEmail.lastIndexOf('@')));
        requireBounded(displayName, 128, "displayName");
        OrganizationMembership membership = OrganizationMembership.provisioned(
                organizationId,
                MembershipSourceType.JIT,
                jitSourceId(coordinate),
                displayName,
                verifiedEmail,
                clock.instant()
        );
        membership = memberships.save(membership);
        return createAccountAssociation(
                assertion,
                coordinate,
                membership,
                IdentityCorrelationStage.JIT_PROVISIONING
        );
    }

    private EnterpriseIdentityAssociation createAccountAssociation(
            IdentityAssertion assertion,
            ExternalIdentityCoordinate coordinate,
            OrganizationMembership membership,
            IdentityCorrelationStage stage
    ) {
        Instant now = clock.instant();
        UserAccount account = createAccount(assertion, membership);
        membership.activate(account.getId(), now);
        memberships.save(membership);
        ExternalIdentity identity = externalIdentities.saveAndFlush(
                ExternalIdentity.bind(coordinate, account.getId(), now)
        );
        globalMemberships.ensureMember(account.getId());
        events.publishEvent(new UserActivatedEvent(
                account.getId(),
                assertion.loginName().orElse(account.getDisplayName()),
                account.getEmail()
        ));
        return new EnterpriseIdentityAssociation(
                identity.getId(),
                account.getId(),
                membership.getId(),
                stage,
                true
        );
    }

    private UserAccount createAccount(
            IdentityAssertion assertion,
            OrganizationMembership membership
    ) {
        String displayName = firstPresent(
                optional(membership.getDisplayName()),
                assertion.displayName(),
                assertion.loginName()
        ).orElse("Enterprise user");
        String email = assertion.email().map(VerifiedEmail::value).orElse(null);
        String avatarUrl = assertion.avatarUrl().orElse(null);
        requireBounded(displayName, 128, "displayName");
        requireBoundedNullable(avatarUrl, 512, "avatarUrl");
        UserAccount account = new UserAccount(
                "usr_" + UUID.randomUUID(),
                displayName,
                email,
                avatarUrl
        );
        return accounts.save(account);
    }

    private UserAccount requireUsableAccount(String userId) {
        UserAccount account = accounts.findById(userId)
                .orElseThrow(() -> new EnterpriseIdentityAssociationException(
                        EnterpriseIdentityAssociationFailure.ACCOUNT_UNAVAILABLE
                ));
        if (!account.isActive()
                || account.isSystemAccount()
                || account.getMergedToUserId() != null) {
            throw new EnterpriseIdentityAssociationException(
                    EnterpriseIdentityAssociationFailure.ACCOUNT_UNAVAILABLE
            );
        }
        return account;
    }

    private IdentityCorrelationCandidates candidates() {
        return new IdentityCorrelationCandidates() {
            @Override
            public List<IdentityCorrelationTarget> findExactBinding(
                    ExternalIdentityCoordinate coordinate
            ) {
                return externalIdentities.findByCoordinate(coordinate)
                        .map(identity -> List.of(IdentityCorrelationTarget.account(
                                identity.getUserId()
                        )))
                        .orElseGet(List::of);
            }

            @Override
            public List<IdentityCorrelationTarget> findPreProvisionedSubject(
                    ExternalIdentityCoordinate coordinate
            ) {
                return preProvisionedSubjects.findByCoordinate(coordinate)
                        .map(subject -> List.of(IdentityCorrelationTarget.membership(
                                subject.getMembershipId()
                        )))
                        .orElseGet(List::of);
            }

            @Override
            public List<IdentityCorrelationTarget> findByVerifiedEmail(
                    String organizationId,
                    VerifiedEmail email
            ) {
                return memberships.findIdentityCorrelationCandidatesByPrimaryEmail(
                                organizationId,
                                email.value()
                        ).stream()
                        .map(membership -> membership.getUserId() == null
                                ? IdentityCorrelationTarget.membership(membership.getId())
                                : IdentityCorrelationTarget.linked(
                                        membership.getUserId(),
                                        membership.getId()
                                ))
                        .toList();
            }
        };
    }

    private static Optional<String> verifiedOrganizationDomain(String email) {
        int separator = email.lastIndexOf('@');
        if (separator < 1 || separator == email.length() - 1) {
            return Optional.empty();
        }
        try {
            return Optional.of(OrganizationDomain.normalize(email.substring(separator + 1)));
        } catch (DomainBadRequestException invalidDomain) {
            return Optional.empty();
        }
    }

    private static String jitSourceId(ExternalIdentityCoordinate coordinate) {
        return "jit:" + coordinateDigest(coordinate);
    }

    private static String coordinateDigest(ExternalIdentityCoordinate coordinate) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            updateDigest(digest, coordinate.connectionId());
            updateDigest(digest, coordinate.issuer().toString());
            updateDigest(digest, coordinate.subject().type().value());
            updateDigest(digest, coordinate.subject().value());
            return Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(digest.digest());
        } catch (GeneralSecurityException unavailable) {
            throw new IllegalStateException("SHA-256 is unavailable", unavailable);
        }
    }

    private static void updateDigest(MessageDigest digest, String value) {
        byte[] encoded = value.getBytes(StandardCharsets.UTF_8);
        digest.update(ByteBuffer.allocate(Integer.BYTES).putInt(encoded.length).array());
        digest.update(encoded);
    }

    @SafeVarargs
    private static Optional<String> firstPresent(Optional<String>... candidates) {
        for (Optional<String> candidate : candidates) {
            if (candidate.isPresent()) {
                return candidate;
            }
        }
        return Optional.empty();
    }

    private static Optional<String> optional(String value) {
        return Optional.ofNullable(value).filter(text -> !text.isBlank());
    }

    private static void requireBounded(String value, int maximum, String field) {
        if (value.isBlank() || value.length() > maximum) {
            throw new IllegalArgumentException(field + " is outside its persistence bounds");
        }
    }

    private static void requireBoundedNullable(String value, int maximum, String field) {
        if (value != null && value.length() > maximum) {
            throw new IllegalArgumentException(field + " is outside its persistence bounds");
        }
    }

    private static TransactionOperations requiresNewTransactions(
            PlatformTransactionManager transactionManager
    ) {
        TransactionTemplate transaction = new TransactionTemplate(
                Objects.requireNonNull(transactionManager, "transactionManager must not be null")
        );
        transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        return transaction;
    }
}
