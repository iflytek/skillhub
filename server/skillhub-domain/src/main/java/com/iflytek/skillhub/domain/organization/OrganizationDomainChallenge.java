package com.iflytek.skillhub.domain.organization;

/** Plaintext DNS proof returned once to an authorized administrator. */
public record OrganizationDomainChallenge(
        String domainId,
        String domain,
        OrganizationDomainVerificationMethod method,
        String proofToken
) {

    @Override
    public String toString() {
        return "OrganizationDomainChallenge[domainId=" + domainId
                + ", domain=" + domain
                + ", method=" + method
                + ", proofToken=<redacted>]";
    }
}
