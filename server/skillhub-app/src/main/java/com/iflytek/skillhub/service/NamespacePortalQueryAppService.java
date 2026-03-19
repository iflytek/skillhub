package com.iflytek.skillhub.service;

import com.iflytek.skillhub.domain.namespace.Namespace;
import com.iflytek.skillhub.domain.namespace.NamespaceAccessPolicy;
import com.iflytek.skillhub.domain.namespace.NamespaceMember;
import com.iflytek.skillhub.domain.namespace.NamespaceMemberService;
import com.iflytek.skillhub.domain.namespace.NamespaceRepository;
import com.iflytek.skillhub.domain.namespace.NamespaceRole;
import com.iflytek.skillhub.domain.namespace.NamespaceService;
import com.iflytek.skillhub.domain.namespace.NamespaceStatus;
import com.iflytek.skillhub.dto.MemberResponse;
import com.iflytek.skillhub.dto.MyNamespaceResponse;
import com.iflytek.skillhub.dto.NamespaceResponse;
import com.iflytek.skillhub.dto.PageResponse;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.Page;
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

    public NamespacePortalQueryAppService(NamespaceRepository namespaceRepository,
                                          NamespaceService namespaceService,
                                          NamespaceMemberService namespaceMemberService,
                                          NamespaceAccessPolicy namespaceAccessPolicy) {
        this.namespaceRepository = namespaceRepository;
        this.namespaceService = namespaceService;
        this.namespaceMemberService = namespaceMemberService;
        this.namespaceAccessPolicy = namespaceAccessPolicy;
    }

    @Transactional(readOnly = true)
    public PageResponse<NamespaceResponse> listNamespaces(Pageable pageable) {
        Page<Namespace> namespaces = namespaceRepository.findByStatus(NamespaceStatus.ACTIVE, pageable);
        return PageResponse.from(namespaces.map(NamespaceResponse::from));
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
        Namespace namespace = namespaceService.getNamespaceBySlugForRead(
                slug,
                userId,
                userNamespaceRoles != null ? userNamespaceRoles : Map.of());
        return NamespaceResponse.from(namespace);
    }

    @Transactional(readOnly = true)
    public PageResponse<MemberResponse> listMembers(String slug, Pageable pageable, String userId) {
        Namespace namespace = namespaceService.getNamespaceBySlug(slug);
        namespaceService.assertMember(namespace.getId(), userId);
        Page<NamespaceMember> members = namespaceMemberService.listMembers(namespace.getId(), pageable);
        return PageResponse.from(members.map(MemberResponse::from));
    }
}
