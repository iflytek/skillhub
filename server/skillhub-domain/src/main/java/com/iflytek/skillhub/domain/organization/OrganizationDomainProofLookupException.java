package com.iflytek.skillhub.domain.organization;

import com.iflytek.skillhub.domain.shared.exception.LocalizedDomainException;

/** Safe error for bounded DNS failures; deliberately carries no record value or token. */
public class OrganizationDomainProofLookupException extends LocalizedDomainException {

    public OrganizationDomainProofLookupException() {
        super("error.organization.domain.dns-unavailable");
    }

    @Override
    public int statusCode() {
        return 503;
    }
}
