package com.iflytek.skillhub.domain.organization;

import java.util.List;

/** Remote DNS port used by the application layer before opening the verification transaction. */
public interface OrganizationDomainProofResolver {

    List<String> resolveTxt(String recordName);
}
