package com.iflytek.skillhub.domain.organization;

import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/** Resolves active Organization roles and applies the explicit administrative policy. */
@Service
public class OrganizationAuthorizationService {

    private final OrganizationAccessGuard accessGuard;
    private final OrganizationRoleBindingRepository roleBindingRepository;
    private final OrganizationAuthorizationPolicy authorizationPolicy;

    public OrganizationAuthorizationService(
            OrganizationAccessGuard accessGuard,
            OrganizationRoleBindingRepository roleBindingRepository,
            OrganizationAuthorizationPolicy authorizationPolicy
    ) {
        this.accessGuard = accessGuard;
        this.roleBindingRepository = roleBindingRepository;
        this.authorizationPolicy = authorizationPolicy;
    }

    public void requireAllowed(
            String organizationId,
            String userId,
            OrganizationAdministrativeAction action
    ) {
        if (action == OrganizationAdministrativeAction.MANAGE_ORGANIZATION_LIFECYCLE) {
            accessGuard.requireCurrentForLifecycleRecovery(organizationId, userId);
        } else {
            accessGuard.requireCurrent(organizationId, userId);
        }
        Set<OrganizationRole> roles = roleBindingRepository
                .findActiveByOrganizationIdAndUserId(organizationId, userId)
                .stream()
                .map(OrganizationRoleBinding::getRole)
                .collect(Collectors.toUnmodifiableSet());
        authorizationPolicy.requireAllowed(roles, action);
    }
}
