package com.iflytek.skillhub.dto;

import com.iflytek.skillhub.domain.organization.OrganizationDomainVerificationMethod;

/** One-time DNS instructions; string rendering is redacted to keep the proof out of logs. */
public record OrganizationDomainChallengeResponse(
        String domainId,
        String domain,
        OrganizationDomainVerificationMethod method,
        String recordName,
        String recordValue
) {
    @Override
    public String toString() {
        return "OrganizationDomainChallengeResponse[domainId=" + domainId
                + ", domain=" + domain
                + ", method=" + method
                + ", recordName=" + recordName
                + ", recordValue=<redacted>]";
    }
}
