package com.iflytek.skillhub.auth.federation.core;

import java.util.List;

/** Read-only candidate lookup port used by deterministic identity correlation. */
public interface IdentityCorrelationCandidates {

    List<IdentityCorrelationTarget> findExactBinding(ExternalIdentityCoordinate coordinate);

    List<IdentityCorrelationTarget> findPreProvisionedSubject(ExternalIdentityCoordinate coordinate);

    List<IdentityCorrelationTarget> findByVerifiedEmail(String organizationId, VerifiedEmail email);
}
