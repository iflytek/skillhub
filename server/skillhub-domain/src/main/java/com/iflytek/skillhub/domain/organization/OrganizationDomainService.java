package com.iflytek.skillhub.domain.organization;

import com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException;
import com.iflytek.skillhub.domain.shared.exception.DomainConflictException;
import com.iflytek.skillhub.domain.shared.exception.DomainNotFoundException;
import java.time.Instant;
import java.util.Collections;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Owns organization-domain challenge, verification, conflict and disable transitions. */
@Service
public class OrganizationDomainService {

    private final OrganizationRepository organizationRepository;
    private final OrganizationDomainRepository domainRepository;
    private final OrganizationAuthorizationService authorizationService;
    private final OrganizationDomainChallengeTokenService tokenService;

    public OrganizationDomainService(
            OrganizationRepository organizationRepository,
            OrganizationDomainRepository domainRepository,
            OrganizationAuthorizationService authorizationService,
            OrganizationDomainChallengeTokenService tokenService
    ) {
        this.organizationRepository = organizationRepository;
        this.domainRepository = domainRepository;
        this.authorizationService = authorizationService;
        this.tokenService = tokenService;
    }

    @Transactional
    public OrganizationDomainChallenge issueChallenge(
            String organizationId,
            String domain,
            OrganizationDomainVerificationMethod method,
            String actorUserId,
            Instant occurredAt
    ) {
        String scopedOrganizationId = requireNonBlank(organizationId, "organizationId");
        String requiredActorUserId = requireNonBlank(actorUserId, "actorUserId");
        String normalizedDomain = OrganizationDomain.normalize(domain);
        OrganizationDomainVerificationMethod requiredMethod =
                Objects.requireNonNull(method, "method");
        Instant challengeTime = Objects.requireNonNull(occurredAt, "occurredAt");

        authorizationService.requireAllowed(
                scopedOrganizationId,
                requiredActorUserId,
                OrganizationAdministrativeAction.MANAGE_DOMAINS
        );
        requireActiveOrganization(scopedOrganizationId);
        rejectVerifiedOwner(scopedOrganizationId, normalizedDomain);

        OrganizationDomainChallengeToken token = tokenService.issue();
        OrganizationDomain claim = domainRepository
                .findByOrganizationIdAndDomain(scopedOrganizationId, normalizedDomain)
                .map(existing -> {
                    existing.reissueChallenge(
                            requiredMethod,
                            token.digest(),
                            challengeTime
                    );
                    return existing;
                })
                .orElseGet(() -> OrganizationDomain.claim(
                        scopedOrganizationId,
                        normalizedDomain,
                        requiredMethod,
                        token.digest(),
                        challengeTime
                ));
        OrganizationDomain saved = domainRepository.save(claim);
        return new OrganizationDomainChallenge(
                saved.getId(),
                saved.getDomain(),
                saved.getVerificationMethod(),
                token.plaintext()
        );
    }

    @Transactional
    public OrganizationDomain verify(
            String organizationId,
            String domainId,
            String observedProof,
            String actorUserId,
            Instant occurredAt
    ) {
        return verifyAny(
                organizationId,
                domainId,
                Collections.singletonList(observedProof),
                actorUserId,
                occurredAt
        );
    }

    @Transactional
    public OrganizationDomain verifyAny(
            String organizationId,
            String domainId,
            Iterable<String> observedProofs,
            String actorUserId,
            Instant occurredAt
    ) {
        String scopedOrganizationId = requireNonBlank(organizationId, "organizationId");
        String requiredDomainId = requireNonBlank(domainId, "domainId");
        String requiredActorUserId = requireNonBlank(actorUserId, "actorUserId");

        authorizationService.requireAllowed(
                scopedOrganizationId,
                requiredActorUserId,
                OrganizationAdministrativeAction.MANAGE_DOMAINS
        );
        requireActiveOrganization(scopedOrganizationId);
        OrganizationDomain claim = getClaim(scopedOrganizationId, requiredDomainId);
        if (claim.getStatus() == OrganizationDomainStatus.VERIFIED) {
            return claim;
        }
        if (claim.getStatus() != OrganizationDomainStatus.PENDING) {
            throw new DomainBadRequestException(
                    "error.organization.domain.transition.invalid"
            );
        }
        boolean matched = false;
        if (observedProofs != null) {
            for (String observedProof : observedProofs) {
                if (tokenService.matches(observedProof, claim.challengeDigest())) {
                    matched = true;
                    break;
                }
            }
        }
        if (!matched) {
            throw new DomainConflictException(
                    "error.organization.domain.proof-mismatch"
            );
        }
        rejectVerifiedOwner(scopedOrganizationId, claim.getDomain());
        claim.verify(Objects.requireNonNull(occurredAt, "occurredAt"));
        return domainRepository.save(claim);
    }

    @Transactional
    public OrganizationDomain disable(
            String organizationId,
            String domainId,
            String actorUserId,
            Instant occurredAt
    ) {
        String scopedOrganizationId = requireNonBlank(organizationId, "organizationId");
        String requiredDomainId = requireNonBlank(domainId, "domainId");
        String requiredActorUserId = requireNonBlank(actorUserId, "actorUserId");

        authorizationService.requireAllowed(
                scopedOrganizationId,
                requiredActorUserId,
                OrganizationAdministrativeAction.MANAGE_DOMAINS
        );
        requireActiveOrganization(scopedOrganizationId);
        OrganizationDomain claim = getClaim(scopedOrganizationId, requiredDomainId);
        if (!claim.disable(Objects.requireNonNull(occurredAt, "occurredAt"))) {
            return claim;
        }
        return domainRepository.save(claim);
    }

    private void rejectVerifiedOwner(String organizationId, String domain) {
        domainRepository.findVerifiedByDomain(domain).ifPresent(owner -> {
            if (owner.getOrganizationId().equals(organizationId)) {
                throw new DomainConflictException(
                        "error.organization.domain.already-verified"
                );
            }
            throw new DomainConflictException(
                    "error.organization.domain.ownership-conflict"
            );
        });
    }

    private OrganizationDomain getClaim(String organizationId, String domainId) {
        return domainRepository.findByOrganizationIdAndId(organizationId, domainId)
                .orElseThrow(() -> new DomainNotFoundException(
                        "error.organization.domain.not-found"
                ));
    }

    private Organization requireActiveOrganization(String organizationId) {
        Organization organization = organizationRepository.findById(organizationId)
                .orElseThrow(() -> new DomainNotFoundException(
                        "error.organization.not-found"
                ));
        if (organization.getStatus() != OrganizationStatus.ACTIVE) {
            throw new DomainConflictException("error.organization.inactive");
        }
        return organization;
    }

    private static String requireNonBlank(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new DomainBadRequestException(
                    "error.organization.domain.field.required",
                    field
            );
        }
        return value;
    }
}
