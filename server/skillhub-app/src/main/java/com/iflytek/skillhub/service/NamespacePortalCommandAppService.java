package com.iflytek.skillhub.service;

import com.iflytek.skillhub.auth.rbac.PlatformPrincipal;
import com.iflytek.skillhub.domain.namespace.Namespace;
import com.iflytek.skillhub.domain.namespace.NamespaceGovernanceService;
import com.iflytek.skillhub.domain.namespace.NamespaceMember;
import com.iflytek.skillhub.domain.namespace.NamespaceMemberService;
import com.iflytek.skillhub.domain.namespace.NamespaceRepository;
import com.iflytek.skillhub.domain.namespace.NamespaceService;
import com.iflytek.skillhub.dto.MemberResponse;
import com.iflytek.skillhub.dto.MessageResponse;
import com.iflytek.skillhub.dto.NamespaceLifecycleRequest;
import com.iflytek.skillhub.dto.NamespaceRequest;
import com.iflytek.skillhub.dto.NamespaceResponse;
import com.iflytek.skillhub.dto.UpdateMemberRoleRequest;
import com.iflytek.skillhub.exception.ForbiddenException;
import com.iflytek.skillhub.exception.UnauthorizedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Command-facing namespace application service for portal endpoints.
 */
@Service
public class NamespacePortalCommandAppService {

    private final NamespaceService namespaceService;
    private final NamespaceRepository namespaceRepository;
    private final NamespaceGovernanceService namespaceGovernanceService;
    private final NamespaceMemberService namespaceMemberService;

    public NamespacePortalCommandAppService(NamespaceService namespaceService,
                                            NamespaceRepository namespaceRepository,
                                            NamespaceGovernanceService namespaceGovernanceService,
                                            NamespaceMemberService namespaceMemberService) {
        this.namespaceService = namespaceService;
        this.namespaceRepository = namespaceRepository;
        this.namespaceGovernanceService = namespaceGovernanceService;
        this.namespaceMemberService = namespaceMemberService;
    }

    @Transactional
    public NamespaceResponse createNamespace(NamespaceRequest request, PlatformPrincipal principal) {
        if (principal == null) {
            throw new UnauthorizedException("error.auth.required");
        }
        if (!canCreateNamespace(principal)) {
            throw new ForbiddenException("error.namespace.create.platformAdminRequired");
        }

        Namespace namespace = namespaceService.createNamespace(
                request.slug(),
                request.displayName(),
                request.description(),
                principal.userId()
        );
        return NamespaceResponse.from(namespace);
    }

    @Transactional
    public NamespaceResponse updateNamespace(String slug, NamespaceRequest request, String userId) {
        Namespace namespace = namespaceService.getNamespaceBySlug(slug);
        Namespace updated = namespaceService.updateNamespace(
                namespace.getId(),
                request.displayName(),
                request.description(),
                null,
                userId
        );
        return NamespaceResponse.from(updated);
    }

    @Transactional
    public NamespaceResponse freezeNamespace(String slug,
                                             NamespaceLifecycleRequest request,
                                             String userId,
                                             AuditRequestContext auditContext) {
        Namespace namespace = namespaceGovernanceService.freezeNamespace(
                slug,
                userId,
                request != null ? request.reason() : null,
                null,
                auditContext.clientIp(),
                auditContext.userAgent()
        );
        return NamespaceResponse.from(namespace);
    }

    @Transactional
    public NamespaceResponse unfreezeNamespace(String slug, String userId, AuditRequestContext auditContext) {
        Namespace namespace = namespaceGovernanceService.unfreezeNamespace(
                slug,
                userId,
                null,
                auditContext.clientIp(),
                auditContext.userAgent()
        );
        return NamespaceResponse.from(namespace);
    }

    @Transactional
    public NamespaceResponse archiveNamespace(String slug,
                                              NamespaceLifecycleRequest request,
                                              String userId,
                                              AuditRequestContext auditContext) {
        Namespace namespace = namespaceGovernanceService.archiveNamespace(
                slug,
                userId,
                request != null ? request.reason() : null,
                null,
                auditContext.clientIp(),
                auditContext.userAgent()
        );
        return NamespaceResponse.from(namespace);
    }

    @Transactional
    public NamespaceResponse restoreNamespace(String slug, String userId, AuditRequestContext auditContext) {
        Namespace namespace = namespaceGovernanceService.restoreNamespace(
                slug,
                userId,
                null,
                auditContext.clientIp(),
                auditContext.userAgent()
        );
        return NamespaceResponse.from(namespace);
    }

    @Transactional
    public MemberResponse addMember(String slug, String memberUserId, com.iflytek.skillhub.domain.namespace.NamespaceRole role, String operatorUserId) {
        Namespace namespace = namespaceService.getNamespaceBySlug(slug);
        NamespaceMember member = namespaceMemberService.addMember(
                namespace.getId(),
                memberUserId,
                role,
                operatorUserId
        );
        return MemberResponse.from(member);
    }

    @Transactional
    public MessageResponse removeMember(String slug, String memberUserId, String operatorUserId) {
        Namespace namespace = namespaceService.getNamespaceBySlug(slug);
        namespaceMemberService.removeMember(namespace.getId(), memberUserId, operatorUserId);
        return new MessageResponse("Member removed successfully");
    }

    @Transactional
    public MemberResponse updateMemberRole(String slug,
                                           String userId,
                                           UpdateMemberRoleRequest request,
                                           String operatorUserId) {
        Namespace namespace = namespaceService.getNamespaceBySlug(slug);
        NamespaceMember member = namespaceMemberService.updateMemberRole(
                namespace.getId(),
                userId,
                request.role(),
                operatorUserId
        );
        return MemberResponse.from(member);
    }

    private boolean canCreateNamespace(PlatformPrincipal principal) {
        return principal.platformRoles().contains("SKILL_ADMIN")
                || principal.platformRoles().contains("SUPER_ADMIN");
    }
}
