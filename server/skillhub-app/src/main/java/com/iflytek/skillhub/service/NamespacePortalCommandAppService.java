package com.iflytek.skillhub.service;

import com.iflytek.skillhub.auth.rbac.PlatformPrincipal;
import com.iflytek.skillhub.domain.namespace.Namespace;
import com.iflytek.skillhub.domain.namespace.NamespaceGovernanceService;
import com.iflytek.skillhub.domain.namespace.NamespaceMember;
import com.iflytek.skillhub.domain.namespace.NamespaceMemberService;
import com.iflytek.skillhub.domain.namespace.NamespaceRepository;
import com.iflytek.skillhub.domain.namespace.NamespaceService;
import com.iflytek.skillhub.domain.user.UserAccount;
import com.iflytek.skillhub.domain.user.UserAccountRepository;
import com.iflytek.skillhub.dto.BatchMemberRequest;
import com.iflytek.skillhub.dto.BatchMemberResponse;
import com.iflytek.skillhub.dto.BatchMemberResult;
import com.iflytek.skillhub.dto.MemberResponse;
import com.iflytek.skillhub.dto.MessageResponse;
import com.iflytek.skillhub.dto.NamespaceLifecycleRequest;
import com.iflytek.skillhub.dto.NamespaceRequest;
import com.iflytek.skillhub.dto.NamespaceResponse;
import com.iflytek.skillhub.dto.MemberRequest;
import com.iflytek.skillhub.dto.UpdateMemberRoleRequest;
import com.iflytek.skillhub.exception.ForbiddenException;
import com.iflytek.skillhub.exception.UnauthorizedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * Command-facing namespace application service for portal endpoints.
 */
@Service
public class NamespacePortalCommandAppService {

    private final NamespaceService namespaceService;
    private final NamespaceRepository namespaceRepository;
    private final NamespaceGovernanceService namespaceGovernanceService;
    private final NamespaceMemberService namespaceMemberService;
    private final UserAccountRepository userAccountRepository;

    public NamespacePortalCommandAppService(NamespaceService namespaceService,
                                            NamespaceRepository namespaceRepository,
                                            NamespaceGovernanceService namespaceGovernanceService,
                                            NamespaceMemberService namespaceMemberService,
                                            UserAccountRepository userAccountRepository) {
        this.namespaceService = namespaceService;
        this.namespaceRepository = namespaceRepository;
        this.namespaceGovernanceService = namespaceGovernanceService;
        this.namespaceMemberService = namespaceMemberService;
        this.userAccountRepository = userAccountRepository;
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
        UserAccount user = userAccountRepository.findById(memberUserId).orElse(null);
        return MemberResponse.from(member, user);
    }

    // Intentionally not @Transactional: each addMember runs in its own transaction
    // so partial success is possible (some members added even if others fail).
    public BatchMemberResponse batchAddMembers(String slug, List<MemberRequest> members, String operatorUserId) {
        Namespace namespace = namespaceService.getNamespaceBySlug(slug);
        Long namespaceId = namespace.getId();

        List<BatchMemberResult> results = new ArrayList<>();
        int successCount = 0;
        int failureCount = 0;

        for (MemberRequest req : members) {
            try {
                namespaceMemberService.addMember(namespaceId, req.userId(), req.role(), operatorUserId);
                results.add(BatchMemberResult.success(req.userId(), req.role().name()));
                successCount++;
            } catch (Exception e) {
                String errorCode = mapBatchError(e);
                results.add(BatchMemberResult.failure(req.userId(), req.role().name(), errorCode));
                failureCount++;
            }
        }

        return new BatchMemberResponse(members.size(), successCount, failureCount, results);
    }

    private String mapBatchError(Exception e) {
        String msg = e.getMessage();
        if (msg == null) return "UNKNOWN_ERROR";
        if (msg.contains("alreadyExists")) return "ALREADY_MEMBER";
        if (msg.contains("owner.assignDirect")) return "INVALID_ROLE";
        if (msg.contains("notFound") || msg.contains("not found")) return "USER_NOT_FOUND";
        if (msg.contains("immutable") || msg.contains("readonly")) return "NAMESPACE_READONLY";
        return "UNKNOWN_ERROR";
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
        return MemberResponse.from(member, userAccountRepository.findById(userId).orElse(null));
    }

    private boolean canCreateNamespace(PlatformPrincipal principal) {
        return principal.platformRoles().contains("SKILL_ADMIN")
                || principal.platformRoles().contains("SUPER_ADMIN");
    }
}
