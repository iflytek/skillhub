package com.iflytek.skillhub.domain.organization;

import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Fail-closed lookup used by login discovery and verified-email correlation. */
@Service
public class OrganizationDomainLookupService {

    private final OrganizationDomainRepository domainRepository;
    private final OrganizationRepository organizationRepository;

    public OrganizationDomainLookupService(
            OrganizationDomainRepository domainRepository,
            OrganizationRepository organizationRepository
    ) {
        this.domainRepository = domainRepository;
        this.organizationRepository = organizationRepository;
    }

    @Transactional(readOnly = true)
    public Optional<OrganizationDomain> findActiveVerifiedOwnership(String domain) {
        String normalized = OrganizationDomain.normalize(domain);
        return domainRepository.findVerifiedByDomain(normalized)
                .filter(claim -> claim.getStatus() == OrganizationDomainStatus.VERIFIED)
                .filter(claim -> organizationRepository.findById(claim.getOrganizationId())
                        .filter(organization ->
                                organization.getStatus() == OrganizationStatus.ACTIVE)
                        .isPresent());
    }
}
