package com.iflytek.skillhub.service;

import com.iflytek.skillhub.domain.namespace.Namespace;
import com.iflytek.skillhub.domain.namespace.NamespaceAccessPolicy;
import com.iflytek.skillhub.domain.namespace.NamespaceGovernanceService;
import com.iflytek.skillhub.domain.namespace.NamespaceMember;
import com.iflytek.skillhub.domain.namespace.NamespaceMemberRepository;
import com.iflytek.skillhub.domain.namespace.NamespaceMemberService;
import com.iflytek.skillhub.domain.namespace.NamespaceRole;
import com.iflytek.skillhub.domain.namespace.NamespaceService;
import com.iflytek.skillhub.domain.namespace.NamespaceStatus;
import com.iflytek.skillhub.domain.namespace.NamespaceType;
import com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException;
import com.iflytek.skillhub.domain.user.UserAccount;
import com.iflytek.skillhub.domain.user.UserAccountRepository;
import com.iflytek.skillhub.dto.AdminNamespaceDetailResponse;
import com.iflytek.skillhub.dto.AdminNamespaceListResponse;
import com.iflytek.skillhub.dto.AdminNamespacePermissionsResponse;
import com.iflytek.skillhub.dto.AdminNamespaceStatsResponse;
import com.iflytek.skillhub.dto.AdminNamespaceSummaryResponse;
import com.iflytek.skillhub.dto.BatchMemberRequest;
import com.iflytek.skillhub.dto.BatchMemberResponse;
import com.iflytek.skillhub.dto.BatchMemberResult;
import com.iflytek.skillhub.dto.MemberRequest;
import com.iflytek.skillhub.dto.MemberResponse;
import com.iflytek.skillhub.dto.MessageResponse;
import com.iflytek.skillhub.dto.NamespaceCandidateUserResponse;
import com.iflytek.skillhub.dto.NamespaceLifecycleRequest;
import com.iflytek.skillhub.dto.PageResponse;
import com.iflytek.skillhub.dto.UpdateMemberRoleRequest;
import com.iflytek.skillhub.repository.AdminNamespaceQueryRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class AdminNamespaceAppService {

    private final AdminNamespaceQueryRepository adminNamespaceQueryRepository;
    private final NamespaceService namespaceService;
    private final NamespaceGovernanceService namespaceGovernanceService;
    private final NamespaceMemberService namespaceMemberService;
    private final NamespaceMemberRepository namespaceMemberRepository;
    private final NamespaceMemberCandidateService namespaceMemberCandidateService;
    private final NamespaceAccessPolicy namespaceAccessPolicy;
    private final UserAccountRepository userAccountRepository;

    public AdminNamespaceAppService(AdminNamespaceQueryRepository adminNamespaceQueryRepository,
                                    NamespaceService namespaceService,
                                    NamespaceGovernanceService namespaceGovernanceService,
                                    NamespaceMemberService namespaceMemberService,
                                    NamespaceMemberRepository namespaceMemberRepository,
                                    NamespaceMemberCandidateService namespaceMemberCandidateService,
                                    NamespaceAccessPolicy namespaceAccessPolicy,
                                    UserAccountRepository userAccountRepository) {
        this.adminNamespaceQueryRepository = adminNamespaceQueryRepository;
        this.namespaceService = namespaceService;
        this.namespaceGovernanceService = namespaceGovernanceService;
        this.namespaceMemberService = namespaceMemberService;
        this.namespaceMemberRepository = namespaceMemberRepository;
        this.namespaceMemberCandidateService = namespaceMemberCandidateService;
        this.namespaceAccessPolicy = namespaceAccessPolicy;
        this.userAccountRepository = userAccountRepository;
    }

    @Transactional(readOnly = true)
    public AdminNamespaceListResponse list(String keyword,
                                           String status,
                                           String type,
                                           int page,
                                           int size,
                                           String actorUserId) {
        PageRequest pageRequest = PageRequest.of(Math.max(page, 0), normalizePageSize(size));
        Page<Namespace> namespaces = adminNamespaceQueryRepository.search(
                keyword,
                parseStatus(status),
                parseType(type),
                pageRequest);
        List<Long> namespaceIds = namespaces.getContent().stream().map(Namespace::getId).toList();
        Map<Long, Long> memberCounts = adminNamespaceQueryRepository.countMembersByNamespaceId(namespaceIds);
        Map<Long, Long> skillCounts = adminNamespaceQueryRepository.countSkillsByNamespaceId(namespaceIds);
        Map<Long, NamespaceRole> roles = loadRoles(namespaceIds, actorUserId);

        List<AdminNamespaceSummaryResponse> items = namespaces.getContent().stream()
                .map(namespace -> AdminNamespaceSummaryResponse.from(
                        namespace,
                        stats(namespace, memberCounts, skillCounts),
                        permissions(namespace, roles.get(namespace.getId()))))
                .toList();

        return new AdminNamespaceListResponse(
                items,
                namespaces.getTotalElements(),
                namespaces.getNumber(),
                namespaces.getSize(),
                adminNamespaceQueryRepository.stats());
    }

    @Transactional(readOnly = true)
    public AdminNamespaceDetailResponse detail(String slug, String actorUserId) {
        Namespace namespace = namespaceService.getNamespaceBySlug(slug);
        Map<Long, Long> memberCounts = adminNamespaceQueryRepository.countMembersByNamespaceId(List.of(namespace.getId()));
        Map<Long, Long> skillCounts = adminNamespaceQueryRepository.countSkillsByNamespaceId(List.of(namespace.getId()));
        NamespaceRole role = namespaceMemberRepository.findByNamespaceIdAndUserId(namespace.getId(), actorUserId)
                .map(NamespaceMember::getRole)
                .orElse(null);
        return AdminNamespaceDetailResponse.from(namespace, stats(namespace, memberCounts, skillCounts), permissions(namespace, role));
    }

    @Transactional(readOnly = true)
    public PageResponse<MemberResponse> listMembers(String slug, int page, int size) {
        Namespace namespace = namespaceService.getNamespaceBySlug(slug);
        Page<NamespaceMember> members = namespaceMemberService.listMembers(
                namespace.getId(),
                PageRequest.of(Math.max(page, 0), normalizePageSize(size)));

        List<String> memberUserIds = members.getContent().stream()
                .map(NamespaceMember::getUserId)
                .toList();
        Map<String, UserAccount> userMap = memberUserIds.isEmpty()
                ? Map.of()
                : userAccountRepository.findByIdIn(memberUserIds).stream()
                        .collect(Collectors.toMap(UserAccount::getId, Function.identity()));

        return PageResponse.from(members.map(member -> MemberResponse.from(member, userMap.get(member.getUserId()))));
    }

    @Transactional(readOnly = true)
    public List<NamespaceCandidateUserResponse> searchMemberCandidates(String slug, String search, int size) {
        return namespaceMemberCandidateService.searchCandidatesForPlatformAdmin(slug, search, size);
    }

    @Transactional
    public MemberResponse addMember(String slug, MemberRequest request, String actorUserId) {
        Namespace namespace = requireMutableTeamNamespace(slug);
        if (request.role() == NamespaceRole.OWNER) {
            throw new DomainBadRequestException("error.namespace.member.owner.assignDirect");
        }
        if (namespaceMemberRepository.findByNamespaceIdAndUserId(namespace.getId(), request.userId()).isPresent()) {
            throw new DomainBadRequestException("error.namespace.member.alreadyExists");
        }
        NamespaceMember member = namespaceMemberRepository.save(new NamespaceMember(namespace.getId(), request.userId(), request.role()));
        return MemberResponse.from(member, userAccountRepository.findById(member.getUserId()).orElse(null));
    }

    @Transactional
    public BatchMemberResponse batchAddMembers(String slug, BatchMemberRequest request, String actorUserId) {
        Namespace namespace = requireMutableTeamNamespace(slug);
        List<BatchMemberResult> results = new ArrayList<>();
        int successCount = 0;
        int failureCount = 0;

        for (MemberRequest member : request.members()) {
            try {
                if (member.role() == NamespaceRole.OWNER) {
                    throw new DomainBadRequestException("error.namespace.member.owner.assignDirect");
                }
                if (namespaceMemberRepository.findByNamespaceIdAndUserId(namespace.getId(), member.userId()).isPresent()) {
                    throw new DomainBadRequestException("error.namespace.member.alreadyExists");
                }
                namespaceMemberRepository.save(new NamespaceMember(namespace.getId(), member.userId(), member.role()));
                results.add(BatchMemberResult.success(member.userId(), member.role().name()));
                successCount++;
            } catch (Exception e) {
                results.add(BatchMemberResult.failure(member.userId(), member.role().name(), mapBatchError(e)));
                failureCount++;
            }
        }
        return new BatchMemberResponse(request.members().size(), successCount, failureCount, results);
    }

    @Transactional
    public MemberResponse updateMemberRole(String slug, String userId, UpdateMemberRoleRequest request, String actorUserId) {
        Namespace namespace = requireMutableTeamNamespace(slug);
        if (request.role() == NamespaceRole.OWNER) {
            throw new DomainBadRequestException("error.namespace.member.owner.setDirect");
        }
        NamespaceMember member = namespaceMemberRepository.findByNamespaceIdAndUserId(namespace.getId(), userId)
                .orElseThrow(() -> new DomainBadRequestException("error.namespace.member.notFound"));
        if (member.getRole() == NamespaceRole.OWNER) {
            throw new DomainBadRequestException("error.namespace.member.owner.setDirect");
        }
        member.setRole(request.role());
        NamespaceMember saved = namespaceMemberRepository.save(member);
        return MemberResponse.from(saved, userAccountRepository.findById(saved.getUserId()).orElse(null));
    }

    @Transactional
    public MessageResponse removeMember(String slug, String userId, String actorUserId) {
        Namespace namespace = requireMutableTeamNamespace(slug);
        NamespaceMember member = namespaceMemberRepository.findByNamespaceIdAndUserId(namespace.getId(), userId)
                .orElseThrow(() -> new DomainBadRequestException("error.namespace.member.notFound"));
        if (member.getRole() == NamespaceRole.OWNER) {
            throw new DomainBadRequestException("error.namespace.member.owner.remove");
        }
        namespaceMemberRepository.deleteByNamespaceIdAndUserId(namespace.getId(), userId);
        return new MessageResponse("Member removed successfully");
    }

    @Transactional
    public MessageResponse transferOwnership(String slug, String newOwnerId, String actorUserId) {
        Namespace namespace = requireMutableTeamNamespace(slug);
        NamespaceMember currentOwner = namespaceMemberRepository.findByNamespaceIdAndRoleIn(namespace.getId(), List.of(NamespaceRole.OWNER))
                .stream()
                .findFirst()
                .orElseThrow(() -> new DomainBadRequestException("error.namespace.owner.current.notFound"));
        NamespaceMember newOwner = namespaceMemberRepository.findByNamespaceIdAndUserId(namespace.getId(), newOwnerId)
                .orElseThrow(() -> new DomainBadRequestException("error.namespace.owner.new.notFound"));
        currentOwner.setRole(NamespaceRole.ADMIN);
        newOwner.setRole(NamespaceRole.OWNER);
        namespaceMemberRepository.save(currentOwner);
        namespaceMemberRepository.save(newOwner);
        return new MessageResponse("Ownership transferred successfully");
    }

    @Transactional
    public AdminNamespaceDetailResponse freeze(String slug,
                                               NamespaceLifecycleRequest request,
                                               String actorUserId,
                                               AuditRequestContext auditContext) {
        Namespace namespace = namespaceGovernanceService.freezeNamespaceByPlatformAdmin(
                slug,
                actorUserId,
                request != null ? request.reason() : null,
                null,
                auditContext.clientIp(),
                auditContext.userAgent());
        return detail(namespace.getSlug(), actorUserId);
    }

    @Transactional
    public AdminNamespaceDetailResponse unfreeze(String slug, String actorUserId, AuditRequestContext auditContext) {
        Namespace namespace = namespaceGovernanceService.unfreezeNamespaceByPlatformAdmin(
                slug,
                actorUserId,
                null,
                auditContext.clientIp(),
                auditContext.userAgent());
        return detail(namespace.getSlug(), actorUserId);
    }

    @Transactional
    public AdminNamespaceDetailResponse archive(String slug,
                                                NamespaceLifecycleRequest request,
                                                String actorUserId,
                                                AuditRequestContext auditContext) {
        Namespace namespace = namespaceGovernanceService.archiveNamespaceByPlatformAdmin(
                slug,
                actorUserId,
                request != null ? request.reason() : null,
                null,
                auditContext.clientIp(),
                auditContext.userAgent());
        return detail(namespace.getSlug(), actorUserId);
    }

    @Transactional
    public AdminNamespaceDetailResponse restore(String slug, String actorUserId, AuditRequestContext auditContext) {
        Namespace namespace = namespaceGovernanceService.restoreNamespaceByPlatformAdmin(
                slug,
                actorUserId,
                null,
                auditContext.clientIp(),
                auditContext.userAgent());
        return detail(namespace.getSlug(), actorUserId);
    }

    private AdminNamespaceStatsResponse stats(Namespace namespace,
                                              Map<Long, Long> memberCounts,
                                              Map<Long, Long> skillCounts) {
        return new AdminNamespaceStatsResponse(
                memberCounts.getOrDefault(namespace.getId(), 0L),
                skillCounts.getOrDefault(namespace.getId(), 0L));
    }

    private AdminNamespacePermissionsResponse permissions(Namespace namespace, NamespaceRole currentUserRole) {
        return AdminNamespacePermissionsResponse.forSuperAdmin(namespace, currentUserRole, namespaceAccessPolicy);
    }

    private Map<Long, NamespaceRole> loadRoles(List<Long> namespaceIds, String userId) {
        if (!StringUtils.hasText(userId) || namespaceIds.isEmpty()) {
            return Map.of();
        }
        return namespaceIds.stream()
                .map(id -> namespaceMemberRepository.findByNamespaceIdAndUserId(id, userId)
                        .orElse(null))
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toMap(NamespaceMember::getNamespaceId, NamespaceMember::getRole));
    }

    private Namespace requireMutableTeamNamespace(String slug) {
        Namespace namespace = namespaceService.getNamespaceBySlug(slug);
        if (namespaceAccessPolicy.isImmutable(namespace)) {
            throw new DomainBadRequestException("error.namespace.system.immutable", namespace.getSlug());
        }
        if (!namespaceAccessPolicy.canManageMembers(namespace)) {
            throw new DomainBadRequestException("error.namespace.readonly", namespace.getSlug());
        }
        return namespace;
    }

    private NamespaceStatus parseStatus(String status) {
        if (!StringUtils.hasText(status)) {
            return null;
        }
        try {
            return NamespaceStatus.valueOf(status.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new DomainBadRequestException("error.namespace.status.invalid", status);
        }
    }

    private NamespaceType parseType(String type) {
        if (!StringUtils.hasText(type)) {
            return null;
        }
        try {
            return NamespaceType.valueOf(type.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new DomainBadRequestException("error.namespace.type.invalid", type);
        }
    }

    private int normalizePageSize(int size) {
        if (size <= 0) {
            return 20;
        }
        return Math.min(size, 100);
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
}
