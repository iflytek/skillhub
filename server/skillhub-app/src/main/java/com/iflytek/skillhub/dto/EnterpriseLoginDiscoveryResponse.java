package com.iflytek.skillhub.dto;

import java.util.List;

public record EnterpriseLoginDiscoveryResponse(
        List<AuthMethodResponse> publicMethods,
        List<OrganizationLoginOptions> organizations
) {
    public EnterpriseLoginDiscoveryResponse {
        publicMethods = List.copyOf(publicMethods);
        organizations = List.copyOf(organizations);
    }

    public record OrganizationLoginOptions(
            String slug,
            String displayName,
            List<LoginOption> loginOptions
    ) {
        public OrganizationLoginOptions {
            loginOptions = List.copyOf(loginOptions);
        }
    }

    public record LoginOption(String displayName, String actionUrl) {
    }
}
