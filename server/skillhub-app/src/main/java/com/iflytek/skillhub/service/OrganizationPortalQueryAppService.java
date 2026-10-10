package com.iflytek.skillhub.service;

import com.iflytek.skillhub.domain.organization.OrganizationAdministrativeAction;
import com.iflytek.skillhub.domain.organization.OrganizationAuthorizationPolicy;
import com.iflytek.skillhub.domain.organization.OrganizationAuthorizationService;
import com.iflytek.skillhub.domain.organization.OrganizationRole;
import com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException;
import com.iflytek.skillhub.domain.shared.exception.DomainForbiddenException;
import com.iflytek.skillhub.dto.LoginConnectionSummaryResponse;
import com.iflytek.skillhub.dto.OrganizationDetailResponse;
import com.iflytek.skillhub.dto.OrganizationPermissionsResponse;
import com.iflytek.skillhub.dto.OrganizationSummaryResponse;
import com.iflytek.skillhub.dto.PageResponse;
import com.iflytek.skillhub.repository.OrganizationQueryRepository;
import com.iflytek.skillhub.repository.OrganizationQueryRepository.OrganizationMembershipProjection;
import java.util.Collection;
import java.util.Comparator;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrganizationPortalQueryAppService {

    private static final int MAX_PAGE_SIZE = 100;

    private final OrganizationQueryRepository organizationQueryRepository;
    private final OrganizationAuthorizationService authorizationService;
    private final OrganizationAuthorizationPolicy authorizationPolicy;

    public OrganizationPortalQueryAppService(
            OrganizationQueryRepository organizationQueryRepository,
            OrganizationAuthorizationService authorizationService,
            OrganizationAuthorizationPolicy authorizationPolicy
    ) {
        this.organizationQueryRepository = organizationQueryRepository;
        this.authorizationService = authorizationService;
        this.authorizationPolicy = authorizationPolicy;
    }

    @Transactional(readOnly = true)
    public PageResponse<OrganizationSummaryResponse> listOrganizations(
            String userId,
            int page,
            int size
    ) {
        Pageable pageable = pageRequest(page, size);
        var memberships = organizationQueryRepository.findActiveMembershipsForUser(
                userId,
                pageable
        );
        Map<String, Set<String>> roleNames = organizationQueryRepository.findActiveRoleNames(
                userId,
                memberships.stream().map(OrganizationMembershipProjection::id).toList()
        );
        return PageResponse.from(memberships.map(membership ->
                toSummary(membership, roleNames.getOrDefault(membership.id(), Set.of()))));
    }

    @Transactional(readOnly = true)
    public OrganizationDetailResponse getOrganization(String organizationId, String userId) {
        authorizationService.requireAllowed(
                organizationId,
                userId,
                OrganizationAdministrativeAction.VIEW_ORGANIZATION
        );
        OrganizationMembershipProjection membership = organizationQueryRepository
                .findActiveMembership(organizationId, userId)
                .orElseThrow(() -> new DomainForbiddenException(
                        "error.organization.permission.denied"
                ));
        Set<String> roles = organizationQueryRepository
                .findActiveRoleNames(userId, Set.of(organizationId))
                .getOrDefault(organizationId, Set.of());
        return toDetail(membership, roles);
    }

    @Transactional(readOnly = true)
    public PageResponse<LoginConnectionSummaryResponse> listLoginConnections(
            String organizationId,
            String userId,
            int page,
            int size
    ) {
        authorizationService.requireAllowed(
                organizationId,
                userId,
                OrganizationAdministrativeAction.VIEW_LOGIN_CONNECTIONS
        );
        return PageResponse.from(organizationQueryRepository
                .findLoginConnections(organizationId, pageRequest(page, size))
                .map(connection -> new LoginConnectionSummaryResponse(
                        connection.id(),
                        connection.publicHandle(),
                        connection.displayName(),
                        connection.status(),
                        connection.adapterKey(),
                        connection.activeRevisionId(),
                        connection.lastTestedRevisionId(),
                        connection.createdAt(),
                        connection.updatedAt(),
                        connection.version()
                )));
    }

    private OrganizationSummaryResponse toSummary(
            OrganizationMembershipProjection membership,
            Set<String> roleNames
    ) {
        Set<OrganizationRole> roles = roles(roleNames);
        return new OrganizationSummaryResponse(
                membership.id(),
                membership.slug(),
                membership.displayName(),
                membership.status(),
                membership.authorityVersion(),
                membership.membershipId(),
                membership.membershipAuthorityVersion(),
                roleNames(roleNames),
                membership.memberSince(),
                membership.updatedAt(),
                permissions(roles)
        );
    }

    private OrganizationDetailResponse toDetail(
            OrganizationMembershipProjection membership,
            Set<String> roleNames
    ) {
        Set<OrganizationRole> roles = roles(roleNames);
        return new OrganizationDetailResponse(
                membership.id(),
                membership.slug(),
                membership.displayName(),
                membership.status(),
                membership.authorityVersion(),
                membership.membershipId(),
                membership.membershipAuthorityVersion(),
                roleNames(roleNames),
                membership.memberSince(),
                membership.createdAt(),
                membership.updatedAt(),
                permissions(roles)
        );
    }

    private OrganizationPermissionsResponse permissions(Set<OrganizationRole> roles) {
        return new OrganizationPermissionsResponse(
                authorizationPolicy.isAllowed(roles, OrganizationAdministrativeAction.VIEW_ORGANIZATION),
                authorizationPolicy.isAllowed(roles, OrganizationAdministrativeAction.VIEW_ORGANIZATION_ROLES),
                authorizationPolicy.isAllowed(roles, OrganizationAdministrativeAction.VIEW_DOMAINS),
                authorizationPolicy.isAllowed(roles, OrganizationAdministrativeAction.VIEW_MEMBERS),
                authorizationPolicy.isAllowed(roles, OrganizationAdministrativeAction.VIEW_LOGIN_CONNECTIONS),
                authorizationPolicy.isAllowed(roles, OrganizationAdministrativeAction.MANAGE_LOGIN_CONNECTIONS),
                authorizationPolicy.isAllowed(roles, OrganizationAdministrativeAction.ROTATE_LOGIN_SECRETS),
                authorizationPolicy.isAllowed(roles, OrganizationAdministrativeAction.MANAGE_MEMBERS),
                authorizationPolicy.isAllowed(roles, OrganizationAdministrativeAction.VIEW_AUDIT)
        );
    }

    private Set<OrganizationRole> roles(Collection<String> roleNames) {
        return roleNames.stream()
                .map(role -> OrganizationRole.valueOf(role.toUpperCase(Locale.ROOT)))
                .collect(Collectors.toUnmodifiableSet());
    }

    private Set<String> roleNames(Collection<String> roleNames) {
        return roleNames.stream()
                .sorted(Comparator.naturalOrder())
                .collect(Collectors.toUnmodifiableSet());
    }

    private Pageable pageRequest(int page, int size) {
        if (page < 0 || size < 1) {
            throw new DomainBadRequestException("error.pagination.invalid");
        }
        return PageRequest.of(page, Math.min(size, MAX_PAGE_SIZE));
    }
}
