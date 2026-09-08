package com.iflytek.skillhub.domain.organization;

import com.iflytek.skillhub.domain.shared.exception.DomainForbiddenException;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.stereotype.Component;

/** Explicit Organization RBAC matrix; Platform and Namespace roles are not valid inputs. */
@Component
public class OrganizationAuthorizationPolicy {

    private static final Set<OrganizationRole> ALL_ROLES = Set.of(OrganizationRole.values());

    private static final Map<OrganizationAdministrativeAction, Set<OrganizationRole>>
            ALLOWED_ROLES = Map.ofEntries(
                    Map.entry(OrganizationAdministrativeAction.VIEW_ORGANIZATION, ALL_ROLES),
                    Map.entry(
                            OrganizationAdministrativeAction.VIEW_ORGANIZATION_ROLES,
                            Set.of(OrganizationRole.ORG_OWNER, OrganizationRole.ORG_AUDITOR)
                    ),
                    Map.entry(
                            OrganizationAdministrativeAction.VIEW_DOMAINS,
                            Set.of(
                                    OrganizationRole.ORG_OWNER,
                                    OrganizationRole.IDENTITY_ADMIN,
                                    OrganizationRole.LOGIN_SECRET_ADMIN,
                                    OrganizationRole.MEMBER_ADMIN,
                                    OrganizationRole.ORG_AUDITOR
                            )
                    ),
                    Map.entry(
                            OrganizationAdministrativeAction.VIEW_MEMBERS,
                            Set.of(
                                    OrganizationRole.ORG_OWNER,
                                    OrganizationRole.MEMBER_ADMIN,
                                    OrganizationRole.ORG_AUDITOR
                            )
                    ),
                    Map.entry(
                            OrganizationAdministrativeAction.VIEW_LOGIN_CONNECTIONS,
                            Set.of(
                                    OrganizationRole.ORG_OWNER,
                                    OrganizationRole.IDENTITY_ADMIN,
                                    OrganizationRole.LOGIN_SECRET_ADMIN,
                                    OrganizationRole.MEMBER_ADMIN,
                                    OrganizationRole.ORG_AUDITOR
                            )
                    ),
                    Map.entry(
                            OrganizationAdministrativeAction.VIEW_LOGIN_CONNECTIONS,
                            Set.of(
                                    OrganizationRole.ORG_OWNER,
                                    OrganizationRole.IDENTITY_ADMIN,
                                    OrganizationRole.ORG_AUDITOR
                            )
                    ),
                    Map.entry(
                            OrganizationAdministrativeAction.MANAGE_ORGANIZATION_LIFECYCLE,
                            Set.of(OrganizationRole.ORG_OWNER)
                    ),
                    Map.entry(
                            OrganizationAdministrativeAction.MANAGE_ORGANIZATION_ROLES,
                            Set.of(OrganizationRole.ORG_OWNER)
                    ),
                    Map.entry(
                            OrganizationAdministrativeAction.MANAGE_DOMAINS,
                            Set.of(OrganizationRole.IDENTITY_ADMIN)
                    ),
                    Map.entry(
                            OrganizationAdministrativeAction.MANAGE_LOGIN_CONNECTIONS,
                            Set.of(OrganizationRole.IDENTITY_ADMIN)
                    ),
                    Map.entry(
                            OrganizationAdministrativeAction.ROTATE_LOGIN_SECRETS,
                            Set.of(OrganizationRole.LOGIN_SECRET_ADMIN)
                    ),
                    Map.entry(
                            OrganizationAdministrativeAction.RESOLVE_IDENTITY_CONFLICTS,
                            Set.of(OrganizationRole.IDENTITY_ADMIN)
                    ),
                    Map.entry(
                            OrganizationAdministrativeAction.MANAGE_MEMBERS,
                            Set.of(OrganizationRole.MEMBER_ADMIN)
                    ),
                    Map.entry(
                            OrganizationAdministrativeAction.VIEW_AUDIT,
                            Set.of(OrganizationRole.ORG_OWNER, OrganizationRole.ORG_AUDITOR)
                    ),
                    Map.entry(OrganizationAdministrativeAction.VIEW_HEALTH, ALL_ROLES)
            );

    public boolean isAllowed(
            Set<OrganizationRole> roles,
            OrganizationAdministrativeAction action
    ) {
        Objects.requireNonNull(action, "action");
        if (roles == null || roles.isEmpty()) {
            return false;
        }
        Set<OrganizationRole> allowedRoles = ALLOWED_ROLES.get(action);
        return allowedRoles != null && !Collections.disjoint(roles, allowedRoles);
    }

    public void requireAllowed(
            Set<OrganizationRole> roles,
            OrganizationAdministrativeAction action
    ) {
        if (!isAllowed(roles, action)) {
            throw new DomainForbiddenException("error.organization.permission.denied");
        }
    }
}
