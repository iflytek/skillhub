package com.iflytek.skillhub.dto;

public record OrganizationPermissionsResponse(
        boolean canViewManagement,
        boolean canViewRoles,
        boolean canViewDomains,
        boolean canViewMembers,
        boolean canViewLoginConnections,
        boolean canManageLoginConnections,
        boolean canRotateLoginSecrets,
        boolean canManageMembers,
        boolean canViewAudit
) {
}
