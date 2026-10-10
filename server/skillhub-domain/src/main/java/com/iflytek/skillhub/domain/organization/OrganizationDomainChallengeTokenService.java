package com.iflytek.skillhub.domain.organization;

/** Generates and verifies one-time ownership proofs without exposing hash mechanics to domain code. */
public interface OrganizationDomainChallengeTokenService {

    OrganizationDomainChallengeToken issue();

    boolean matches(String candidate, String storedDigest);
}
