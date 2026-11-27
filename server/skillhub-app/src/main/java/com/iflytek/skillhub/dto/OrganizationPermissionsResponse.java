package com.iflytek.skillhub.dto;

public record OrganizationPermissionsResponse(
        boolean canViewManagement,
        boolean canViewRoles,
        boolean canViewDomains,
        boolean canViewMembers,
        boolean canViewLoginConnections,
        boolean canManageLoginConnections,
        boolean canRotateLoginSecrets,
        boolean canViewDirectoryConnections,
        boolean canManageDirectoryConnections,
        boolean canRotateDirectorySecrets,
        boolean canManageMembers,
        boolean canViewAudit
) {
}
