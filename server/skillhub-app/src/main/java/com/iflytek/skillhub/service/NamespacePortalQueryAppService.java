package com.iflytek.skillhub.service;

import com.iflytek.skillhub.domain.namespace.Namespace;
import com.iflytek.skillhub.domain.namespace.NamespaceAccessPolicy;
import com.iflytek.skillhub.domain.namespace.NamespaceMember;
import com.iflytek.skillhub.domain.namespace.NamespaceMemberService;
import com.iflytek.skillhub.domain.namespace.NamespaceRepository;
import com.iflytek.skillhub.domain.namespace.NamespaceRole;
import com.iflytek.skillhub.domain.namespace.NamespaceService;
import com.iflytek.skillhub.domain.namespace.NamespaceStatus;
import com.iflytek.skillhub.domain.shared.exception.DomainForbiddenException;
import com.iflytek.skillhub.domain.user.UserAccount;
import com.iflytek.skillhub.domain.user.UserAccountRepository;
import com.iflytek.skillhub.dto.MemberResponse;
import com.iflytek.skillhub.dto.MyNamespaceResponse;
import com.iflytek.skillhub.dto.NamespaceResponse;
import com.iflytek.skillhub.dto.PageResponse;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Query-facing namespace application service that keeps controller methods
 * thin while preserving current response contracts.
 */
@Service
public class NamespacePortalQueryAppService {

    private final NamespaceRepository namespaceRepository;
    private final NamespaceService namespaceService;
    private final NamespaceMemberService namespaceMemberService;
    private final NamespaceAccessPolicy namespaceAccessPolicy;
    private final UserAccountRepository userAccountRepository;

    public NamespacePortalQueryAppService(NamespaceRepository namespaceRepository,
                                          NamespaceService namespaceService,
                                          NamespaceMemberService namespaceMemberService,
                                          NamespaceAccessPolicy namespaceAccessPolicy,
                                          UserAccountRepository userAccountRepository) {
        this.namespaceRepository = namespaceRepository;
        this.namespaceService = namespaceService;
        this.namespaceMemberService = namespaceMemberService;
        this.namespaceAccessPolicy = namespaceAccessPolicy;
        this.userAccountRepository = userAccountRepository;
    }

    @Transactional(readOnly = true)
    public PageResponse<NamespaceResponse> listNamespaces(Pageable pageable, Map<Long, NamespaceRole> userNamespaceRoles) {
        Map<Long, NamespaceRole> namespaceRoles = userNamespaceRoles != null ? userNamespaceRoles : Map.of();
        if (namespaceRoles.isEmpty()) {
            Page<NamespaceResponse> empty = new PageImpl<>(
                    List.of(),
                    PageRequest.of(pageable.getPageNumber(), pageable.getPageSize()),
                    0
            );
            return PageResponse.from(empty);
        }

        List<Namespace> scopedNamespaces = namespaceRepository.findByIdIn(namespaceRoles.keySet().stream().toList()).stream()
                .filter(namespace -> namespace.getStatus() == NamespaceStatus.ACTIVE)
                .sorted(Comparator.comparing(Namespace::getSlug))
                .toList();
        int fromIndex = Math.min((int) pageable.getOffset(), scopedNamespaces.size());
        int toIndex = Math.min(fromIndex + pageable.getPageSize(), scopedNamespaces.size());
        Page<NamespaceResponse> page = new PageImpl<>(
                scopedNamespaces.subList(fromIndex, toIndex).stream()
                        .map(NamespaceResponse::from)
                        .toList(),
                pageable,
                scopedNamespaces.size()
        );
        return PageResponse.from(page);
    }

    @Transactional(readOnly = true)
    public List<MyNamespaceResponse> listMyNamespaces(Map<Long, NamespaceRole> userNamespaceRoles) {
        Map<Long, NamespaceRole> namespaceRoles = userNamespaceRoles != null ? userNamespaceRoles : Map.of();
        if (namespaceRoles.isEmpty()) {
            return List.of();
        }

        return namespaceRepository.findByIdIn(namespaceRoles.keySet().stream().toList()).stream()
                .sorted(Comparator.comparing(Namespace::getSlug))
                .map(namespace -> MyNamespaceResponse.from(
                        namespace,
                        namespaceRoles.get(namespace.getId()),
                        namespaceAccessPolicy))
                .toList();
    }

    @Transactional(readOnly = true)
    public NamespaceResponse getNamespace(String slug, String userId, Map<Long, NamespaceRole> userNamespaceRoles) {
        Map<Long, NamespaceRole> namespaceRoles = userNamespaceRoles != null ? userNamespaceRoles : Map.of();
        Namespace namespace = namespaceService.getNamespaceBySlugForRead(
                slug,
                userId,
                namespaceRoles);
        if (!namespaceRoles.containsKey(namespace.getId())) {
            throw new DomainForbiddenException("error.namespace.membership.required");
        }
        return NamespaceResponse.from(namespace);
    }

    @Transactional(readOnly = true)
    public PageResponse<MemberResponse> listMembers(String slug, Pageable pageable, String userId) {
        Namespace namespace = namespaceService.getNamespaceBySlug(slug);
        namespaceService.assertMember(namespace.getId(), userId);
        Page<NamespaceMember> members = namespaceMemberService.listMembers(namespace.getId(), pageable);

        List<String> memberUserIds = members.getContent().stream()
                .map(NamespaceMember::getUserId)
                .toList();

        Map<String, UserAccount> userMap = memberUserIds.isEmpty()
                ? Map.of()
                : userAccountRepository.findByIdIn(memberUserIds).stream()
                        .collect(Collectors.toMap(UserAccount::getId, Function.identity()));

        return PageResponse.from(members.map(member ->
                MemberResponse.from(member, userMap.get(member.getUserId()))
        ));
    }
}
